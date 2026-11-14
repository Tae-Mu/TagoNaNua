package com.gatita.domain.model

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * 금액·거리·연비 계산의 정밀도 정책.
 *
 * - 저장되는 모든 BigDecimal은 [canonical] 형태(후행 0 제거, 음수 scale 없음)로 정규화해 equals 비교가 값 비교가 되게 한다.
 * - 나눗셈처럼 끝나지 않는 소수가 생기는 경우에만 [CALCULATION_CONTEXT](유효숫자 50자리)를 쓴다.
 *   원 단위 반올림은 합산이 끝난 최종 총액에서 한 번만([TOTAL_ROUNDING]) 수행한다.
 */
object Decimals {
    val CALCULATION_CONTEXT: MathContext = MathContext(50, RoundingMode.HALF_EVEN)
    val TOTAL_ROUNDING: RoundingMode = RoundingMode.HALF_UP

    const val PRECISION_POLICY: String =
        "BIGDECIMAL_PRECISION_50_FOR_DIVISION;NO_INTERMEDIATE_KRW_ROUNDING;" +
            "TOTAL_KRW_SCALE0_HALF_UP;REMAINDER_BY_PARTICIPANT_ORDER"

    fun canonical(value: BigDecimal): BigDecimal {
        if (value.signum() == 0) return BigDecimal.ZERO
        val stripped = value.stripTrailingZeros()
        return if (stripped.scale() < 0) stripped.setScale(0) else stripped
    }

    fun parse(text: String): BigDecimal = canonical(BigDecimal(text))
}
