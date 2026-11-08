package com.gatita.domain.repository

import com.gatita.domain.model.Settlement

interface SettlementRepository {
    suspend fun findByRequestId(requestId: String): Settlement?

    suspend fun findByTripId(tripId: Long): Settlement?

    /**
     * 정산 스냅샷(유가·항목·분담액)을 저장하고 여행을 SETTLED로 바꾼다. 하나의 트랜잭션이며 일부만 남지 않는다.
     * 여행당 정산 하나, 요청 ID당 정산 하나가 제약으로 보장되며 위반하면 예외를 던지고 아무것도 저장하지 않는다.
     */
    suspend fun saveAndMarkTripSettled(settlement: Settlement): Settlement
}
