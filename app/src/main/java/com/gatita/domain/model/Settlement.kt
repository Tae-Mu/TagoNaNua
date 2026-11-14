package com.gatita.domain.model

import java.math.BigDecimal

enum class FuelType { GASOLINE, DIESEL, LPG }

enum class PriceSource { MANUAL }

/** 수동 입력 유가. [priceDate]는 ISO-8601(yyyy-MM-dd) 기준일이며 선택이다. */
data class FuelPrice(
    val type: FuelType,
    val pricePerLiterWon: BigDecimal,
    val priceDate: String?,
    val source: PriceSource = PriceSource.MANUAL,
)

enum class CalculationMethod {
    /** 구간별 사용량을 합산. */
    SEGMENT_SUM,

    /** 구간 데이터를 쓸 수 없어 차량 기준 연비로 전체 거리를 추정. 결과에 사유가 함께 남는다. */
    REFERENCE_ESTIMATE,
}

/** 계산에 쓰인 한 항목(구간 또는 fallback 전체 추정)의 스냅샷. [segmentSeq]가 null이면 전체 여행 추정이다. */
data class SettlementItem(
    val index: Int,
    val segmentSeq: Int?,
    val distanceKm: BigDecimal,
    val efficiency: EfficiencyReading,
    val baseline: EfficiencyReading?,
    val fuelLiters: BigDecimal,
    val unitPriceWonPerLiter: BigDecimal,
    /** 반올림하지 않은 항목 비용. */
    val costWon: BigDecimal,
    val source: VehicleSource,
)

/** [participantIndex]는 1부터 시작하며 나머지 배분 순서이기도 하다. */
data class SettlementShare(
    val participantIndex: Int,
    val participantName: String?,
    val amountWon: Long,
)

data class SettlementCalculation(
    val method: CalculationMethod,
    /** [CalculationMethod.REFERENCE_ESTIMATE]를 쓴 이유. 구간 합산이면 비어 있다. */
    val fallbackReasons: List<ErrorCode>,
    val fuelPrice: FuelPrice,
    val headcount: Int,
    val totalFuelLiters: BigDecimal,
    val rawTotalCostWon: BigDecimal,
    val totalCostWon: Long,
    val precisionPolicy: String,
    val items: List<SettlementItem>,
    val shares: List<SettlementShare>,
)

/** 확정된 정산. 저장 후 변경하지 않는다. */
data class Settlement(
    val id: Long,
    val tripId: Long,
    /** 중복 확정 방지를 위한 호출자 생성 요청 ID. */
    val requestId: String,
    val confirmedAtMillis: Long,
    val calculation: SettlementCalculation,
)

/**
 * 정산 입력.
 *
 * [fallbackEfficiency]는 구간 데이터가 불완전할 때만 쓰이는 차량 기준 연비다([EfficiencyScope.VEHICLE_REFERENCE]).
 * 지정하지 않으면 불완전한 데이터는 실패로 보고된다.
 */
data class SettlementInput(
    val fuelPrice: FuelPrice,
    val headcount: Int,
    val participantNames: List<String>? = null,
    val fallbackEfficiency: EfficiencyReading? = null,
)
