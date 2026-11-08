package com.gatita.data

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.gatita.data.local.AppDatabase
import com.gatita.data.local.entity.SegmentEntity
import com.gatita.data.local.entity.TripEntity
import com.gatita.data.repository.RoomSettlementRepository
import com.gatita.data.repository.RoomTransactionRunner
import com.gatita.data.repository.RoomTripRepository
import com.gatita.domain.calculator.SettlementCalculator
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.EfficiencyReading
import com.gatita.domain.model.EfficiencyScope
import com.gatita.domain.model.EfficiencyUnit
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.FuelPrice
import com.gatita.domain.model.FuelType
import com.gatita.domain.model.SettlementInput
import com.gatita.domain.model.Trip
import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.VehicleSource
import com.gatita.domain.usecase.ConfirmSettlementCommand
import com.gatita.domain.usecase.ConfirmSettlementUseCase
import com.gatita.domain.usecase.FinishTripCommand
import com.gatita.domain.usecase.FinishTripUseCase
import com.gatita.domain.usecase.RestoreActiveTripUseCase
import com.gatita.domain.usecase.SaveSegmentCommand
import com.gatita.domain.usecase.SaveSegmentUseCase
import com.gatita.domain.usecase.StartTripCommand
import com.gatita.domain.usecase.StartTripUseCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** 실제 Room(SQLite) 위에서 제약·트랜잭션·복구를 검증한다. 에뮬레이터 또는 기기가 필요하다. */
class RoomPersistenceTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val dbName = "room-persistence-test.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)
        db = open()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun open(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addCallback(AppDatabase.immutabilityCallback)
            .build()

    private class Wiring(db: AppDatabase) {
        val trips = RoomTripRepository(db)
        val settlements = RoomSettlementRepository(db)
        private val tx = RoomTransactionRunner(db)
        private val calculator = SettlementCalculator()
        val start = StartTripUseCase(trips, tx)
        val restore = RestoreActiveTripUseCase(trips)
        val saveSegment = SaveSegmentUseCase(trips, tx)
        val finish = FinishTripUseCase(trips, tx)
        val confirm = ConfirmSettlementUseCase(
            trips, settlements, tx, calculator, Clock.fixed(Instant.ofEpochMilli(9_000_000), ZoneOffset.UTC),
        )
    }

    private fun kmPerLiter(value: String, scope: EfficiencyScope = EfficiencyScope.SEGMENT_AVERAGE) =
        EfficiencyReading(BigDecimal(value), EfficiencyUnit.KM_PER_LITER, scope)

    private fun segmentCommand(tripId: Long, eventId: String, end: String, at: Long, efficiency: String? = "10") =
        SaveSegmentCommand(
            tripId = tripId,
            eventId = eventId,
            endOdometerKm = BigDecimal(end),
            endedAtMillis = at,
            efficiency = efficiency?.let { kmPerLiter(it) },
            source = VehicleSource.SAMPLE,
        )

    private fun input(price: String = "368.5", headcount: Int = 4) =
        SettlementInput(FuelPrice(FuelType.GASOLINE, BigDecimal(price), "2026-11-09"), headcount)

    private fun <T> DomainResult<T>.ok(): T = when (this) {
        is DomainResult.Success -> value
        is DomainResult.Failure -> throw AssertionError("expected success: ${error.code} ${error.message}")
    }

    private fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private suspend fun finishedTrip(w: Wiring): Trip {
        val trip = w.start(StartTripCommand(BigDecimal("1000"), 1_000, VehicleSource.SAMPLE)).ok()
        w.saveSegment(segmentCommand(trip.id, "e1", "1100", 2_000)).ok()
        return w.finish(FinishTripCommand(trip.id, BigDecimal("1100"), 3_000)).ok()
    }

    // ---- 복구 ----

    @Test
    fun activeTripSurvivesClosingAndReopeningTheDatabase() = runBlocking {
        val started = Wiring(db).start(StartTripCommand(BigDecimal("12345.6"), 1_000, VehicleSource.MANUAL)).ok()
        Wiring(db).saveSegment(segmentCommand(started.id, "e1", "12400", 2_000)).ok()

        db.close()
        db = open()

        val wiring = Wiring(db)
        val restored = wiring.restore()
        assertEquals(started, restored)
        assertEquals(1, wiring.trips.getSegments(started.id).size)
    }

    @Test
    fun finishedTripIsNotRestoredAsActive() = runBlocking {
        val w = Wiring(db)
        finishedTrip(w)
        assertNull(w.restore())
    }

    // ---- 제약 ----

    @Test
    fun databaseAllowsOnlyOneInProgressTrip() = runBlocking {
        val dao = db.tripDao()
        fun trip() = TripEntity(
            status = "IN_PROGRESS", source = "MANUAL", startOdometerKm = "1", startedAtMillis = 1,
            endOdometerKm = null, endedAtMillis = null, activeSlot = 1,
        )
        dao.insert(trip())
        assertThrows(SQLiteException::class.java) { runBlocking { dao.insert(trip()) } }
        assertEquals(1, count("trips"))
    }

    @Test
    fun segmentSequenceAndEventIdAreUniquePerTrip() = runBlocking {
        val w = Wiring(db)
        val trip = w.start(StartTripCommand(BigDecimal("1000"), 1_000, VehicleSource.MANUAL)).ok()
        val dao = db.tripDao()
        fun segment(seq: Int, eventId: String) = SegmentEntity(
            tripId = trip.id, seq = seq, eventId = eventId, startOdometerKm = "1000", endOdometerKm = "1010",
            startedAtMillis = 1, endedAtMillis = 2, efficiencyValue = "10", efficiencyUnit = "KM_PER_LITER",
            efficiencyScope = "SEGMENT_AVERAGE", aggregationKey = null, cumulativeDistanceKm = null,
            baselineEfficiencyValue = null, baselineCumulativeDistanceKm = null, source = "MANUAL",
        )
        dao.insertSegment(segment(1, "a"))
        assertThrows(SQLiteException::class.java) { runBlocking { dao.insertSegment(segment(1, "b")) } }
        assertThrows(SQLiteException::class.java) { runBlocking { dao.insertSegment(segment(2, "a")) } }
        assertEquals(1, count("segments"))
    }

    @Test
    fun savingTheSameSegmentEventTwiceKeepsOneRow() = runBlocking {
        val w = Wiring(db)
        val trip = w.start(StartTripCommand(BigDecimal("1000"), 1_000, VehicleSource.SAMPLE)).ok()
        val first = w.saveSegment(segmentCommand(trip.id, "off-1", "1040", 2_000)).ok()
        val again = w.saveSegment(segmentCommand(trip.id, "off-1", "1040", 2_000)).ok()
        assertEquals(first.id, again.id)
        assertEquals(1, count("segments"))
    }

    @Test
    fun failedMeasurementIsPersistedAsNullEfficiencyNotZero() = runBlocking {
        val w = Wiring(db)
        val trip = w.start(StartTripCommand(BigDecimal("1000"), 1_000, VehicleSource.SAMPLE)).ok()
        w.saveSegment(segmentCommand(trip.id, "e1", "1040", 2_000, efficiency = null)).ok()
        val stored = w.trips.getSegments(trip.id).single()
        assertNull(stored.efficiency)
        assertEquals(VehicleSource.SAMPLE, stored.source)
    }

    @Test
    fun segmentsOfATripCannotBeDeletedWhileReferenced() = runBlocking {
        val w = Wiring(db)
        val trip = w.start(StartTripCommand(BigDecimal("1000"), 1_000, VehicleSource.MANUAL)).ok()
        w.saveSegment(segmentCommand(trip.id, "e1", "1040", 2_000)).ok()
        assertThrows(SQLiteException::class.java) {
            db.openHelper.writableDatabase.execSQL("DELETE FROM trips WHERE id = ${trip.id}")
        }
        assertEquals(1, count("trips"))
    }

    // ---- 정산 확정 ----

    @Test
    fun confirmedSettlementPersistsFullSnapshotAndRoundTrips() = runBlocking {
        val w = Wiring(db)
        val trip = finishedTrip(w)
        val saved = w.confirm(ConfirmSettlementCommand(trip.id, "req-1", input("368.5", 4))).ok()

        val loaded = checkNotNull(w.settlements.findByRequestId("req-1"))
        assertEquals(saved, loaded)
        assertEquals(3685L, loaded.calculation.totalCostWon)
        assertEquals(listOf(922L, 921L, 921L, 921L), loaded.calculation.shares.map { it.amountWon })
        assertEquals(1, loaded.calculation.items.size)
        assertEquals(VehicleSource.SAMPLE, loaded.calculation.items.single().source)
        assertEquals(TripStatus.SETTLED, w.trips.getTrip(trip.id)!!.status)
        assertEquals(1, count("fuel_prices"))
        assertEquals(1, count("settlement_items"))
        assertEquals(4, count("settlement_shares"))
    }

    @Test
    fun highPrecisionNonTerminatingValuesRoundTripExactly() = runBlocking {
        val w = Wiring(db)
        val trip = w.start(StartTripCommand(BigDecimal("0"), 1_000, VehicleSource.MANUAL)).ok()
        w.saveSegment(segmentCommand(trip.id, "e1", "100", 2_000, efficiency = "3")).ok()
        w.finish(FinishTripCommand(trip.id, BigDecimal("100"), 3_000)).ok()
        val saved = w.confirm(ConfirmSettlementCommand(trip.id, "req-1", input("1500", 3))).ok()
        val loaded = w.settlements.findByTripId(trip.id)!!
        assertEquals(saved.calculation.items.single().fuelLiters, loaded.calculation.items.single().fuelLiters)
        assertEquals(saved.calculation.rawTotalCostWon, loaded.calculation.rawTotalCostWon)
        assertEquals(50000L, loaded.calculation.totalCostWon)
    }

    @Test
    fun duplicateConfirmationCreatesOnlyOneSettlement() = runBlocking {
        val w = Wiring(db)
        val trip = finishedTrip(w)
        val first = w.confirm(ConfirmSettlementCommand(trip.id, "req-1", input())).ok()
        val retry = w.confirm(ConfirmSettlementCommand(trip.id, "req-1", input())).ok()
        assertEquals(first.id, retry.id)

        val other = w.confirm(ConfirmSettlementCommand(trip.id, "req-2", input()))
        assertEquals(ErrorCode.ALREADY_SETTLED, (other as DomainResult.Failure).error.code)

        assertEquals(1, count("settlements"))
        assertEquals(1, count("fuel_prices"))
        assertEquals(4, count("settlement_shares"))
    }

    @Test
    fun settlementTableRejectsSecondSettlementForTheSameTrip() = runBlocking {
        val w = Wiring(db)
        val trip = finishedTrip(w)
        val saved = w.confirm(ConfirmSettlementCommand(trip.id, "req-1", input())).ok()
        // 유스케이스를 우회해 저장소에 직접 넣어도 DB 제약이 막는다.
        assertThrows(Exception::class.java) {
            runBlocking { w.settlements.saveAndMarkTripSettled(saved.copy(id = 0, requestId = "req-other")) }
        }
        assertEquals(1, count("settlements"))
    }

    @Test
    fun failureInsideSettlementSaveRollsBackEverything() = runBlocking {
        val w = Wiring(db)
        val trip = finishedTrip(w)
        val good = (w.confirm(ConfirmSettlementCommand(trip.id, "req-1", input())) as DomainResult.Success).value

        // 새 여행에 대해, 분담액 순번이 중복된 손상된 정산을 저장해 중간 실패를 만든다.
        val second = w.start(StartTripCommand(BigDecimal("2000"), 10_000, VehicleSource.SAMPLE)).ok()
        w.saveSegment(segmentCommand(second.id, "s1", "2100", 11_000)).ok()
        w.finish(FinishTripCommand(second.id, BigDecimal("2100"), 12_000)).ok()

        val shares = good.calculation.shares
        val corrupt = good.copy(
            id = 0,
            tripId = second.id,
            requestId = "req-corrupt",
            calculation = good.calculation.copy(shares = listOf(shares[0], shares[0])),
        )
        assertThrows(Exception::class.java) { runBlocking { w.settlements.saveAndMarkTripSettled(corrupt) } }

        assertEquals(1, count("settlements"))
        assertEquals(1, count("fuel_prices"))
        assertEquals(1, count("settlement_items"))
        assertEquals(4, count("settlement_shares"))
        assertEquals(TripStatus.FINISHED, w.trips.getTrip(second.id)!!.status)
    }

    @Test
    fun savingSettlementForUnfinishedTripRollsBackInsertedRows() = runBlocking {
        val w = Wiring(db)
        val done = finishedTrip(w)
        val good = (w.confirm(ConfirmSettlementCommand(done.id, "req-1", input())) as DomainResult.Success).value

        val active = w.start(StartTripCommand(BigDecimal("3000"), 20_000, VehicleSource.SAMPLE)).ok()
        val invalid = good.copy(id = 0, tripId = active.id, requestId = "req-active")
        assertThrows(IllegalStateException::class.java) {
            runBlocking { w.settlements.saveAndMarkTripSettled(invalid) }
        }

        assertEquals(1, count("settlements"))
        assertEquals(1, count("fuel_prices"))
        assertEquals(TripStatus.IN_PROGRESS, w.trips.getTrip(active.id)!!.status)
    }

    @Test
    fun confirmedSettlementRowsCannotBeUpdatedOrDeleted() = runBlocking {
        val w = Wiring(db)
        val trip = finishedTrip(w)
        w.confirm(ConfirmSettlementCommand(trip.id, "req-1", input())).ok()
        val sql = db.openHelper.writableDatabase

        listOf(
            "UPDATE settlements SET totalCostWon = 1",
            "DELETE FROM settlements",
            "UPDATE settlement_items SET costWon = '1'",
            "DELETE FROM settlement_items",
            "UPDATE settlement_shares SET amountWon = 1",
            "DELETE FROM settlement_shares",
            "UPDATE fuel_prices SET pricePerLiterWon = '1'",
            "DELETE FROM fuel_prices",
        ).forEach { statement ->
            assertThrows(statement, SQLiteException::class.java) { sql.execSQL(statement) }
        }
        assertEquals(3685L, w.settlements.findByTripId(trip.id)!!.calculation.totalCostWon)
        assertTrue(count("settlement_shares") == 4)
    }

    @Test
    fun incompleteSegmentsDoNotCreateAnySettlementRows() = runBlocking {
        val w = Wiring(db)
        val trip = w.start(StartTripCommand(BigDecimal("1000"), 1_000, VehicleSource.SAMPLE)).ok()
        w.saveSegment(segmentCommand(trip.id, "e1", "1060", 2_000)).ok()
        w.finish(FinishTripCommand(trip.id, BigDecimal("1100"), 3_000)).ok()

        val result = w.confirm(ConfirmSettlementCommand(trip.id, "req-1", input()))
        assertEquals(ErrorCode.SEGMENT_GAP, (result as DomainResult.Failure).error.code)
        assertEquals(0, count("settlements"))
        assertEquals(0, count("fuel_prices"))
        assertEquals(TripStatus.FINISHED, w.trips.getTrip(trip.id)!!.status)
    }
}
