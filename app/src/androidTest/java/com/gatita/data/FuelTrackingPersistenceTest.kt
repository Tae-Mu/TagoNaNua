package com.gatita.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.gatita.data.local.AppDatabase
import com.gatita.data.repository.RoomCostRepository
import com.gatita.data.repository.RoomFuelTrackingRepository
import com.gatita.data.repository.RoomPaymentSettlementRepository
import com.gatita.data.repository.RoomTransactionRunner
import com.gatita.data.repository.RoomTripRepository
import com.gatita.domain.calculator.FuelSettlementPlanner
import com.gatita.domain.model.CostInput
import com.gatita.domain.model.CostKind
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.EfficiencyReading
import com.gatita.domain.model.EfficiencyScope
import com.gatita.domain.model.EfficiencyUnit
import com.gatita.domain.model.FUEL_SEGMENTS_CALCULATION_VERSION
import com.gatita.domain.model.FuelSnapshot
import com.gatita.domain.model.FuelSnapshotKind
import com.gatita.domain.model.RefuelEvent
import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.UNSAVED_ID
import com.gatita.domain.model.VehicleSource
import com.gatita.domain.usecase.AddCostUseCase
import com.gatita.domain.usecase.CompleteTripCommand
import com.gatita.domain.usecase.CompleteTripUseCase
import com.gatita.domain.usecase.ConfirmPaymentCommand
import com.gatita.domain.usecase.ConfirmPaymentSettlementUseCase
import com.gatita.domain.usecase.SetCostUndetectedUseCase
import com.gatita.domain.usecase.SetManualFuelUnitPriceUseCase
import com.gatita.domain.usecase.SetRefuelDismissedUseCase
import com.gatita.domain.usecase.StartTripCommand
import com.gatita.domain.usecase.StartTripUseCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 구간별 유류비(fuel-segments-v2)의 Room 저장: 측정·주유 저장, revision 증가, 확정 롤백, 불변 트리거. */
class FuelTrackingPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "fuel-tracking-test.db"
    private lateinit var db: AppDatabase
    private val clock = Clock.fixed(Instant.ofEpochMilli(10_000), ZoneOffset.UTC)

    private lateinit var tx: RoomTransactionRunner
    private lateinit var trips: RoomTripRepository
    private lateinit var costs: RoomCostRepository
    private lateinit var fuel: RoomFuelTrackingRepository
    private lateinit var settlements: RoomPaymentSettlementRepository

    @Before fun setup() {
        context.deleteDatabase(name)
        db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addCallback(AppDatabase.immutabilityCallback).build()
        tx = RoomTransactionRunner(db)
        trips = RoomTripRepository(db)
        costs = RoomCostRepository(db)
        fuel = RoomFuelTrackingRepository(db)
        settlements = RoomPaymentSettlementRepository(db)
    }

    @After fun cleanup() {
        db.close()
        context.deleteDatabase(name)
    }

    private fun <T> DomainResult<T>.ok(): T = (this as DomainResult.Success).value
    private fun d(v: String) = BigDecimal(v)

    private fun reading(lPer100: String, km: String) = EfficiencyReading(
        d(lPer100), EfficiencyUnit.LITERS_PER_100_KM, EfficiencyScope.CUMULATIVE_AVERAGE, "since-refuel", d(km),
    )

    private fun snapshot(tripId: Long, at: Long, kind: FuelSnapshotKind, odo: String, pct: Int?, eff: EfficiencyReading?) =
        FuelSnapshot(UNSAVED_ID, tripId, at, kind, d(odo), pct, eff, null, VehicleSource.PLEOS)

    private suspend fun startV2(people: Int = 2) = StartTripUseCase(trips, tx)(
        StartTripCommand(d("1000"), 1_000, VehicleSource.PLEOS, people = people),
    ).ok()

    @Test fun snapshotsAndRefuelsRoundTripWithMeasurements() = runBlocking {
        val trip = startV2()
        val start = fuel.insertSnapshot(snapshot(trip.id, 1_000, FuelSnapshotKind.START, "1000", 80, reading("7.0", "0")))
        val failed = fuel.insertSnapshot(snapshot(trip.id, 2_000, FuelSnapshotKind.PERIODIC, "1010", 78, null))
        val event = fuel.insertRefuel(RefuelEvent(UNSAVED_ID, trip.id, 3_000, 30, 90))
        assertTrue(start.id > 0 && failed.id > start.id && event.id > 0)

        val saved = fuel.getSnapshots(trip.id)
        // BigDecimal은 저장 시 정규화(7.0 → 7)되므로 값은 compareTo로, 나머지 필드는 그대로 비교한다.
        assertEquals(listOf(start.id, failed.id), saved.map { it.id })
        assertEquals(listOf(FuelSnapshotKind.START, FuelSnapshotKind.PERIODIC), saved.map { it.kind })
        assertEquals(listOf(80, 78), saved.map { it.fuelPercent })
        assertEquals(0, d("1010").compareTo(saved[1].odometerKm))
        assertEquals(0, d("0").compareTo(saved[0].efficiency!!.cumulativeDistanceKm!!))
        assertEquals("since-refuel", saved[0].efficiency!!.aggregationKey)
        // 측정 실패는 0이 아니라 null로 저장된다.
        assertNull(saved[1].efficiency)
        assertEquals(d("7.0").compareTo(saved[0].efficiency!!.value), 0)
        assertEquals(EfficiencyUnit.LITERS_PER_100_KM, saved[0].efficiency!!.unit)
        assertEquals(listOf(event), fuel.getRefuels(trip.id))
    }

    @Test fun refuelDismissUndetectedAndManualPriceBumpTripRevision() = runBlocking {
        val trip = startV2()
        val event = fuel.insertRefuel(RefuelEvent(UNSAVED_ID, trip.id, 3_000, 30, 90))
        val cost = AddCostUseCase(trips, costs, tx, clock)(
            trip.id, CostInput(CostKind.FUEL, 50_000, null, d("1700"), LocalDate.of(2026, 11, 12)), "fuel-1",
        ).ok()

        var revision = trips.getTrip(trip.id)!!.revision
        assertTrue(fuel.setRefuelDismissed(event.id, true, 20_000))
        assertTrue(fuel.getRefuels(trip.id).single().dismissed)
        assertTrue(trips.getTrip(trip.id)!!.revision > revision)

        revision = trips.getTrip(trip.id)!!.revision
        assertTrue(fuel.setCostUndetected(cost.id, true, 21_000))
        assertTrue(costs.get(cost.id)!!.undetected)
        assertTrue(trips.getTrip(trip.id)!!.revision > revision)

        revision = trips.getTrip(trip.id)!!.revision
        assertTrue(fuel.setManualFuelUnitPrice(trip.id, d("1650"), 22_000))
        assertEquals(0, d("1650").compareTo(trips.getTrip(trip.id)!!.manualFuelUnitPrice!!))
        assertTrue(trips.getTrip(trip.id)!!.revision > revision)
        assertTrue(fuel.setManualFuelUnitPrice(trip.id, null, 23_000))
        assertNull(trips.getTrip(trip.id)!!.manualFuelUnitPrice)

        // 없는 대상은 false이고 revision을 올리지 않는다.
        revision = trips.getTrip(trip.id)!!.revision
        assertEquals(false, fuel.setRefuelDismissed(9_999, true, 30_000))
        assertEquals(false, fuel.setCostUndetected(9_999, true, 30_000))
        assertEquals(revision, trips.getTrip(trip.id)!!.revision)
    }

    /** 확정 가능한 v2 여행: 구간 0이 8L(1,700원/L = 13,600원) + 통행료 3,000원. */
    private suspend fun finishedV2WithToll(): Pair<Long, Long> {
        val trip = startV2()
        fuel.insertSnapshot(snapshot(trip.id, 1_000, FuelSnapshotKind.START, "1000", 80, reading("7.0", "0")))
        fuel.insertSnapshot(snapshot(trip.id, 9_000, FuelSnapshotKind.END, "1100", 70, reading("8.0", "100")))
        SetManualFuelUnitPriceUseCase(trips, fuel, tx, clock)(trip.id, d("1700")).ok()
        AddCostUseCase(trips, costs, tx, clock)(trip.id, CostInput(CostKind.TOLL, 3_000), "toll-1").ok()
        CompleteTripUseCase(trips, costs, tx, fuel)(CompleteTripCommand(trip.id, 10_000, d("1100"))).ok()
        return trip.id to trips.getTrip(trip.id)!!.revision
    }

    @Test fun fuelSegmentsConfirmPersistsIntervalsAndTriggerRejectsMutation() = runBlocking {
        val (tripId, revision) = finishedV2WithToll()
        val saved = ConfirmPaymentSettlementUseCase(trips, costs, settlements, tx, clock, fuel)(
            ConfirmPaymentCommand(tripId, "confirm-v2", revision, "민수"),
        ).ok()

        assertEquals(FUEL_SEGMENTS_CALCULATION_VERSION, saved.calculationVersion)
        assertEquals(3_000L, saved.otherTotalWon)
        assertEquals(saved.fuelTotalWon + saved.otherTotalWon, saved.totalWon)
        assertEquals(saved.totalWon, saved.shares.sumOf { it.amountWon })
        assertTrue(saved.fuelIntervals.isNotEmpty())
        assertEquals(TripStatus.SETTLED, trips.getTrip(tripId)!!.status)
        assertEquals(saved, settlements.findByTripId(tripId))

        val sqlite = db.openHelper.writableDatabase
        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            sqlite.execSQL("UPDATE payment_settlement_fuel_intervals SET ordinal = 99 WHERE settlementId = ?", arrayOf(saved.id))
        }
        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            sqlite.execSQL("DELETE FROM payment_settlement_fuel_intervals WHERE settlementId = ?", arrayOf(saved.id))
        }
        assertEquals(saved.fuelIntervals.size, settlements.findByTripId(tripId)!!.fuelIntervals.size)
    }

    @Test fun otherTotalMismatchRollsBackV2Settlement() = runBlocking {
        val (tripId, revision) = finishedV2WithToll()
        val trip = trips.getTrip(tripId)!!
        val valid = FuelSettlementPlanner.buildSnapshot(
            trip, costs.getCosts(tripId), fuel.getSnapshots(tripId), fuel.getRefuels(tripId), "나", "bad-v2", 10_000,
        ).ok()
        // 합계(fuel + other = total)는 맞지만 포함한 기타 비용 합(3,000원)과 otherTotalWon이 다르다.
        val invalid = valid.copy(fuelTotalWon = valid.fuelTotalWon - 1, otherTotalWon = valid.otherTotalWon + 1)
        try {
            settlements.saveAndMarkTripSettled(invalid, revision)
            fail("mismatched other total was accepted")
        } catch (_: IllegalStateException) {
        } catch (_: android.database.sqlite.SQLiteException) {
        }
        assertEquals(TripStatus.FINISHED, trips.getTrip(tripId)!!.status)
        assertNull(settlements.findByTripId(tripId))
        assertNull(settlements.findByRequestId("bad-v2"))
        // 롤백 뒤 같은 여행을 올바른 값으로 다시 확정할 수 있다.
        assertNotNull(settlements.saveAndMarkTripSettled(valid, revision))
    }

    @Test fun settledTripRejectsFuelEdits() = runBlocking {
        val (tripId, revision) = finishedV2WithToll()
        val event = fuel.insertRefuel(RefuelEvent(UNSAVED_ID, tripId, 5_000, 30, 90, dismissed = true))
        ConfirmPaymentSettlementUseCase(trips, costs, settlements, tx, clock, fuel)(
            ConfirmPaymentCommand(tripId, "confirm-lock", revision, "나"),
        ).ok()

        val afterConfirm = trips.getTrip(tripId)!!.revision
        assertTrue(SetRefuelDismissedUseCase(trips, fuel, tx, clock)(tripId, event.id, false) is DomainResult.Failure)
        assertTrue(SetManualFuelUnitPriceUseCase(trips, fuel, tx, clock)(tripId, d("1800")) is DomainResult.Failure)
        assertTrue(fuel.getRefuels(tripId).single().dismissed)
        assertEquals(afterConfirm, trips.getTrip(tripId)!!.revision)
        val cost = costs.getCosts(tripId).first()
        assertTrue(SetCostUndetectedUseCase(trips, fuel, tx, clock)(tripId, cost.id, true) is DomainResult.Failure)
    }
}
