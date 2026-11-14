package com.gatita.domain.calculator

import com.gatita.domain.model.Decimals
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.EfficiencyMath
import com.gatita.domain.model.EfficiencyReading
import com.gatita.domain.model.EfficiencyScope
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.FuelCostRef
import com.gatita.domain.model.FuelExclusion
import com.gatita.domain.model.FuelInterval
import com.gatita.domain.model.FuelIntervalResult
import com.gatita.domain.model.FuelSettlementInput
import com.gatita.domain.model.FuelSettlementResult
import com.gatita.domain.model.domainFailure
import java.math.BigDecimal

/**
 * 구간별 유류비 계산(fuel-segments-v2). 순수 함수이며 Android·Room·SDK에 의존하지 않는다.
 *
 * - 주유 구간 k의 단가: 구간 0과 1은 첫 주유 비용의 단가, 구간 k(k≥1)는 k번째 주유 비용의 단가.
 *   주유가 없으면 구간 0은 사용자가 직접 입력한 단가를 쓴다. 단가가 없으면 그 구간의 유류비는 포함하지 않는다.
 * - 사용량은 "마지막 주유 이후" 누적 연비의 측정값 차이로 구한다. 측정이 없는 구간은 0이나 평균으로 메우지 않고 제외한다.
 * - 금액은 구간별로 반올림하지 않고 합계에서 한 번만 HALF_UP으로 반올림한다.
 */
object FuelSettlementCalculator {

    /**
     * 감지된 주유 이벤트 수([refuelCount], "주유 아님" 제외)와 짝짓기 대상 FUEL 비용 수("감지 안 됨" 제외)가 같은지 확인한다.
     * 다르면 [ErrorCode.REFUEL_COST_MISMATCH]이며 여행을 종료할 수 없다.
     */
    fun checkRefuelMatching(refuelCount: Int, fuelCosts: List<FuelCostRef>): DomainResult<Unit> {
        val paired = fuelCosts.count { !it.undetected }
        return if (paired == refuelCount) {
            DomainResult.Success(Unit)
        } else {
            domainFailure(
                ErrorCode.REFUEL_COST_MISMATCH,
                "Detected refuels ($refuelCount) and paired fuel costs ($paired) must match.",
            )
        }
    }

    fun calculate(input: FuelSettlementInput): DomainResult<FuelSettlementResult> {
        if (input.otherTotalWon < 0) return domainFailure(ErrorCode.AMOUNT_OUT_OF_RANGE, "Other total must not be negative.")
        if (input.intervals.isEmpty()) return domainFailure(ErrorCode.NO_SEGMENTS, "At least one fuel interval is required.")
        input.intervals.forEachIndexed { index, interval ->
            if (interval.ordinal != index) {
                return domainFailure(ErrorCode.INVALID_DISTANCE, "Fuel intervals must be ordered from 0.")
            }
            if (interval.distanceKm.signum() < 0) {
                return domainFailure(ErrorCode.INVALID_DISTANCE, "Interval ${interval.ordinal} has a negative distance.")
            }
        }
        val refuelCount = input.intervals.size - 1
        checkRefuelMatching(refuelCount, input.fuelCosts).let { if (it is DomainResult.Failure) return it }

        val paired = input.fuelCosts.filter { !it.undetected }
        val results = input.intervals.map { interval ->
            val price = unitPriceFor(interval.ordinal, paired, input.manualUnitPrice)
            calculateInterval(interval, price)
        }

        val exactSum = results.mapNotNull { it.costWonExact }.fold(BigDecimal.ZERO, BigDecimal::add)
        val fuelCost = roundToWon(exactSum)
            ?: return domainFailure(ErrorCode.AMOUNT_OUT_OF_RANGE, "Fuel cost is out of range.")
        val total = try {
            Math.addExact(fuelCost, input.otherTotalWon)
        } catch (e: ArithmeticException) {
            return domainFailure(ErrorCode.AMOUNT_OUT_OF_RANGE, "Total is out of range.")
        }
        val excluded = results.count { it.exclusion != null }
        return DomainResult.Success(
            FuelSettlementResult(
                intervals = results,
                fuelCostWon = fuelCost,
                otherTotalWon = input.otherTotalWon,
                totalWon = total,
                excludedIntervalCount = excluded,
                fuelNotIncluded = excluded == results.size,
                canConfirm = total > 0,
                blockingReason = if (total > 0) null else ErrorCode.NO_INCLUDED_COSTS,
            ),
        )
    }

    private fun unitPriceFor(ordinal: Int, paired: List<FuelCostRef>, manual: BigDecimal?): BigDecimal? =
        when {
            paired.isEmpty() -> if (ordinal == 0) manual else null
            ordinal == 0 -> paired.first().unitPriceWonPerLiter
            else -> paired[ordinal - 1].unitPriceWonPerLiter
        }

    private fun calculateInterval(interval: FuelInterval, price: BigDecimal?): FuelIntervalResult {
        fun excluded(reason: FuelExclusion, detail: ErrorCode? = null) = FuelIntervalResult(
            ordinal = interval.ordinal,
            distanceKm = interval.distanceKm,
            usage = null,
            unitPriceWonPerLiter = price,
            costWonExact = null,
            exclusion = reason,
            exclusionDetail = detail,
        )

        val usage = when (val result = usageOf(interval)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return excluded(FuelExclusion.MEASUREMENT_MISSING, result.error.code)
        }
        if (price == null || price.signum() <= 0) return excluded(FuelExclusion.UNIT_PRICE_MISSING)
        return FuelIntervalResult(
            ordinal = interval.ordinal,
            distanceKm = interval.distanceKm,
            usage = usage,
            unitPriceWonPerLiter = price,
            costWonExact = usage.multiply(price),
            exclusion = null,
        )
    }

    /** 구간 사용량(L 또는 kWh). 첫 구간은 시작·끝 측정의 차이, 주유 직후 구간은 0에서 끝 측정까지다. */
    private fun usageOf(interval: FuelInterval): DomainResult<BigDecimal> {
        val end = interval.end
            ?: return domainFailure(ErrorCode.MEASUREMENT_FAILED_SEGMENT, "Interval ${interval.ordinal} has no end measurement.")
        if (interval.ordinal == 0) {
            val start = interval.start
                ?: return domainFailure(ErrorCode.MEASUREMENT_FAILED_SEGMENT, "Interval 0 has no start measurement.")
            return EfficiencyMath.segmentFuelLiters(interval.distanceKm, end, start)
        }
        return sinceRefuelUsage(end)
    }

    private fun sinceRefuelUsage(end: EfficiencyReading): DomainResult<BigDecimal> {
        EfficiencyMath.validate(end)?.let { return DomainResult.Failure(it) }
        if (end.scope != EfficiencyScope.CUMULATIVE_AVERAGE) {
            return domainFailure(ErrorCode.INVALID_EFFICIENCY_SCOPE, "Since-refuel usage needs a cumulative reading.")
        }
        val distance = checkNotNull(end.cumulativeDistanceKm)
        return DomainResult.Success(EfficiencyMath.fuelLiters(distance, end))
    }

    /** 합계를 원 단위로 한 번 반올림한다. Long 범위를 넘으면 null. */
    private fun roundToWon(value: BigDecimal): Long? = try {
        value.setScale(0, Decimals.TOTAL_ROUNDING).longValueExact()
    } catch (e: ArithmeticException) {
        null
    }
}
