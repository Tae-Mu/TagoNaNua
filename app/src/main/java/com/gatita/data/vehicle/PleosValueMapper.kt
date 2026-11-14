package com.gatita.data.vehicle

import com.gatita.domain.model.EfficiencyReading
import com.gatita.domain.model.EfficiencyScope
import com.gatita.domain.model.EfficiencyUnit
import java.math.BigDecimal

/** Pleos SDK의 원시 값을 도메인 값으로 바꾸는 순수 함수. SDK 클래스에 의존하지 않아 JVM 단위 테스트가 가능하다. */
internal object PleosValueMapper {
    /**
     * `Odometer.getCurrentDistanceDriven`의 값(km, `Float?`)을 정규화한 십진수로 바꾼다.
     *
     * `Float`를 `BigDecimal(double)`로 넘기면 이진 오차(예: 100.1 → 100.09999847…)가 생기므로 문자열을 거친다.
     * 값이 없거나(null), 유한하지 않거나, 음수이면 null을 돌려준다. 이 경우 호출자는 0으로 기록하지 않고 조회 실패로 처리한다.
     */
    fun odometerKm(value: Float?): BigDecimal? {
        if (value == null || value.isNaN() || value.isInfinite() || value < 0f) return null
        return BigDecimal(value.toString()).stripTrailingZeros().let {
            if (it.scale() < 0) it.setScale(0) else it
        }
    }

    /** 연료·배터리 잔량 %. 0..100 밖이거나 null이면 null(읽지 못함, 0이 아니다). */
    fun percent(value: Int?): Int? = value?.takeIf { it in 0..100 }

    /** `Odometer.getTripSinceRefillDrivingDistance`의 미터 값을 km로 바꾼다. 없거나 0 이하·비정상이면 null. */
    fun distanceKmFromMeters(meters: Float?): BigDecimal? {
        if (meters == null || meters.isNaN() || meters.isInfinite() || meters <= 0f) return null
        return BigDecimal(meters.toString()).divide(BigDecimal(1000))
    }

    /**
     * "마지막 주유(충전) 이후" 누적 연비. [value]는 내연 L/100km 또는 전기 km/kWh이며 [unit]이 이를 정한다.
     * 값이나 거리가 없거나 0 이하이면 null이다(0으로 기록하지 않는다). 집계 키는 호출자가 구간에 맞춰 다시 정한다.
     */
    fun sinceRefuelEfficiency(value: Float?, distanceKm: BigDecimal?, unit: EfficiencyUnit): EfficiencyReading? {
        if (value == null || value.isNaN() || value.isInfinite() || value <= 0f) return null
        if (distanceKm == null || distanceKm.signum() <= 0) return null
        return EfficiencyReading(
            value = BigDecimal(value.toString()),
            unit = unit,
            scope = EfficiencyScope.CUMULATIVE_AVERAGE,
            aggregationKey = "since-refuel",
            cumulativeDistanceKm = distanceKm,
        )
    }
}
