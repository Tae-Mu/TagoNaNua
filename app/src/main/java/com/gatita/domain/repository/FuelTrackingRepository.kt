package com.gatita.domain.repository

import com.gatita.domain.model.FuelSnapshot
import com.gatita.domain.model.RefuelEvent
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

/**
 * 구간별 유류비 방식의 차량 측정·주유 이벤트 저장소. 규칙 검증은 유스케이스가 한다.
 * 사용자가 바꾸는 값(주유 아님, 감지 안 됨, 직접 입력 단가)은 소속 여행의 revision을 1 올린다.
 * 측정 저장과 주유 이벤트 추가는 진행 중 여행의 revision을 바꾸지 않는다(종료 시 expectedRevision 충돌 방지).
 */
interface FuelTrackingRepository {
    suspend fun insertSnapshot(snapshot: FuelSnapshot): FuelSnapshot

    /** 측정 시각, id 순서. */
    suspend fun getSnapshots(tripId: Long): List<FuelSnapshot>

    /** 화면의 이동 거리·시간 표시용. 측정이 저장될 때마다 새 목록을 내보낸다. */
    fun observeSnapshots(tripId: Long): Flow<List<FuelSnapshot>>

    suspend fun insertRefuel(event: RefuelEvent): RefuelEvent

    /** 감지 시각, id 순서. 제외한 이벤트도 포함한다. */
    suspend fun getRefuels(tripId: Long): List<RefuelEvent>

    fun observeRefuels(tripId: Long): Flow<List<RefuelEvent>>

    /** 이벤트가 속한 여행의 revision도 올린다. 없으면 false. */
    suspend fun setRefuelDismissed(eventId: Long, dismissed: Boolean, updatedAtMillis: Long): Boolean

    /** 비용이 속한 여행의 revision도 올린다. 없으면 false. */
    suspend fun setCostUndetected(costId: Long, undetected: Boolean, updatedAtMillis: Long): Boolean

    /** 직접 입력 단가(null이면 지움). 여행의 revision도 올린다. 없으면 false. */
    suspend fun setManualFuelUnitPrice(tripId: Long, price: BigDecimal?, updatedAtMillis: Long): Boolean
}
