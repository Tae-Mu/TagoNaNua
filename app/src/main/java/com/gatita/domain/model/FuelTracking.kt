package com.gatita.domain.model

import java.math.BigDecimal

enum class FuelSnapshotKind {
    /** 여행 시작 직후의 첫 측정. */
    START,

    /** 주기 측정. */
    PERIODIC,

    /** P단(읽지 못하면 거리 변화 없음)에서 정차 시간 기준을 넘긴 지점. 표시용 하위 구간 경계다. */
    STOP,

    /** 여행 종료 직전 측정. */
    END,
}

/**
 * 한 시점의 차량 측정 저장본. [efficiency]는 "마지막 주유 이후" 누적 연비이며 null이면 측정 실패다(0이 아니다).
 * 집계 키는 저장하지 않는다. 주유 구간(epoch)은 "주유 아님"으로 제외하지 않은 주유 이벤트 시각으로 계산 시점에 정한다.
 */
data class FuelSnapshot(
    val id: Long,
    val tripId: Long,
    val takenAtMillis: Long,
    val kind: FuelSnapshotKind,
    val odometerKm: BigDecimal,
    val fuelPercent: Int?,
    val efficiency: EfficiencyReading?,
    val parked: Boolean?,
    val source: VehicleSource,
)

/**
 * 연료 %가 임계값 이상 오른 것으로 감지한 주유. [detectedAtMillis]는 상승을 처음 본 측정 시각이다.
 * [dismissed]가 true면 사용자가 "주유 아님"으로 제외한 이벤트이며 계산과 짝짓기에서 빠진다.
 */
data class RefuelEvent(
    val id: Long,
    val tripId: Long,
    val detectedAtMillis: Long,
    val percentBefore: Int,
    val percentAfter: Int,
    val dismissed: Boolean = false,
)
