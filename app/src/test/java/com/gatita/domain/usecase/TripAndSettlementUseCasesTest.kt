package com.gatita.domain.usecase

import com.gatita.FakeSettlementRepository
import com.gatita.FakeStore
import com.gatita.FakeTripRepository
import com.gatita.SerialTransactionRunner
import com.gatita.assertDecimal
import com.gatita.assertFailure
import com.gatita.d
import com.gatita.domain.calculator.SettlementCalculator
import com.gatita.domain.model.CalculationMethod
import com.gatita.domain.model.EfficiencyScope
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.Trip
import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.VehicleSource
import com.gatita.input
import com.gatita.kmPerLiter
import com.gatita.success
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class TripAndSettlementUseCasesTest {
    private val store = FakeStore()
    private val trips = FakeTripRepository(store)
    private val settlements = FakeSettlementRepository(store)
    private val tx = SerialTransactionRunner()
    private val calculator = SettlementCalculator()
    private val clock = Clock.fixed(Instant.ofEpochMilli(5_000_000), ZoneOffset.UTC)

    private val startTrip = StartTripUseCase(trips, tx)
    private val restore = RestoreActiveTripUseCase(trips)
    private val saveSegment = SaveSegmentUseCase(trips, tx)
    private val finishTrip = FinishTripUseCase(trips, tx)
    private val preview = PreviewSettlementUseCase(trips, calculator)
    private val confirm = ConfirmSettlementUseCase(trips, settlements, tx, calculator, clock)

    private suspend fun start(odometer: String = "1000", at: Long = 1_000): Trip =
        startTrip(StartTripCommand(d(odometer), at, VehicleSource.MANUAL)).success()

    private fun segmentCommand(
        tripId: Long,
        eventId: String,
        end: String,
        at: Long,
        efficiency: String? = "10",
    ) = SaveSegmentCommand(
        tripId = tripId,
        eventId = eventId,
        endOdometerKm = d(end),
        endedAtMillis = at,
        efficiency = efficiency?.let { kmPerLiter(it) },
        source = VehicleSource.MANUAL,
    )

    private suspend fun finishedTripWithFullSegment(): Trip {
        val trip = start()
        saveSegment(segmentCommand(trip.id, "e1", "1100", 2_000)).success()
        return finishTrip(FinishTripCommand(trip.id, d("1100"), 3_000)).success()
    }

    // ---- start / restore ----

    @Test
    fun `started trip is in progress and restorable after relaunch`() = runTest {
        val trip = start()
        assertEquals(TripStatus.IN_PROGRESS, trip.status)

        // a fresh use case over the same persisted store models an app relaunch
        val restored = RestoreActiveTripUseCase(FakeTripRepository(store))()
        assertEquals(trip, restored)
    }

    @Test
    fun `second trip cannot start while one is in progress`() = runTest {
        start()
        startTrip(StartTripCommand(d("2000"), 2_000, VehicleSource.MANUAL)).assertFailure(ErrorCode.ACTIVE_TRIP_EXISTS)
        assertEquals(1, store.trips.size)
    }

    @Test
    fun `no active trip is restored once finished`() = runTest {
        finishedTripWithFullSegment()
        assertNull(restore())
    }

    @Test
    fun `invalid start odometer or time is rejected`() = runTest {
        startTrip(StartTripCommand(d("-1"), 1_000, VehicleSource.MANUAL)).assertFailure(ErrorCode.INVALID_ODOMETER)
        startTrip(StartTripCommand(d("10"), 0, VehicleSource.MANUAL)).assertFailure(ErrorCode.INVALID_TIMESTAMP)
        assertEquals(0, store.trips.size)
    }

    // ---- segments ----

    @Test
    fun `segments chain from the trip start and get increasing sequence numbers`() = runTest {
        val trip = start()
        val first = saveSegment(segmentCommand(trip.id, "e1", "1040", 2_000)).success()
        val second = saveSegment(segmentCommand(trip.id, "e2", "1100", 3_000)).success()
        assertEquals(1, first.seq)
        assertEquals(2, second.seq)
        assertDecimal("1000", first.startOdometerKm)
        assertDecimal("1040", second.startOdometerKm)
    }

    @Test
    fun `same event id saves the segment once and returns the existing record`() = runTest {
        val trip = start()
        val first = saveSegment(segmentCommand(trip.id, "off-1", "1040", 2_000)).success()
        val again = saveSegment(segmentCommand(trip.id, "off-1", "1040", 2_000)).success()
        assertEquals(first.id, again.id)
        assertEquals(1, store.segments.size)
    }

    @Test
    fun `retrying an old event after later segments still returns the original`() = runTest {
        val trip = start()
        val first = saveSegment(segmentCommand(trip.id, "off-1", "1040", 2_000)).success()
        saveSegment(segmentCommand(trip.id, "off-2", "1100", 3_000)).success()
        val retry = saveSegment(segmentCommand(trip.id, "off-1", "1040", 2_000)).success()
        assertEquals(first.id, retry.id)
        assertEquals(2, store.segments.size)
    }

    @Test
    fun `numerically equal values with different scale are the same event`() = runTest {
        val trip = start()
        val first = saveSegment(segmentCommand(trip.id, "off-1", "1040", 2_000, efficiency = "10")).success()
        val again = saveSegment(segmentCommand(trip.id, "off-1", "1040.00", 2_000, efficiency = "10.0")).success()
        assertEquals(first.id, again.id)
    }

    @Test
    fun `same event id with different data is a conflict and changes nothing`() = runTest {
        val trip = start()
        saveSegment(segmentCommand(trip.id, "off-1", "1040", 2_000)).success()
        saveSegment(segmentCommand(trip.id, "off-1", "1050", 2_000)).assertFailure(ErrorCode.SEGMENT_CONFLICT)
        assertDecimal("1040", store.segments.single().endOdometerKm)
    }

    @Test
    fun `retry of a segment is accepted even after the trip was finished`() = runTest {
        val trip = start()
        val first = saveSegment(segmentCommand(trip.id, "e1", "1100", 2_000)).success()
        finishTrip(FinishTripCommand(trip.id, d("1100"), 3_000)).success()
        assertEquals(first.id, saveSegment(segmentCommand(trip.id, "e1", "1100", 2_000)).success().id)
        saveSegment(segmentCommand(trip.id, "e2", "1200", 4_000)).assertFailure(ErrorCode.TRIP_NOT_IN_PROGRESS)
    }

    @Test
    fun `bad segment distances are rejected`() = runTest {
        val trip = start()
        saveSegment(segmentCommand(trip.id, "e1", "1000", 2_000)).assertFailure(ErrorCode.INVALID_DISTANCE)
        saveSegment(segmentCommand(trip.id, "e2", "990", 2_000)).assertFailure(ErrorCode.ODOMETER_DECREASED)
        saveSegment(segmentCommand(trip.id, "e3", "-5", 2_000)).assertFailure(ErrorCode.INVALID_ODOMETER)
        assertEquals(0, store.segments.size)
    }

    @Test
    fun `segment starting before the previous end overlaps`() = runTest {
        val trip = start()
        saveSegment(segmentCommand(trip.id, "e1", "1050", 2_000)).success()
        saveSegment(
            segmentCommand(trip.id, "e2", "1100", 3_000).copy(startOdometerKm = d("1040")),
        ).assertFailure(ErrorCode.SEGMENT_OVERLAP)
    }

    @Test
    fun `explicit later start is stored so the gap stays visible`() = runTest {
        val trip = start()
        saveSegment(segmentCommand(trip.id, "e1", "1040", 2_000)).success()
        val second = saveSegment(
            segmentCommand(trip.id, "e2", "1100", 3_000).copy(startOdometerKm = d("1060")),
        ).success()
        assertDecimal("1060", second.startOdometerKm)
    }

    @Test
    fun `time must not go backwards`() = runTest {
        val trip = start(at = 1_000)
        saveSegment(segmentCommand(trip.id, "e1", "1040", 500)).assertFailure(ErrorCode.INVALID_TIMESTAMP)
        saveSegment(segmentCommand(trip.id, "e2", "1040", 2_000)).success()
        saveSegment(segmentCommand(trip.id, "e3", "1100", 1_999)).assertFailure(ErrorCode.INVALID_TIMESTAMP)
    }

    @Test
    fun `non positive efficiency is rejected`() = runTest {
        val trip = start()
        saveSegment(segmentCommand(trip.id, "e1", "1040", 2_000, efficiency = "0")).assertFailure(ErrorCode.INVALID_EFFICIENCY)
        saveSegment(segmentCommand(trip.id, "e2", "1040", 2_000, efficiency = "-1")).assertFailure(ErrorCode.INVALID_EFFICIENCY)
        assertEquals(0, store.segments.size)
    }

    @Test
    fun `failed measurement is stored as missing efficiency never as zero`() = runTest {
        val trip = start()
        val saved = saveSegment(segmentCommand(trip.id, "e1", "1040", 2_000, efficiency = null)).success()
        assertNull(saved.efficiency)
        assertNull(store.segments.single().efficiency)
    }

    @Test
    fun `efficiency scope rules are enforced when saving`() = runTest {
        val trip = start()
        // vehicle reference efficiency may only be used for an explicit fallback
        saveSegment(
            segmentCommand(trip.id, "e1", "1040", 2_000)
                .copy(efficiency = kmPerLiter("10", EfficiencyScope.VEHICLE_REFERENCE)),
        ).assertFailure(ErrorCode.INVALID_EFFICIENCY_SCOPE)
        // cumulative without baseline
        saveSegment(
            segmentCommand(trip.id, "e2", "1040", 2_000)
                .copy(efficiency = kmPerLiter("10", EfficiencyScope.CUMULATIVE_AVERAGE, "A", "200")),
        ).assertFailure(ErrorCode.AGGREGATION_MISMATCH)
        // segment average carrying a baseline
        saveSegment(
            segmentCommand(trip.id, "e3", "1040", 2_000)
                .copy(baseline = kmPerLiter("10", EfficiencyScope.CUMULATIVE_AVERAGE, "A", "100")),
        ).assertFailure(ErrorCode.AGGREGATION_MISMATCH)
        // unknown scope is preserved as raw data
        val unknown = saveSegment(
            segmentCommand(trip.id, "e4", "1040", 2_000)
                .copy(efficiency = kmPerLiter("10", EfficiencyScope.UNKNOWN)),
        ).success()
        assertEquals(EfficiencyScope.UNKNOWN, unknown.efficiency?.scope)
    }

    @Test
    fun `valid cumulative segment keeps raw readings and aggregation key`() = runTest {
        val trip = start()
        val saved = saveSegment(
            segmentCommand(trip.id, "e1", "1200", 2_000).copy(
                efficiency = kmPerLiter("12.5", EfficiencyScope.CUMULATIVE_AVERAGE, "A", "300"),
                baseline = kmPerLiter("10", EfficiencyScope.CUMULATIVE_AVERAGE, "A", "100"),
            ),
        ).success()
        assertDecimal("12.5", saved.efficiency!!.value)
        assertDecimal("10", saved.baseline!!.value)
        assertEquals("A", saved.efficiency!!.aggregationKey)
    }

    @Test
    fun `blank event id and unknown trip are rejected`() = runTest {
        val trip = start()
        saveSegment(segmentCommand(trip.id, "  ", "1040", 2_000)).assertFailure(ErrorCode.INVALID_EVENT_ID)
        saveSegment(segmentCommand(999, "e1", "1040", 2_000)).assertFailure(ErrorCode.TRIP_NOT_FOUND)
    }

    // ---- finish ----

    @Test
    fun `finish below last recorded odometer is rejected and trip stays active`() = runTest {
        val trip = start()
        saveSegment(segmentCommand(trip.id, "e1", "1100", 2_000)).success()
        finishTrip(FinishTripCommand(trip.id, d("1090"), 3_000)).assertFailure(ErrorCode.ODOMETER_DECREASED)
        finishTrip(FinishTripCommand(trip.id, d("1100"), 1_500)).assertFailure(ErrorCode.INVALID_TIMESTAMP)
        assertEquals(TripStatus.IN_PROGRESS, store.trips.getValue(trip.id).status)
    }

    @Test
    fun `finish with zero total distance is rejected`() = runTest {
        val trip = start()
        finishTrip(FinishTripCommand(trip.id, d("1000"), 3_000)).assertFailure(ErrorCode.INVALID_DISTANCE)
    }

    @Test
    fun `finish records end values and is idempotent for identical retries`() = runTest {
        val trip = start()
        saveSegment(segmentCommand(trip.id, "e1", "1100", 2_000)).success()
        val finished = finishTrip(FinishTripCommand(trip.id, d("1100"), 3_000)).success()
        assertEquals(TripStatus.FINISHED, finished.status)
        assertDecimal("1100", finished.endOdometerKm!!)
        assertEquals(finished, finishTrip(FinishTripCommand(trip.id, d("1100.0"), 3_000)).success())
        finishTrip(FinishTripCommand(trip.id, d("1150"), 3_000)).assertFailure(ErrorCode.TRIP_NOT_IN_PROGRESS)
    }

    // ---- settlement ----

    @Test
    fun `confirm stores an immutable snapshot and marks the trip settled`() = runTest {
        val trip = finishedTripWithFullSegment()
        val settlement = confirm(ConfirmSettlementCommand(trip.id, "req-1", input("1500", headcount = 3))).success()

        // 100 km / 10 km/L = 10 L * 1500 = 15000 -> 5000 each
        assertEquals(15000L, settlement.calculation.totalCostWon)
        assertEquals(listOf(5000L, 5000L, 5000L), settlement.calculation.shares.map { it.amountWon })
        assertEquals(CalculationMethod.SEGMENT_SUM, settlement.calculation.method)
        assertEquals(5_000_000L, settlement.confirmedAtMillis)
        assertEquals(TripStatus.SETTLED, store.trips.getValue(trip.id).status)
        assertNotNull(settlements.findByTripId(trip.id))
    }

    @Test
    fun `duplicate confirmation with the same request returns the first settlement`() = runTest {
        val trip = finishedTripWithFullSegment()
        val first = confirm(ConfirmSettlementCommand(trip.id, "req-1", input("1500", headcount = 3))).success()
        val second = confirm(ConfirmSettlementCommand(trip.id, "req-1", input("1500.0", headcount = 3))).success()
        assertEquals(first.id, second.id)
        assertEquals(1, store.settlements.size)
    }

    @Test
    fun `second confirmation with a new request id is rejected`() = runTest {
        val trip = finishedTripWithFullSegment()
        confirm(ConfirmSettlementCommand(trip.id, "req-1", input("1500", headcount = 3))).success()
        confirm(ConfirmSettlementCommand(trip.id, "req-2", input("1500", headcount = 3)))
            .assertFailure(ErrorCode.ALREADY_SETTLED)
        assertEquals(1, store.settlements.size)
    }

    @Test
    fun `reusing a request id with different input is rejected`() = runTest {
        val trip = finishedTripWithFullSegment()
        confirm(ConfirmSettlementCommand(trip.id, "req-1", input("1500", headcount = 3))).success()
        confirm(ConfirmSettlementCommand(trip.id, "req-1", input("1600", headcount = 3)))
            .assertFailure(ErrorCode.REQUEST_ID_REUSED)
        confirm(ConfirmSettlementCommand(trip.id, "req-1", input("1500", headcount = 4)))
            .assertFailure(ErrorCode.REQUEST_ID_REUSED)
    }

    @Test
    fun `request id cannot be reused for another trip`() = runTest {
        val trip = finishedTripWithFullSegment()
        confirm(ConfirmSettlementCommand(trip.id, "req-1", input("1500"))).success()
        confirm(ConfirmSettlementCommand(trip.id + 100, "req-1", input("1500"))).assertFailure(ErrorCode.REQUEST_ID_REUSED)
    }

    @Test
    fun `incomplete segments block confirmation and persist nothing`() = runTest {
        val trip = start()
        saveSegment(segmentCommand(trip.id, "e1", "1060", 2_000)).success()
        finishTrip(FinishTripCommand(trip.id, d("1100"), 3_000)).success() // last 40 km never saved

        confirm(ConfirmSettlementCommand(trip.id, "req-1", input()))
            .assertFailure(ErrorCode.SEGMENT_GAP)
        assertEquals(0, store.settlements.size)
        assertEquals(TripStatus.FINISHED, store.trips.getValue(trip.id).status)
    }

    @Test
    fun `explicit fallback confirms with method and reason preserved`() = runTest {
        val trip = start()
        saveSegment(segmentCommand(trip.id, "e1", "1060", 2_000, efficiency = null)).success()
        saveSegment(segmentCommand(trip.id, "e2", "1100", 3_000)).success()
        finishTrip(FinishTripCommand(trip.id, d("1100"), 3_000)).success()

        val fallback = kmPerLiter("12.5", EfficiencyScope.VEHICLE_REFERENCE)
        val settlement = confirm(ConfirmSettlementCommand(trip.id, "req-1", input("1500", fallback = fallback))).success()
        assertEquals(CalculationMethod.REFERENCE_ESTIMATE, settlement.calculation.method)
        assertEquals(listOf(ErrorCode.MEASUREMENT_FAILED_SEGMENT), settlement.calculation.fallbackReasons)
        assertEquals(12000L, settlement.calculation.totalCostWon)
    }

    @Test
    fun `reference estimate replay checks the fallback efficiency`() = runTest {
        val trip = start()
        saveSegment(segmentCommand(trip.id, "e1", "1060", 2_000, efficiency = null)).success()
        saveSegment(segmentCommand(trip.id, "e2", "1100", 3_000)).success()
        finishTrip(FinishTripCommand(trip.id, d("1100"), 3_000)).success()

        val fallback = kmPerLiter("12.5", EfficiencyScope.VEHICLE_REFERENCE)
        val first = confirm(ConfirmSettlementCommand(trip.id, "req-1", input("1500", fallback = fallback))).success()

        // 같은 입력(스케일만 다른 동일 값 포함)은 기존 정산을 그대로 반환한다.
        val sameValue = kmPerLiter("12.50", EfficiencyScope.VEHICLE_REFERENCE)
        assertEquals(first, confirm(ConfirmSettlementCommand(trip.id, "req-1", input("1500", fallback = fallback))).success())
        assertEquals(first, confirm(ConfirmSettlementCommand(trip.id, "req-1", input("1500", fallback = sameValue))).success())

        // 다른 대체 연비, 또는 대체 연비 없음은 같은 요청 ID의 재사용이다.
        val other = kmPerLiter("10", EfficiencyScope.VEHICLE_REFERENCE)
        confirm(ConfirmSettlementCommand(trip.id, "req-1", input("1500", fallback = other)))
            .assertFailure(ErrorCode.REQUEST_ID_REUSED)
        confirm(ConfirmSettlementCommand(trip.id, "req-1", input("1500")))
            .assertFailure(ErrorCode.REQUEST_ID_REUSED)
        assertEquals(1, store.settlements.size)
    }

    @Test
    fun `unfinished unknown and invalid confirmations are rejected`() = runTest {
        val trip = start()
        confirm(ConfirmSettlementCommand(trip.id, "req-1", input())).assertFailure(ErrorCode.TRIP_NOT_FINISHED)
        confirm(ConfirmSettlementCommand(999, "req-2", input())).assertFailure(ErrorCode.TRIP_NOT_FOUND)
        confirm(ConfirmSettlementCommand(trip.id, " ", input())).assertFailure(ErrorCode.INVALID_REQUEST_ID)
        assertEquals(0, store.settlements.size)
    }

    @Test
    fun `invalid price or headcount on confirm leaves the trip finished`() = runTest {
        val trip = finishedTripWithFullSegment()
        confirm(ConfirmSettlementCommand(trip.id, "req-1", input("0"))).assertFailure(ErrorCode.INVALID_FUEL_PRICE)
        confirm(ConfirmSettlementCommand(trip.id, "req-2", input(headcount = 0))).assertFailure(ErrorCode.INVALID_HEADCOUNT)
        assertEquals(TripStatus.FINISHED, store.trips.getValue(trip.id).status)
        assertEquals(0, store.settlements.size)
    }

    @Test
    fun `preview calculates without persisting anything`() = runTest {
        val trip = finishedTripWithFullSegment()
        val calculation = preview(trip.id, input("1500", headcount = 2)).success()
        assertEquals(15000L, calculation.totalCostWon)
        assertEquals(0, store.settlements.size)
        assertEquals(TripStatus.FINISHED, store.trips.getValue(trip.id).status)
    }
}
