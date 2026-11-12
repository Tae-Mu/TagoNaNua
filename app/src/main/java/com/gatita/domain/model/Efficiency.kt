package com.gatita.domain.model

import java.math.BigDecimal

/** 연비 원본 단위. 차량이 어떤 단위를 주는지는 미확인이므로 변환하지 않고 원본 단위를 보존한다. */
enum class EfficiencyUnit {
    KM_PER_LITER,

    /** Pleos `Fuel` 연비 API의 단위(SDK 2.3.2 KDoc). */
    LITERS_PER_100_KM,

    /** Pleos `EvBattery` 효율 API의 단위(SDK 2.3.2 KDoc). 이 단위의 "연료량"은 L가 아니라 kWh다. */
    KM_PER_KWH,
}

/**
 * 연비 값이 무엇의 평균인지.
 *
 * - [SEGMENT_AVERAGE]: 해당 구간만의 평균.
 * - [CUMULATIVE_AVERAGE]: 어떤 집계 시작점부터의 누적 평균. 구간 평균으로 취급하면 안 된다.
 * - [VEHICLE_REFERENCE]: 차량 기준 연비. 정산 fallback 추정에만 쓴다.
 * - [UNKNOWN]: 집계 기준을 확인할 수 없음. 원본은 보존하지만 구간 합산에는 쓰지 않는다.
 */
enum class EfficiencyScope { SEGMENT_AVERAGE, CUMULATIVE_AVERAGE, VEHICLE_REFERENCE, UNKNOWN }

/**
 * 연비 원본 측정값.
 *
 * 누적 평균([EfficiencyScope.CUMULATIVE_AVERAGE])은 같은 [aggregationKey](같은 집계 시작점)를 가진 두 측정값과
 * 각 시점의 [cumulativeDistanceKm]이 있어야 사용량 차이를 계산할 수 있다.
 */
data class EfficiencyReading(
    val value: BigDecimal,
    val unit: EfficiencyUnit,
    val scope: EfficiencyScope,
    val aggregationKey: String? = null,
    val cumulativeDistanceKm: BigDecimal? = null,
)

object EfficiencyMath {
    fun validate(reading: EfficiencyReading): DomainError? {
        if (reading.value.signum() <= 0) {
            return DomainError(ErrorCode.INVALID_EFFICIENCY, "Efficiency must be greater than zero.")
        }
        if (reading.scope == EfficiencyScope.CUMULATIVE_AVERAGE) {
            if (reading.aggregationKey.isNullOrBlank()) {
                return DomainError(
                    ErrorCode.UNKNOWN_AGGREGATION,
                    "Cumulative efficiency requires an aggregation key.",
                )
            }
            val distance = reading.cumulativeDistanceKm
            if (distance == null || distance.signum() <= 0) {
                return DomainError(
                    ErrorCode.INVALID_DISTANCE,
                    "Cumulative efficiency requires a positive cumulative distance.",
                )
            }
        }
        return null
    }

    /** 거리 [distanceKm]를 이 연비로 주행했을 때의 연료량(L). [EfficiencyUnit.KM_PER_KWH]이면 전력량(kWh). 반올림하지 않는다. */
    fun fuelLiters(distanceKm: BigDecimal, reading: EfficiencyReading): BigDecimal =
        when (reading.unit) {
            EfficiencyUnit.KM_PER_LITER,
            EfficiencyUnit.KM_PER_KWH,
            -> distanceKm.divide(reading.value, Decimals.CALCULATION_CONTEXT)
            EfficiencyUnit.LITERS_PER_100_KM -> distanceKm.multiply(reading.value).movePointLeft(2)
        }

    /**
     * 구간의 연료 사용량(L).
     *
     * 구간 평균은 거리 ÷ 연비, 누적 평균은 같은 집계 기준의 누적 사용량 차이
     * (끝 시점 누적거리/연비 − 시작 시점 누적거리/연비)로 계산한다.
     */
    fun segmentFuelLiters(
        distanceKm: BigDecimal,
        end: EfficiencyReading,
        baseline: EfficiencyReading?,
    ): DomainResult<BigDecimal> {
        validate(end)?.let { return DomainResult.Failure(it) }
        return when (end.scope) {
            EfficiencyScope.SEGMENT_AVERAGE -> {
                if (baseline != null) {
                    domainFailure(ErrorCode.AGGREGATION_MISMATCH, "Segment-average efficiency must not have a baseline.")
                } else {
                    DomainResult.Success(fuelLiters(distanceKm, end))
                }
            }

            EfficiencyScope.CUMULATIVE_AVERAGE -> cumulativeFuelLiters(end, baseline)
            EfficiencyScope.UNKNOWN ->
                domainFailure(ErrorCode.UNKNOWN_AGGREGATION, "Efficiency aggregation basis is unknown.")

            EfficiencyScope.VEHICLE_REFERENCE ->
                domainFailure(
                    ErrorCode.INVALID_EFFICIENCY_SCOPE,
                    "Vehicle reference efficiency can only be used for an explicit fallback estimate.",
                )
        }
    }

    private fun cumulativeFuelLiters(end: EfficiencyReading, baseline: EfficiencyReading?): DomainResult<BigDecimal> {
        if (baseline == null) {
            return domainFailure(ErrorCode.AGGREGATION_MISMATCH, "Cumulative efficiency requires a baseline reading.")
        }
        validate(baseline)?.let { return DomainResult.Failure(it) }
        if (baseline.scope != EfficiencyScope.CUMULATIVE_AVERAGE ||
            baseline.unit != end.unit ||
            baseline.aggregationKey != end.aggregationKey
        ) {
            return domainFailure(
                ErrorCode.AGGREGATION_MISMATCH,
                "Baseline and end readings must share scope, unit and aggregation key.",
            )
        }
        val endDistance = checkNotNull(end.cumulativeDistanceKm)
        val baseDistance = checkNotNull(baseline.cumulativeDistanceKm)
        if (endDistance.compareTo(baseDistance) <= 0) {
            return domainFailure(
                ErrorCode.AGGREGATION_MISMATCH,
                "Cumulative distance did not advance; the aggregation may have been reset.",
            )
        }
        val liters = fuelLiters(endDistance, end).subtract(fuelLiters(baseDistance, baseline))
        if (liters.signum() <= 0) {
            return domainFailure(
                ErrorCode.INVALID_EFFICIENCY,
                "Cumulative readings imply non-positive fuel use for the segment.",
            )
        }
        return DomainResult.Success(liters)
    }
}
