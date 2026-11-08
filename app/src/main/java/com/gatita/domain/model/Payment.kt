package com.gatita.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * 결제액 방식(payments-v1) 모델. 정산 총액은 정산에 포함한 실제 결제 금액의 합계뿐이며,
 * 거리·연비·단가로 계산한 추정값은 어디에도 더하지 않는다.
 */
const val PAYMENTS_CALCULATION_VERSION = "payments-v1"

enum class CostKind { FUEL, PARKING, TOLL, OTHER }

object CostLimits {
    const val MIN_AMOUNT_WON = 1L
    const val MAX_AMOUNT_WON = 10_000_000L
    val MIN_UNIT_PRICE: BigDecimal = BigDecimal.ONE
    val MAX_UNIT_PRICE: BigDecimal = BigDecimal(9_999)
    const val UNIT_PRICE_MAX_SCALE = 2
    const val FUEL_STATION_MAX_LENGTH = 50
    const val DESCRIPTION_MAX_LENGTH = 100
    const val TRIP_NAME_MAX_LENGTH = 32
    const val DISPLAY_NAME_MAX_LENGTH = 20
    const val MIN_PEOPLE = 1
    const val MAX_PEOPLE = 8
}

/**
 * 비용 입력. FUEL은 [unitPriceWonPerLiter]가 필수이고 [description]은 주유소 이름이다.
 * [paidOn]이 null인 FUEL은 등록 시점의 지역 날짜로 채워진다.
 * FUEL이 아닌 종류의 [unitPriceWonPerLiter]/[paidOn]은 저장하지 않고 버려진다(종류 변경 시 입력 유지용).
 */
data class CostInput(
    val kind: CostKind,
    val amountWon: Long,
    val description: String? = null,
    val unitPriceWonPerLiter: BigDecimal? = null,
    val paidOn: LocalDate? = null,
)

/** 저장된 비용 한 건. 주유량은 저장하지 않고 [fuelLiters]로 파생한다. */
data class Cost(
    val id: Long,
    val tripId: Long,
    /** 이중 탭 등 같은 등록 시도를 식별하는 호출자 생성 ID. */
    val requestId: String,
    val kind: CostKind,
    val amountWon: Long,
    val description: String?,
    val unitPriceWonPerLiter: BigDecimal?,
    val paidOn: LocalDate?,
    /** 정산 합계에 포함할지. 신규 비용은 true이며 수정해도 유지된다. */
    val included: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    /** 표시용 주유량(L, 소수 3자리 HALF_UP). 정산 금액에는 쓰이지 않는다. FUEL이 아니면 null. */
    val fuelLiters: BigDecimal? get() = FuelLiters.calculate(kind, amountWonDecimal(), unitPriceWonPerLiter)

    private fun amountWonDecimal(): BigDecimal = BigDecimal.valueOf(amountWon)
}

object FuelLiters {
    const val DISPLAY_SCALE = 3

    fun calculate(kind: CostKind, amountWon: BigDecimal, unitPriceWonPerLiter: BigDecimal?): BigDecimal? {
        if (kind != CostKind.FUEL || unitPriceWonPerLiter == null || unitPriceWonPerLiter.signum() <= 0) return null
        return amountWon.divide(unitPriceWonPerLiter, DISPLAY_SCALE, RoundingMode.HALF_UP)
    }

    fun calculate(amountWon: Long, unitPriceWonPerLiter: BigDecimal): BigDecimal =
        BigDecimal.valueOf(amountWon).divide(unitPriceWonPerLiter, DISPLAY_SCALE, RoundingMode.HALF_UP)
}

/** 여행과 그 비용을 한 번에(같은 트랜잭션 시점으로) 읽은 값. 비용은 등록 순서다. */
data class TripWithCosts(val trip: Trip, val costs: List<Cost>)

/**
 * 비용 목록과 합계. 종류별 소계 [totalsByKind]는 포함 여부와 무관한 등록액이다.
 * [editable]이 false면(확정됨) 어떤 비용 변경도 거부된다.
 */
data class CostLedger(
    val tripId: Long,
    val tripStatus: TripStatus,
    val tripRevision: Long,
    val editable: Boolean,
    val costs: List<Cost>,
    val count: Int,
    val includedCount: Int,
    val registeredTotalWon: Long,
    val includedTotalWon: Long,
    val excludedTotalWon: Long,
    val totalsByKind: Map<CostKind, Long>,
)

// ---------------------------------------------------------------- 분담·미리보기·확정

/** [ordinal] 0이 운전자. 운전자를 제외해도 0원 행으로 남는다. */
data class PaymentShare(
    val ordinal: Int,
    val displayName: String,
    val isDriver: Boolean,
    val amountWon: Long,
)

enum class MeasurementQuality {
    /** 구간이 전체 거리를 빠짐없이 덮고 모든 구간의 연비가 측정됨. */
    VALID,

    /** 거리는 있으나 구간 누락·연비 측정 실패 등이 있음. 정산 금액에는 영향이 없다. */
    PARTIAL,

    /** 유효한 거리 기록이 없음(비용만 있는 여행 포함). 0으로 대체하지 않는다. */
    MISSING,
}

/**
 * 정산 금액과 무관한 기록 메타데이터. 측정 누락·실패는 null/[MeasurementQuality]로만 표현하며 확정을 막지 않는다.
 * [distanceKm]은 여행 주행거리계 구간(종료−시작)이고 [recordedSeconds]는 저장된 구간 시간의 합이다.
 */
data class RecordingMetadata(
    val source: VehicleSource,
    val distanceKm: BigDecimal?,
    val recordedSeconds: Long?,
    val quality: MeasurementQuality,
)

/** 확정 전 계산 결과. 저장하지 않는다. 확정은 [tripRevision]을 expectedRevision으로 보내야 한다. */
data class PaymentPreview(
    val tripId: Long,
    val tripRevision: Long,
    val tripStatus: TripStatus,
    val totalWon: Long,
    val fuelTotalWon: Long,
    val otherTotalWon: Long,
    val includedCount: Int,
    val registeredCount: Int,
    val people: Int,
    val splitPeople: Int,
    val includeDriver: Boolean,
    val shares: List<PaymentShare>,
    val canConfirm: Boolean,
    /** [canConfirm]이 false인 이유: [ErrorCode.TRIP_NOT_FINISHED] 또는 [ErrorCode.NO_INCLUDED_COSTS]. */
    val blockingReason: ErrorCode?,
    val recording: RecordingMetadata,
)

/** 확정 당시 비용의 복사본. 제외된 비용([included]=false)도 보존한다. [originalCostId]는 추적용이며 FK가 아니다. */
data class PaymentSettlementCost(
    val originalCostId: Long,
    val ordinal: Int,
    val kind: CostKind,
    val amountWon: Long,
    val description: String?,
    val unitPriceWonPerLiter: BigDecimal?,
    val paidOn: LocalDate?,
    val included: Boolean,
) {
    val fuelLiters: BigDecimal? get() = FuelLiters.calculate(kind, BigDecimal.valueOf(amountWon), unitPriceWonPerLiter)
}

/** 확정된 결제액 정산. 저장 후 수정 API가 없으며 DB 트리거도 UPDATE/DELETE를 거부한다. */
data class PaymentSettlement(
    val id: Long,
    val tripId: Long,
    /** 중복 확정 방지를 위한 호출자 생성 요청 ID. */
    val requestId: String,
    val tripName: String,
    val tripDate: LocalDate,
    val recording: RecordingMetadata,
    val totalWon: Long,
    val fuelTotalWon: Long,
    val otherTotalWon: Long,
    val people: Int,
    val includeDriver: Boolean,
    val splitPeople: Int,
    /** 이 정산이 계산된 시점의 여행 revision. */
    val sourceTripRevision: Long,
    val calculationVersion: String,
    val confirmedAtMillis: Long,
    val costs: List<PaymentSettlementCost>,
    val shares: List<PaymentShare>,
)

/** 정산 기록의 종류. 옛 추정 방식은 [Legacy]로 구분해 보여줘야 한다. */
sealed interface SettlementRecord {
    val tripId: Long

    data class Payments(val settlement: PaymentSettlement) : SettlementRecord {
        override val tripId: Long get() = settlement.tripId
    }

    /** 거리·연비·유가로 추정한 v1 정산. 결제액이 아니며 읽기 전용 이력이다. */
    data class Legacy(val settlement: Settlement) : SettlementRecord {
        override val tripId: Long get() = settlement.tripId
    }
}

// ---------------------------------------------------------------- 내역

enum class SettlementKind { PAYMENTS, LEGACY_ESTIMATE }

data class SettlementBrief(
    val kind: SettlementKind,
    val totalWon: Long,
    val confirmedAtMillis: Long,
)

/** 내역·홈 목록의 한 줄. 미확정 여행의 [includedTotalWon]은 현재 저장된 포함 비용 합계다. */
data class TripSummary(
    val tripId: Long,
    val name: String,
    val tripDate: LocalDate,
    val status: TripStatus,
    val source: VehicleSource,
    val people: Int,
    val includeDriver: Boolean,
    val revision: Long,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    /** 종료된 여행의 주행거리계 구간. 측정이 없으면(0 이하) null이며 0으로 대체하지 않는다. */
    val distanceKm: BigDecimal?,
    val costCount: Int,
    val includedCount: Int,
    val registeredTotalWon: Long,
    val includedTotalWon: Long,
    val settlement: SettlementBrief?,
)

enum class HistoryFilter { ALL, PENDING, SETTLED }

/** 월 통계는 상태 필터와 무관하고 [trips]만 필터링된다. */
data class HistorySummary(
    val month: java.time.YearMonth,
    val filter: HistoryFilter,
    val trips: List<TripSummary>,
    val monthlyTripCount: Int,
    val monthlyDistanceKm: BigDecimal,
    val monthlySettledCount: Int,
)

/** 정산 목록. [pendingTotalWon]은 종료됐지만 아직 확정하지 않은 여행의 포함 비용 합계다. */
data class SettlementList(
    val pending: List<TripSummary>,
    val settled: List<TripSummary>,
    val pendingTotalWon: Long,
)
