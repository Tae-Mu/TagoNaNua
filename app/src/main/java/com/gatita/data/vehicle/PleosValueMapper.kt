package com.gatita.data.vehicle

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
}
