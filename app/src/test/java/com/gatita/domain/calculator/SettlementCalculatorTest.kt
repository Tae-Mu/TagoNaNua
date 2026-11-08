package com.gatita.domain.calculator

import com.gatita.assertDecimal
import com.gatita.assertFailure
import com.gatita.d
import com.gatita.domain.model.CalculationMethod
import com.gatita.domain.model.EfficiencyReading
import com.gatita.domain.model.EfficiencyScope
import com.gatita.domain.model.EfficiencyUnit
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.FuelType
import com.gatita.domain.model.SettlementInput
import com.gatita.domain.model.TripStatus
import com.gatita.failure
import com.gatita.finishedTrip
import com.gatita.input
import com.gatita.kmPerLiter
import com.gatita.price
import com.gatita.segment
import com.gatita.success
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettlementCalculatorTest {
    private val calculator = SettlementCalculator()

    @Test
    fun `3685 won among 4 people gives 922 921 921 921`() {
        // 100 km / 10 km/L = 10 L, 368.5 won/L -> 3685 won
        val result = calculator.calculate(
            finishedTrip("0", "100"),
            listOf(segment(1, "0", "100", kmPerLiter("10"))),
            input("368.5", headcount = 4),
        ).success()

        assertEquals(CalculationMethod.SEGMENT_SUM, result.method)
        assertEquals(3685L, result.totalCostWon)
        assertEquals(listOf(922L, 921L, 921L, 921L), result.shares.map { it.amountWon })
        assertEquals(listOf(1, 2, 3, 4), result.shares.map { it.participantIndex })
    }

    @Test
    fun `segment costs are summed before the single final HALF_UP rounding`() {
        // each segment costs exactly 1500.4 won; rounding each first would give 3000, summing first gives 3001
        val result = calculator.calculate(
            finishedTrip("0", "20"),
            listOf(segment(1, "0", "10", kmPerLiter("10")), segment(2, "10", "20", kmPerLiter("10"))),
            input("1500.4"),
        ).success()

        assertDecimal("3000.8", result.rawTotalCostWon)
        assertEquals(3001L, result.totalCostWon)
        assertEquals(2, result.items.size)
        result.items.forEach { assertDecimal("1500.4", it.costWon) }
    }

    @Test
    fun `half won rounds up`() {
        // 5 km / 10 km/L = 0.5 L * 1001 = 500.5 -> 501
        val result = calculator.calculate(
            finishedTrip("0", "5"),
            listOf(segment(1, "0", "5", kmPerLiter("10"))),
            input("1001"),
        ).success()
        assertEquals(501L, result.totalCostWon)
    }

    @Test
    fun `non terminating division does not leak rounding error into the total`() {
        // 100 / 3 = 33.333... L * 1500 = 50000
        val result = calculator.calculate(
            finishedTrip("0", "100"),
            listOf(segment(1, "0", "100", kmPerLiter("3"))),
            input("1500"),
        ).success()
        assertEquals(50000L, result.totalCostWon)
    }

    @Test
    fun `liters per 100 km is converted without changing the stored raw unit`() {
        val raw = EfficiencyReading(d("8"), EfficiencyUnit.LITERS_PER_100_KM, EfficiencyScope.SEGMENT_AVERAGE)
        val result = calculator.calculate(
            finishedTrip("0", "100"),
            listOf(segment(1, "0", "100", raw)),
            input("1700"),
        ).success()

        assertEquals(13600L, result.totalCostWon)
        assertDecimal("8", result.totalFuelLiters)
        assertEquals(raw, result.items.single().efficiency)
    }

    @Test
    fun `cumulative average uses usage difference not the end average`() {
        val baseline = kmPerLiter("10", EfficiencyScope.CUMULATIVE_AVERAGE, "reset-A", "100") // 10 L used
        val end = kmPerLiter("12.5", EfficiencyScope.CUMULATIVE_AVERAGE, "reset-A", "300") // 24 L used
        val result = calculator.calculate(
            finishedTrip("0", "200"),
            listOf(segment(1, "0", "200", end, baseline)),
            input("1000"),
        ).success()

        // difference is 14 L; treating 12.5 as a segment average would wrongly give 16 L
        assertDecimal("14", result.totalFuelLiters)
        assertEquals(14000L, result.totalCostWon)
        assertEquals(baseline, result.items.single().baseline)
        assertEquals("reset-A", result.items.single().efficiency.aggregationKey)
    }

    @Test
    fun `cumulative readings from different aggregations are rejected`() {
        val baseline = kmPerLiter("10", EfficiencyScope.CUMULATIVE_AVERAGE, "A", "100")
        val end = kmPerLiter("12", EfficiencyScope.CUMULATIVE_AVERAGE, "B", "300")
        calculator.calculate(
            finishedTrip("0", "200"),
            listOf(segment(1, "0", "200", end, baseline)),
            input(),
        ).assertFailure(ErrorCode.AGGREGATION_MISMATCH)
    }

    @Test
    fun `cumulative distance that did not advance is rejected as a possible reset`() {
        val baseline = kmPerLiter("10", EfficiencyScope.CUMULATIVE_AVERAGE, "A", "300")
        val end = kmPerLiter("12", EfficiencyScope.CUMULATIVE_AVERAGE, "A", "50")
        calculator.calculate(
            finishedTrip("0", "200"),
            listOf(segment(1, "0", "200", end, baseline)),
            input(),
        ).assertFailure(ErrorCode.AGGREGATION_MISMATCH)
    }

    @Test
    fun `cumulative average without baseline is rejected`() {
        val end = kmPerLiter("12", EfficiencyScope.CUMULATIVE_AVERAGE, "A", "300")
        calculator.calculate(
            finishedTrip("0", "200"),
            listOf(segment(1, "0", "200", end, null)),
            input(),
        ).assertFailure(ErrorCode.AGGREGATION_MISMATCH)
    }

    @Test
    fun `missing segment blocks settlement instead of being silently omitted`() {
        val error = calculator.calculate(
            finishedTrip("0", "100"),
            listOf(segment(1, "0", "40"), segment(2, "60", "100")),
            input(),
        ).failure()
        assertEquals(ErrorCode.SEGMENT_GAP, error.code)
    }

    @Test
    fun `uncovered tail after last segment is a gap`() {
        calculator.calculate(
            finishedTrip("0", "100"),
            listOf(segment(1, "0", "80")),
            input(),
        ).assertFailure(ErrorCode.SEGMENT_GAP)
    }

    @Test
    fun `uncovered head before first segment is a gap`() {
        calculator.calculate(
            finishedTrip("0", "100"),
            listOf(segment(1, "20", "100")),
            input(),
        ).assertFailure(ErrorCode.SEGMENT_GAP)
    }

    @Test
    fun `no segments blocks settlement`() {
        calculator.calculate(finishedTrip(), emptyList(), input()).assertFailure(ErrorCode.NO_SEGMENTS)
    }

    @Test
    fun `failed measurement is never treated as zero cost`() {
        val error = calculator.calculate(
            finishedTrip("0", "100"),
            listOf(segment(1, "0", "50"), segment(2, "50", "100", efficiency = null)),
            input(),
        ).failure()
        assertEquals(ErrorCode.MEASUREMENT_FAILED_SEGMENT, error.code)
    }

    @Test
    fun `unknown aggregation scope blocks segment sum`() {
        calculator.calculate(
            finishedTrip("0", "100"),
            listOf(segment(1, "0", "100", kmPerLiter("10", EfficiencyScope.UNKNOWN))),
            input(),
        ).assertFailure(ErrorCode.UNKNOWN_AGGREGATION)
    }

    @Test
    fun `all blocking issues are reported together`() {
        val error = calculator.calculate(
            finishedTrip("0", "100"),
            listOf(segment(1, "0", "40", efficiency = null), segment(2, "60", "90", kmPerLiter("10", EfficiencyScope.UNKNOWN))),
            input(),
        ).failure()
        assertTrue(
            error.issues.containsAll(
                listOf(ErrorCode.MEASUREMENT_FAILED_SEGMENT, ErrorCode.UNKNOWN_AGGREGATION, ErrorCode.SEGMENT_GAP),
            ),
        )
    }

    @Test
    fun `explicit fallback estimates the whole trip and records why`() {
        val reference = kmPerLiter("12.5", EfficiencyScope.VEHICLE_REFERENCE)
        val result = calculator.calculate(
            finishedTrip("0", "100"),
            listOf(segment(1, "0", "40")), // tail missing
            input("1500", headcount = 2, fallback = reference),
        ).success()

        assertEquals(CalculationMethod.REFERENCE_ESTIMATE, result.method)
        assertEquals(listOf(ErrorCode.SEGMENT_GAP), result.fallbackReasons)
        val item = result.items.single()
        assertNull(item.segmentSeq)
        assertDecimal("100", item.distanceKm) // whole trip, not just the recorded segment
        assertEquals(12000L, result.totalCostWon) // 100 / 12.5 = 8 L * 1500
        assertEquals(listOf(6000L, 6000L), result.shares.map { it.amountWon })
    }

    @Test
    fun `fallback is ignored when segment data is complete`() {
        val result = calculator.calculate(
            finishedTrip("0", "100"),
            listOf(segment(1, "0", "100", kmPerLiter("10"))),
            input("1500", fallback = kmPerLiter("5", EfficiencyScope.VEHICLE_REFERENCE)),
        ).success()
        assertEquals(CalculationMethod.SEGMENT_SUM, result.method)
        assertEquals(15000L, result.totalCostWon)
        assertTrue(result.fallbackReasons.isEmpty())
    }

    @Test
    fun `fallback must be a vehicle reference`() {
        calculator.calculate(
            finishedTrip("0", "100"),
            emptyList(),
            input(fallback = kmPerLiter("10", EfficiencyScope.SEGMENT_AVERAGE)),
        ).assertFailure(ErrorCode.INVALID_EFFICIENCY_SCOPE)
    }

    @Test
    fun `fallback with non positive efficiency is rejected`() {
        calculator.calculate(
            finishedTrip("0", "100"),
            emptyList(),
            input(fallback = kmPerLiter("0", EfficiencyScope.VEHICLE_REFERENCE)),
        ).assertFailure(ErrorCode.INVALID_EFFICIENCY)
    }

    @Test
    fun `overlapping segments are an error even with a fallback`() {
        calculator.calculate(
            finishedTrip("0", "100"),
            listOf(segment(1, "0", "60"), segment(2, "50", "100")),
            input(fallback = kmPerLiter("10", EfficiencyScope.VEHICLE_REFERENCE)),
        ).assertFailure(ErrorCode.SEGMENT_OVERLAP)
    }

    @Test
    fun `segments beyond trip end are an error`() {
        calculator.calculate(
            finishedTrip("0", "100"),
            listOf(segment(1, "0", "120")),
            input(),
        ).assertFailure(ErrorCode.ODOMETER_DECREASED)
    }

    @Test
    fun `non positive efficiency on a stored segment is rejected`() {
        calculator.calculate(
            finishedTrip("0", "100"),
            listOf(segment(1, "0", "100", kmPerLiter("0"))),
            input(),
        ).assertFailure(ErrorCode.INVALID_EFFICIENCY)
        calculator.calculate(
            finishedTrip("0", "100"),
            listOf(segment(1, "0", "100", kmPerLiter("-3"))),
            input(),
        ).assertFailure(ErrorCode.INVALID_EFFICIENCY)
    }

    @Test
    fun `unfinished trip cannot be calculated`() {
        calculator.calculate(
            finishedTrip().copy(status = TripStatus.IN_PROGRESS, endOdometerKm = null, endedAtMillis = null),
            emptyList(),
            input(),
        ).assertFailure(ErrorCode.TRIP_NOT_FINISHED)
    }

    @Test
    fun `invalid price headcount and participants are rejected`() {
        val trip = finishedTrip()
        val segments = listOf(segment(1, "0", "100"))
        calculator.calculate(trip, segments, input("0")).assertFailure(ErrorCode.INVALID_FUEL_PRICE)
        calculator.calculate(trip, segments, input("-1500")).assertFailure(ErrorCode.INVALID_FUEL_PRICE)
        calculator.calculate(trip, segments, input(headcount = 0)).assertFailure(ErrorCode.INVALID_HEADCOUNT)
        calculator.calculate(trip, segments, input(headcount = -2)).assertFailure(ErrorCode.INVALID_HEADCOUNT)
        calculator.calculate(trip, segments, input(headcount = SettlementCalculator.MAX_HEADCOUNT + 1))
            .assertFailure(ErrorCode.INVALID_HEADCOUNT)
        calculator.calculate(
            trip,
            segments,
            SettlementInput(price("1500").copy(priceDate = "09/11/2026"), 2),
        ).assertFailure(ErrorCode.INVALID_FUEL_PRICE)
        calculator.calculate(trip, segments, SettlementInput(price("1500"), 2, listOf("A")))
            .assertFailure(ErrorCode.INVALID_PARTICIPANTS)
        calculator.calculate(trip, segments, SettlementInput(price("1500"), 2, listOf("A", " ")))
            .assertFailure(ErrorCode.INVALID_PARTICIPANTS)
    }

    @Test
    fun `participant names with control characters are rejected`() {
        val trip = finishedTrip()
        val segments = listOf(segment(1, "0", "100"))
        // 줄바꿈·제어 문자는 QR 요약 텍스트에 가짜 줄을 만들 수 있다.
        for (name in listOf("A\nB", "A\rB", "A\r\nB", "A\tB", "A\u0000B", "A\u0085B", "\nA")) {
            calculator.calculate(trip, segments, SettlementInput(price("1500"), 2, listOf("Kim", name)))
                .assertFailure(ErrorCode.INVALID_PARTICIPANTS)
        }
    }

    @Test
    fun `participant names are trimmed and aligned with share order`() {
        val result = calculator.calculate(
            finishedTrip("0", "100"),
            listOf(segment(1, "0", "100")),
            SettlementInput(price("1000").copy(type = FuelType.DIESEL), 3, listOf(" 민수 ", "지은", "철수")),
        ).success()
        // 10 L * 1000 = 10000 -> 3334, 3333, 3333
        assertEquals(listOf("민수", "지은", "철수"), result.shares.map { it.participantName })
        assertEquals(listOf(3334L, 3333L, 3333L), result.shares.map { it.amountWon })
        assertEquals(FuelType.DIESEL, result.fuelPrice.type)
    }

    @Test
    fun `item snapshot keeps applied distance efficiency price and source`() {
        val result = calculator.calculate(
            finishedTrip("1000", "1100"),
            listOf(segment(1, "1000", "1100", kmPerLiter("12.50"))),
            input("1650.0"),
        ).success()
        val item = result.items.single()
        assertDecimal("100", item.distanceKm)
        assertDecimal("12.5", item.efficiency.value)
        assertDecimal("1650", item.unitPriceWonPerLiter)
        assertEquals(1, item.segmentSeq)
        assertEquals(com.gatita.domain.model.VehicleSource.MANUAL, item.source)
    }
}
