package com.gatita.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gatita.data.local.entity.SegmentEntity
import com.gatita.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    /** 진행 중 여행이 이미 있으면 activeSlot 유일 인덱스 위반으로 예외가 난다. */
    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun get(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE activeSlot = 1 LIMIT 1")
    suspend fun getActive(): TripEntity?

    @Query("SELECT * FROM trips WHERE activeSlot = 1 LIMIT 1")
    fun observeActive(): Flow<TripEntity?>

    @Query("SELECT * FROM trips ORDER BY startedAtMillis DESC, id DESC")
    fun observeAll(): Flow<List<TripEntity>>

    /** 변경된 행 수를 돌려준다. 진행 중이 아니면 0. */
    @Query(
        "UPDATE trips SET status = 'FINISHED', endOdometerKm = :endOdometerKm, endedAtMillis = :endedAtMillis, " +
            "activeSlot = NULL, revision = revision + 1, updatedAtMillis = :endedAtMillis " +
            "WHERE id = :id AND status = 'IN_PROGRESS'",
    )
    suspend fun markFinished(id: Long, endOdometerKm: String, endedAtMillis: Long): Int

    /** v1(추정) 정산 확정용. 상태 전이와 함께 revision을 올린다. */
    @Query(
        "UPDATE trips SET status = 'SETTLED', revision = revision + 1 " +
            "WHERE id = :id AND status = 'FINISHED'",
    )
    suspend fun markSettled(id: Long): Int

    /** 결제액 정산 확정용. FINISHED이고 revision이 일치할 때만 전이한다(낙관적 잠금). */
    @Query(
        "UPDATE trips SET status = 'SETTLED', revision = revision + 1, updatedAtMillis = :updatedAtMillis " +
            "WHERE id = :id AND status = 'FINISHED' AND revision = :expectedRevision",
    )
    suspend fun markSettledIfRevision(id: Long, expectedRevision: Long, updatedAtMillis: Long): Int

    /** 비용·구간 변경처럼 자식 데이터가 바뀔 때 revision을 올린다. */
    @Query("UPDATE trips SET revision = revision + 1, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun bumpRevision(id: Long, updatedAtMillis: Long): Int

    @Query(
        "UPDATE trips SET people = :people, includeDriver = :includeDriver, revision = revision + 1, " +
            "updatedAtMillis = :updatedAtMillis " +
            "WHERE id = :id AND status = 'FINISHED' AND revision = :expectedRevision",
    )
    suspend fun updateSplitPolicy(
        id: Long,
        people: Int,
        includeDriver: Boolean,
        expectedRevision: Long,
        updatedAtMillis: Long,
    ): Int

    /** 진행 중이며 구간·비용이 없는 여행만 지운다. 비용은 FK CASCADE라 조건에서 비어 있음을 반드시 확인한다. */
    @Query(
        "DELETE FROM trips WHERE id = :id AND status = 'IN_PROGRESS' AND activeSlot = 1 " +
            "AND NOT EXISTS (SELECT 1 FROM segments WHERE tripId = :id) " +
            "AND NOT EXISTS (SELECT 1 FROM costs WHERE tripId = :id)",
    )
    suspend fun deleteIfEmptyActive(id: Long): Int

    @Insert
    suspend fun insertSegment(segment: SegmentEntity): Long

    @Query("SELECT * FROM segments WHERE tripId = :tripId ORDER BY seq ASC")
    suspend fun getSegments(tripId: Long): List<SegmentEntity>

    @Query("SELECT * FROM segments WHERE tripId = :tripId AND eventId = :eventId LIMIT 1")
    suspend fun findSegmentByEventId(tripId: Long, eventId: String): SegmentEntity?
}
