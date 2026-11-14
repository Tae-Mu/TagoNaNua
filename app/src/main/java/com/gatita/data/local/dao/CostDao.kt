package com.gatita.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.gatita.data.local.entity.CostEntity
import com.gatita.data.local.entity.TripSummaryRow
import com.gatita.data.local.entity.TripWithCostsRelation
import kotlinx.coroutines.flow.Flow

@Dao
interface CostDao {
    /** requestId가 이미 있으면 유일 인덱스 위반으로 예외가 난다. */
    @Insert
    suspend fun insert(cost: CostEntity): Long

    @Query("SELECT * FROM costs WHERE id = :id")
    suspend fun get(id: Long): CostEntity?

    @Query("SELECT * FROM costs WHERE requestId = :requestId LIMIT 1")
    suspend fun findByRequestId(requestId: String): CostEntity?

    @Query("SELECT * FROM costs WHERE tripId = :tripId ORDER BY createdAtMillis ASC, id ASC")
    suspend fun getCosts(tripId: Long): List<CostEntity>

    @Query(
        "UPDATE costs SET kind = :kind, amountWon = :amountWon, description = :description, " +
            "unitPrice = :unitPrice, paidOn = :paidOn, updatedAtMillis = :updatedAtMillis WHERE id = :id",
    )
    suspend fun updateContent(
        id: Long,
        kind: String,
        amountWon: Long,
        description: String?,
        unitPrice: String?,
        paidOn: String?,
        updatedAtMillis: Long,
    ): Int

    @Query("UPDATE costs SET included = :included, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun setIncluded(id: Long, included: Boolean, updatedAtMillis: Long): Int

    @Query("DELETE FROM costs WHERE id = :id")
    suspend fun delete(id: Long): Int

    /** 여행과 비용을 한 트랜잭션으로 읽어 revision과 목록이 어긋나지 않게 한다. */
    @Transaction
    @Query("SELECT * FROM trips WHERE id = :tripId")
    fun observeTripWithCosts(tripId: Long): Flow<TripWithCostsRelation?>

    @Query(
        "SELECT t.id AS tripId, t.name AS name, t.tripDate AS tripDate, t.status AS status, t.source AS source, " +
            "t.people AS people, t.includeDriver AS includeDriver, t.revision AS revision, " +
            "t.startedAtMillis AS startedAtMillis, t.endedAtMillis AS endedAtMillis, " +
            "t.startOdometerKm AS startOdometerKm, t.endOdometerKm AS endOdometerKm, " +
            "(SELECT COUNT(*) FROM costs c WHERE c.tripId = t.id) AS costCount, " +
            "(SELECT COUNT(*) FROM costs c WHERE c.tripId = t.id AND c.included = 1) AS includedCount, " +
            "(SELECT COALESCE(SUM(c.amountWon), 0) FROM costs c WHERE c.tripId = t.id) AS registeredTotalWon, " +
            "(SELECT COALESCE(SUM(c.amountWon), 0) FROM costs c WHERE c.tripId = t.id AND c.included = 1) AS includedTotalWon, " +
            "(SELECT p.totalWon FROM payment_settlements p WHERE p.tripId = t.id) AS paymentTotalWon, " +
            "(SELECT p.confirmedAtMillis FROM payment_settlements p WHERE p.tripId = t.id) AS paymentConfirmedAtMillis, " +
            "(SELECT s.totalCostWon FROM settlements s WHERE s.tripId = t.id) AS legacyTotalWon, " +
            "(SELECT s.confirmedAtMillis FROM settlements s WHERE s.tripId = t.id) AS legacyConfirmedAtMillis " +
            "FROM trips t ORDER BY t.startedAtMillis DESC, t.id DESC",
    )
    fun observeSummaries(): Flow<List<TripSummaryRow>>
}
