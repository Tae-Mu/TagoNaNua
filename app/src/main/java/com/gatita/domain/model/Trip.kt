package com.gatita.domain.model

import java.math.BigDecimal
import java.time.LocalDate

/** 데이터 출처. 화면에서 실제 차량 데이터와 구분해 표시해야 한다. */
enum class VehicleSource {
    /** 사용자가 직접 입력. */
    MANUAL,

    /** 개발·시연용 샘플. 실제 차량 값이 아니다. */
    SAMPLE,

    /** Pleos 차량에서 읽은 값. 아직 이 값을 만드는 구현은 없다. */
    PLEOS,
}

/**
 * 허용 전이: IN_PROGRESS → FINISHED → SETTLED.
 * 결제액 명세(html-api-spec)의 RECORDING은 IN_PROGRESS, COMPLETED는 FINISHED에 대응한다. 일시정지(PAUSED)는 아직 없다.
 */
enum class TripStatus { IN_PROGRESS, FINISHED, SETTLED }

/** 저장 전 신규 객체의 임시 id. */
const val UNSAVED_ID: Long = 0L

const val DEFAULT_TRIP_NAME = "새로운 여행"

data class Trip(
    val id: Long,
    val status: TripStatus,
    val source: VehicleSource,
    val startOdometerKm: BigDecimal,
    val startedAtMillis: Long,
    val endOdometerKm: BigDecimal?,
    val endedAtMillis: Long?,
    val name: String = DEFAULT_TRIP_NAME,
    /** 여행 시작 시점 시간대 기준 날짜. 월별 조회 기준이며 이후 바뀌지 않는다. v1에서 올라온 여행은 UTC 기준이다. */
    val tripDate: LocalDate = LocalDate.ofEpochDay(0),
    val zoneId: String = "UTC",
    /** 시작 당시 차량 표시 이름. 차량 식별자가 아니며 확인되지 않았으면 빈 문자열이다. */
    val vehicleLabel: String = "",
    /** 운전자 포함 인원 1..8. v1에서 올라온 정산 완료 여행은 옛 정산의 인원을 그대로 가진다(8 초과 가능). */
    val people: Int = 1,
    /** 운전자가 분담에 포함되는지. [people]이 1이면 항상 true. */
    val includeDriver: Boolean = true,
    /** 비용·구간·정책·상태가 바뀔 때마다 증가한다. 오래된 화면의 확정/수정을 막는 낙관적 잠금 값이다. */
    val revision: Long = 0,
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
    /** 이 여행의 정산 방식. 새 여행은 [FUEL_SEGMENTS_CALCULATION_VERSION], 기존 여행은 [PAYMENTS_CALCULATION_VERSION]이다. */
    val calculationVersion: String = PAYMENTS_CALCULATION_VERSION,
    /** 주유가 없는 여행에서 사용자가 직접 입력한 L당 단가. 없으면 유류비를 포함하지 않는다. */
    val manualFuelUnitPrice: BigDecimal? = null,
)

/**
 * 구간 기록. [efficiency]가 null이면 측정 실패 구간이다(0으로 바꾸지 않는다).
 * 측정 실패 구간은 거리만 보존되며, 정산에서 명시적으로 처리되기 전에는 확정을 막는다.
 */
data class Segment(
    val id: Long,
    val tripId: Long,
    val seq: Int,
    val eventId: String,
    val startOdometerKm: BigDecimal,
    val endOdometerKm: BigDecimal,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val efficiency: EfficiencyReading?,
    /** 누적 평균일 때 구간 시작 시점의 측정값. 단위·집계 기준·범위는 [efficiency]와 같아야 한다. */
    val baseline: EfficiencyReading?,
    val source: VehicleSource,
) {
    val distanceKm: BigDecimal get() = endOdometerKm.subtract(startOdometerKm)
    val isMeasured: Boolean get() = efficiency != null
}
