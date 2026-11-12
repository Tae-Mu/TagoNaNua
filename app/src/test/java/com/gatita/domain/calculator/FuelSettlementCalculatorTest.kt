package com.gatita.domain.calculator

import com.gatita.d
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.EfficiencyReading
import com.gatita.domain.model.EfficiencyScope
import com.gatita.domain.model.EfficiencyUnit
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.FuelCostRef
import com.gatita.domain.model.FuelExclusion
import com.gatita.domain.model.FuelInterval
import com.gatita.domain.model.FuelRules
import com.gatita.domain.model.FuelSettlementInput
import com.gatita.domain.model.FuelSettlementResult
import com.gatita.domain.model.RefuelDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FuelSettlementCalculatorTest {

    /** "마지막 주유 이후" 누적 연비(L/100km). [km]는 그 시점의 누적 거리다. 사용량은 km × value ÷ 100. */
    private fun l100(value: String, km: String) = EfficiencyReading(
        d(value), EfficiencyUnit.LITERS_PER_100_KM, EfficiencyScope.CUMULATIVE_AVERAGE, "since-refuel", d(km),
    )

    private fun interval(ordinal: Int, km: String, start: EfficiencyReading?, end: EfficiencyReading?) =
        FuelInterval(ordinal, d(km), start, end)

    private fun cost(id: Long, price: String, undetected: Boolean = false) = FuelCostRef(id, d(price), undetected)

    private fun ok(result: DomainResult<FuelSettlementResult>): FuelSettlementResult =
        (result as? DomainResult.Success)?.value ?: fail("expected success but was $result").let { error("unreachable") }

    private fun failure(result: DomainResult<*>): ErrorCode =
        (result as? DomainResult.Failure)?.error?.code ?: fail("expected failure but was $result").let { error("unreachable") }

    // 구간 0: 100km@8.0 → 160km@8.5 = 8.0L → 13.6L, 사용 5.6L
    private val start0 = l100("8.0", "100")
    private val end0 = l100("8.5", "160")

    @Test
    fun `no refuel uses the manual price for the single interval`() {
        val r = ok(
            FuelSettlementCalculator.calculate(
                FuelSettlementInput(
                    intervals = listOf(interval(0, "60", start0, end0)),
                    fuelCosts = emptyList(),
                    manualUnitPrice = d("1800"),
                    otherTotalWon = 3_000,
                ),
            ),
        )
        assertEquals(0, r.intervals.single().usage!!.compareTo(d("5.6")))
        assertEquals(10_080L, r.fuelCostWon)
        assertEquals(13_080L, r.totalWon)
        assertFalse(r.fuelNotIncluded)
        assertTrue(r.canConfirm)
    }

    @Test
    fun `no refuel and no manual price excludes fuel but still settles other costs`() {
        val r = ok(
            FuelSettlementCalculator.calculate(
                FuelSettlementInput(listOf(interval(0, "60", start0, end0)), emptyList(), null, otherTotalWon = 5_000),
            ),
        )
        val only = r.intervals.single()
        assertEquals(FuelExclusion.UNIT_PRICE_MISSING, only.exclusion)
        assertNull(only.costWonExact)
        assertEquals(0L, r.fuelCostWon)
        assertEquals(5_000L, r.totalWon)
        assertTrue(r.fuelNotIncluded)
        assertTrue(r.canConfirm)
    }

    @Test
    fun `one refuel applies its price to the intervals before and after it`() {
        // 구간 1은 주유 직후 0에서 시작: 50km@9.0 = 4.5L
        val r = ok(
            FuelSettlementCalculator.calculate(
                FuelSettlementInput(
                    intervals = listOf(interval(0, "60", start0, end0), interval(1, "50", null, l100("9.0", "50"))),
                    fuelCosts = listOf(cost(1, "1800")),
                    manualUnitPrice = null,
                    otherTotalWon = 0,
                ),
            ),
        )
        assertEquals(d("1800"), r.intervals[0].unitPriceWonPerLiter)
        assertEquals(d("1800"), r.intervals[1].unitPriceWonPerLiter)
        // 5.6 × 1800 + 4.5 × 1800 = 10,080 + 8,100
        assertEquals(18_180L, r.totalWon)
    }

    @Test
    fun `second refuel price applies only after the second refuel`() {
        val r = ok(
            FuelSettlementCalculator.calculate(
                FuelSettlementInput(
                    intervals = listOf(
                        interval(0, "60", start0, end0),
                        interval(1, "50", null, l100("9.0", "50")),
                        interval(2, "40", null, l100("10.0", "40")),
                    ),
                    fuelCosts = listOf(cost(1, "1800"), cost(2, "1600")),
                    manualUnitPrice = null,
                    otherTotalWon = 0,
                ),
            ),
        )
        assertEquals(listOf(d("1800"), d("1800"), d("1600")), r.intervals.map { it.unitPriceWonPerLiter })
        // 10,080 + 8,100 + (4.0L × 1,600 = 6,400)
        assertEquals(24_580L, r.totalWon)
    }

    @Test
    fun `refuel count and paired cost count must match`() {
        val input = FuelSettlementInput(
            intervals = listOf(interval(0, "60", start0, end0), interval(1, "50", null, l100("9.0", "50"))),
            fuelCosts = emptyList(),
            manualUnitPrice = null,
            otherTotalWon = 1_000,
        )
        assertEquals(ErrorCode.REFUEL_COST_MISMATCH, failure(FuelSettlementCalculator.calculate(input)))
        assertEquals(ErrorCode.REFUEL_COST_MISMATCH, failure(FuelSettlementCalculator.checkRefuelMatching(1, emptyList())))
        assertTrue(FuelSettlementCalculator.checkRefuelMatching(1, listOf(cost(1, "1800"))) is DomainResult.Success)
        assertTrue(FuelSettlementCalculator.checkRefuelMatching(0, emptyList()) is DomainResult.Success)
    }

    @Test
    fun `an undetected cost is skipped by the order pairing and does not set a price`() {
        // 감지된 주유 1건, 입력 비용은 2건이지만 첫 번째가 "감지 안 됨" → 두 번째가 감지 주유와 짝이 된다.
        val input = FuelSettlementInput(
            intervals = listOf(interval(0, "60", start0, end0), interval(1, "50", null, l100("9.0", "50"))),
            fuelCosts = listOf(cost(1, "1900", undetected = true), cost(2, "1800")),
            manualUnitPrice = null,
            otherTotalWon = 0,
        )
        val r = ok(FuelSettlementCalculator.calculate(input))
        assertEquals(d("1800"), r.intervals[0].unitPriceWonPerLiter)
        assertEquals(18_180L, r.totalWon)
        // 감지 안 됨 표시가 없으면 개수가 맞지 않아 막힌다.
        val unmarked = input.copy(fuelCosts = listOf(cost(1, "1900"), cost(2, "1800")))
        assertEquals(ErrorCode.REFUEL_COST_MISMATCH, failure(FuelSettlementCalculator.calculate(unmarked)))
    }

    @Test
    fun `an interval without a measurement is excluded and never filled with zero or an average`() {
        val r = ok(
            FuelSettlementCalculator.calculate(
                FuelSettlementInput(
                    intervals = listOf(interval(0, "60", start0, end0), interval(1, "50", null, null)),
                    fuelCosts = listOf(cost(1, "1800")),
                    manualUnitPrice = null,
                    otherTotalWon = 2_000,
                ),
            ),
        )
        assertEquals(FuelExclusion.MEASUREMENT_MISSING, r.intervals[1].exclusion)
        assertNotNull(r.intervals[1].exclusionDetail)
        assertEquals(1, r.excludedIntervalCount)
        assertFalse(r.fuelNotIncluded)
        assertEquals(10_080L + 2_000L, r.totalWon)
    }

    @Test
    fun `a missing start measurement on the first interval excludes it`() {
        val r = ok(
            FuelSettlementCalculator.calculate(
                FuelSettlementInput(listOf(interval(0, "60", null, end0)), emptyList(), d("1800"), otherTotalWon = 1_000),
            ),
        )
        assertEquals(FuelExclusion.MEASUREMENT_MISSING, r.intervals.single().exclusion)
        assertEquals(1_000L, r.totalWon)
    }

    @Test
    fun `a reset aggregation is excluded instead of producing a wrong amount`() {
        // 끝 누적 거리가 시작보다 작음(주유로 리셋되었는데 감지하지 못한 경우)
        val r = ok(
            FuelSettlementCalculator.calculate(
                FuelSettlementInput(
                    listOf(interval(0, "60", l100("8.0", "100"), l100("8.5", "30"))),
                    emptyList(), d("1800"), otherTotalWon = 1_000,
                ),
            ),
        )
        assertEquals(FuelExclusion.MEASUREMENT_MISSING, r.intervals.single().exclusion)
        assertEquals(ErrorCode.AGGREGATION_MISMATCH, r.intervals.single().exclusionDetail)
    }

    @Test
    fun `rounding happens once on the sum not per interval`() {
        // 각 0.4원씩: 구간별 반올림이면 0+0=0, 합계 반올림이면 0.8 → 1
        val tiny = l100("1.0", "1") // 0.01L
        val r = ok(
            FuelSettlementCalculator.calculate(
                FuelSettlementInput(
                    intervals = listOf(
                        FuelInterval(0, d("1"), l100("1.0", "1").copy(cumulativeDistanceKm = d("1")), l100("1.0", "2")),
                        FuelInterval(1, d("1"), null, tiny),
                    ),
                    fuelCosts = listOf(FuelCostRef(1, d("40"))),
                    manualUnitPrice = null,
                    otherTotalWon = 0,
                ),
            ),
        )
        // 구간 0: (2-1)km×1.0/100 = 0.01L × 40 = 0.4원, 구간 1: 0.01L × 40 = 0.4원
        assertEquals(0, r.intervals[0].costWonExact!!.compareTo(d("0.4")))
        assertEquals(1L, r.fuelCostWon)
    }

    @Test
    fun `half won rounds up`() {
        val r = ok(
            FuelSettlementCalculator.calculate(
                FuelSettlementInput(
                    listOf(interval(0, "50", l100("1.0", "50"), l100("1.0", "100"))), // 0.5L
                    emptyList(), d("1"), 0,
                ),
            ),
        )
        assertEquals(1L, r.fuelCostWon) // 0.5원 → 1
    }

    @Test
    fun `electric vehicles use km per kWh and the same rules`() {
        fun kmKwh(value: String, km: String) = EfficiencyReading(
            d(value), EfficiencyUnit.KM_PER_KWH, EfficiencyScope.CUMULATIVE_AVERAGE, "since-charge", d(km),
        )
        val r = ok(
            FuelSettlementCalculator.calculate(
                FuelSettlementInput(
                    // 100km ÷ 5.0 = 20kWh → 200km ÷ 5.0 = 40kWh, 사용 20kWh
                    listOf(interval(0, "100", kmKwh("5.0", "100"), kmKwh("5.0", "200"))),
                    emptyList(), d("300"), otherTotalWon = 0,
                ),
            ),
        )
        assertEquals(0, r.intervals.single().usage!!.compareTo(d("20")))
        assertEquals(6_000L, r.totalWon)
    }

    @Test
    fun `zero total cannot be confirmed`() {
        val r = ok(
            FuelSettlementCalculator.calculate(
                FuelSettlementInput(listOf(interval(0, "60", start0, end0)), emptyList(), null, otherTotalWon = 0),
            ),
        )
        assertEquals(0L, r.totalWon)
        assertFalse(r.canConfirm)
        assertEquals(ErrorCode.NO_INCLUDED_COSTS, r.blockingReason)
    }

    @Test
    fun `shares reuse the existing integer split and match the total`() {
        val r = ok(
            FuelSettlementCalculator.calculate(
                FuelSettlementInput(
                    listOf(interval(0, "60", start0, end0), interval(1, "50", null, l100("9.0", "50"))),
                    listOf(cost(1, "1800")), null, otherTotalWon = 3_001,
                ),
            ),
        )
        assertEquals(21_181L, r.totalWon)
        val shares = PaymentSettlementCalculator.calculateShares(r.totalWon, 4, true, "나")
        assertEquals(r.totalWon, shares.sumOf { it.amountWon })
        assertEquals(listOf(5_296L, 5_295L, 5_295L, 5_295L), shares.map { it.amountWon })
    }

    @Test
    fun `invalid inputs are rejected`() {
        assertEquals(ErrorCode.NO_SEGMENTS, failure(FuelSettlementCalculator.calculate(FuelSettlementInput(emptyList(), emptyList(), null, 0))))
        assertEquals(
            ErrorCode.AMOUNT_OUT_OF_RANGE,
            failure(FuelSettlementCalculator.calculate(FuelSettlementInput(listOf(interval(0, "1", start0, end0)), emptyList(), null, -1))),
        )
        assertEquals(
            ErrorCode.INVALID_DISTANCE,
            failure(FuelSettlementCalculator.calculate(FuelSettlementInput(listOf(interval(1, "1", start0, end0)), emptyList(), null, 0))),
        )
        assertEquals(
            ErrorCode.INVALID_DISTANCE,
            failure(FuelSettlementCalculator.calculate(FuelSettlementInput(listOf(interval(0, "-1", start0, end0)), emptyList(), null, 0))),
        )
    }

    @Test
    fun `refuel detection needs both readings and the threshold`() {
        assertEquals(5, FuelRules.REFUEL_PERCENT_THRESHOLD)
        assertTrue(RefuelDetector.isRefuel(40, 45))
        assertTrue(RefuelDetector.isRefuel(20, 80))
        assertFalse(RefuelDetector.isRefuel(40, 44))
        assertFalse(RefuelDetector.isRefuel(45, 40))
        assertFalse(RefuelDetector.isRefuel(null, 80))
        assertFalse(RefuelDetector.isRefuel(40, null))
        assertEquals(120L, FuelRules.PARK_STOP_SECONDS)
    }

}
