package com.gatita.domain.repository

import com.gatita.domain.model.Cost
import com.gatita.domain.model.PaymentSettlement
import com.gatita.domain.model.TripSummary
import com.gatita.domain.model.TripWithCosts
import kotlinx.coroutines.flow.Flow

/**
 * 비용 저장소. 규칙 검증은 유스케이스가 한다.
 * 변경 함수(insert/update/setIncluded/delete)는 같은 트랜잭션에서 소속 여행의 revision을 1 올리고 updatedAt을 갱신한다.
 * 확정된 여행의 비용 변경은 DB 트리거도 거부한다.
 */
interface CostRepository {
    /** [Cost.id]가 [com.gatita.domain.model.UNSAVED_ID]인 비용을 저장하고 id를 채워 돌려준다. */
    suspend fun insert(cost: Cost): Cost

    /** 종류·금액·내용·단가·주유일·updatedAt만 바꾼다(included와 requestId는 보존). 없으면 false. */
    suspend fun update(cost: Cost): Boolean

    suspend fun setIncluded(costId: Long, included: Boolean, updatedAtMillis: Long): Boolean

    suspend fun delete(costId: Long, updatedAtMillis: Long): Boolean

    suspend fun get(costId: Long): Cost?

    suspend fun findByRequestId(requestId: String): Cost?

    /** 등록 순서(createdAt, id). */
    suspend fun getCosts(tripId: Long): List<Cost>
}

/** 비용과 분담 정책, 목록 조회. 새 화면은 여기서 여행과 비용을 한 번에 구독한다. */
interface TripLedgerRepository {
    /** 여행과 비용을 하나의 일관된 시점으로 내보낸다. 여행이 없으면 null. */
    fun observeTripWithCosts(tripId: Long): Flow<TripWithCosts?>

    /**
     * 종료(FINISHED) 상태이고 revision이 [expectedRevision]과 같을 때만 인원과 운전자 포함 여부를 바꾸고 revision을 올린다.
     * 조건이 맞지 않아 바꾸지 못하면 false.
     */
    suspend fun updateSplitPolicy(
        tripId: Long,
        people: Int,
        includeDriver: Boolean,
        expectedRevision: Long,
        updatedAtMillis: Long,
    ): Boolean

    /** 최근 시작한 여행부터 모든 여행의 요약. 월·상태 필터는 호출자가 적용한다. */
    fun observeSummaries(): Flow<List<TripSummary>>
}

interface PaymentSettlementRepository {
    suspend fun findByRequestId(requestId: String): PaymentSettlement?

    suspend fun findByTripId(tripId: Long): PaymentSettlement?

    /**
     * 스냅샷(정산·비용 전체·분담)을 저장하고 여행을 SETTLED로 바꾼다. 하나의 트랜잭션이며 일부만 남지 않는다.
     * 여행이 FINISHED가 아니거나 revision이 [expectedTripRevision]과 다르거나, 저장된 합계가 총액과 일치하지 않거나,
     * 여행당·요청 ID당 정산 유일 제약을 위반하면 [IllegalStateException] 또는 DB 예외를 던지고 아무것도 저장하지 않는다.
     */
    suspend fun saveAndMarkTripSettled(settlement: PaymentSettlement, expectedTripRevision: Long): PaymentSettlement
}
