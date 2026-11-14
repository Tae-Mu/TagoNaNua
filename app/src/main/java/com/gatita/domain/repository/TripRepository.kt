package com.gatita.domain.repository

import com.gatita.domain.model.Segment
import com.gatita.domain.model.Trip
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

/**
 * 여행·구간 저장소. 규칙 검증은 유스케이스가 하고, 구현은 저장과 DB 제약만 책임진다.
 * 진행 중 여행은 최대 하나이며 구현이 제약으로 보장해야 한다.
 */
interface TripRepository {
    /** [Trip.id]가 [com.gatita.domain.model.UNSAVED_ID]인 진행 중 여행을 저장하고 id가 채워진 값을 돌려준다. */
    suspend fun insertTrip(trip: Trip): Trip

    suspend fun getTrip(tripId: Long): Trip?

    /** 앱 재실행 후 복구할 진행 중 여행. 없으면 null. */
    suspend fun getActiveTrip(): Trip?

    fun observeActiveTrip(): Flow<Trip?>

    /** 최근 시작한 여행부터. 과거 내역 화면용. */
    fun observeTrips(): Flow<List<Trip>>

    /** 진행 중인 여행만 종료 상태로 바꾼다. 이미 진행 중이 아니면 [IllegalStateException]. */
    suspend fun markFinished(tripId: Long, endOdometerKm: BigDecimal, endedAtMillis: Long): Trip

    suspend fun getSegments(tripId: Long): List<Segment>

    suspend fun findSegmentByEventId(tripId: Long, eventId: String): Segment?

    /** [Segment.seq]는 호출자가 정해 전달하며 여행 안에서 유일해야 한다. */
    suspend fun appendSegment(segment: Segment): Segment
}

/**
 * 빈 진행 중 여행 닫기 전용 저장소. [TripRepository]와 분리해 일반 여행 삭제로 오해되지 않게 한다.
 * 구현은 DB 조건(진행 중, 구간·비용 없음)으로도 한 번 더 막아야 한다.
 */
interface EmptyTripRepository {
    /** 진행 중이고 구간·비용이 없는 여행만 삭제한다. 조건이 맞지 않아 삭제하지 않았으면 false. */
    suspend fun deleteIfEmptyActive(tripId: Long): Boolean
}
