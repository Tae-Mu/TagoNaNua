package com.gatita.domain.model

import java.math.BigDecimal

/**
 * 구간별 유류비 방식(fuel-segments-v2) 모델. 결정 근거는 `.codex/docs/decisions.md`의
 * "구간별 유류비 방식으로 정산 규칙 변경"을 따른다.
 *
 * 총액 = Σ(주유 구간별 사용량 × 단가) + 기타 비용(PARKING/TOLL/OTHER). 반올림은 합계에서 한 번만 한다.
 * 단가가 바뀌는 곳은 주유뿐이므로 계산 단위는 "주유 구간"이다. P단 정차로 나눈 하위 구간은 표시용이다.
 */
const val FUEL_SEGMENTS_CALCULATION_VERSION = "fuel-segments-v2"

object FuelRules {
    /** 연료 %가 이만큼 이상 오르면 주유로 본다. 초기값이며 실차 노이즈는 확인되지 않았다. */
    const val REFUEL_PERCENT_THRESHOLD = 5

    /** P단에서 주행거리 변화 없이 이 시간 이상 정차하면 구간 경계로 본다. */
    const val PARK_STOP_SECONDS = 120L
}

object RefuelDetector {
    /** 연료 %는 정수다. 값이 하나라도 없으면 감지하지 않는다(0으로 대체하지 않음). */
    fun isRefuel(previousPercent: Int?, currentPercent: Int?, threshold: Int = FuelRules.REFUEL_PERCENT_THRESHOLD): Boolean =
        previousPercent != null && currentPercent != null && currentPercent - previousPercent >= threshold
}

/**
 * 짝짓기 대상 FUEL 비용. [FuelSettlementInput.fuelCosts]는 입력한 순서대로 둔다.
 * [undetected]가 true면 "감지 안 됨"으로 표시한 비용이며 순서 짝짓기와 단가 산정에서 제외한다.
 */
data class FuelCostRef(
    val costId: Long,
    val unitPriceWonPerLiter: BigDecimal,
    val undetected: Boolean = false,
)

/**
 * 한 주유 구간. 여행 시작 또는 주유 직후에 시작해 다음 주유 직전 또는 여행 종료에 끝난다.
 *
 * [start]는 첫 구간(ordinal 0)의 시작 측정이며 "마지막 주유 이후" 누적 연비다. 주유 직후에 시작하는 구간은 누적이 0에서
 * 출발하므로 null이다. [end]가 null이면 측정 실패이며 0으로 대체하지 않는다.
 * [distanceKm]는 주행거리계 기준 구간 거리로 표시용이다(계산에는 쓰지 않는다).
 */
data class FuelInterval(
    val ordinal: Int,
    val distanceKm: BigDecimal,
    val start: EfficiencyReading?,
    val end: EfficiencyReading?,
)

data class FuelSettlementInput(
    /** 시간순. 크기는 (주유 이벤트 수 + 1)이다. 제외("주유 아님")한 이벤트는 포함하지 않는다. */
    val intervals: List<FuelInterval>,
    /** 입력 순서의 FUEL 비용. */
    val fuelCosts: List<FuelCostRef>,
    /** 주유가 없는 여행에서 사용자가 직접 입력한 단가. 비어 있으면 유류비를 포함하지 않는다. */
    val manualUnitPrice: BigDecimal?,
    /** 포함된 PARKING/TOLL/OTHER 입력 금액의 합. */
    val otherTotalWon: Long,
)

enum class FuelExclusion {
    /** 구간 끝(또는 시작) 측정이 없거나 쓸 수 없다. */
    MEASUREMENT_MISSING,

    /** 적용할 단가가 없다(주유가 없는데 직접 입력도 없음). */
    UNIT_PRICE_MISSING,
}

data class FuelIntervalResult(
    val ordinal: Int,
    val distanceKm: BigDecimal,
    /** 사용량(L 또는 kWh). 제외된 구간은 null. */
    val usage: BigDecimal?,
    val unitPriceWonPerLiter: BigDecimal?,
    /** 반올림하지 않은 구간 유류비. 제외된 구간은 null. 합계는 이 값의 합을 한 번만 반올림한다. */
    val costWonExact: BigDecimal?,
    val exclusion: FuelExclusion?,
    val exclusionDetail: ErrorCode? = null,
)

data class FuelSettlementResult(
    val intervals: List<FuelIntervalResult>,
    /** Σ(구간 유류비)를 한 번 반올림(HALF_UP)한 값. */
    val fuelCostWon: Long,
    val otherTotalWon: Long,
    val totalWon: Long,
    val excludedIntervalCount: Int,
    /** 어떤 구간도 유류비가 계산되지 않았다. 화면은 "유류비 미포함"으로 표시한다. */
    val fuelNotIncluded: Boolean,
    val canConfirm: Boolean,
    val blockingReason: ErrorCode?,
)
