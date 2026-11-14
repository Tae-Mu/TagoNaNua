package com.gatita.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gatita.data.local.entity.FuelSnapshotEntity
import com.gatita.data.local.entity.RefuelEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelTrackingDao {
    @Insert
    suspend fun insertSnapshot(snapshot: FuelSnapshotEntity): Long

    @Query("SELECT * FROM fuel_snapshots WHERE tripId = :tripId ORDER BY takenAtMillis ASC, id ASC")
    suspend fun getSnapshots(tripId: Long): List<FuelSnapshotEntity>

    @Query("SELECT * FROM fuel_snapshots WHERE tripId = :tripId ORDER BY takenAtMillis ASC, id ASC")
    fun observeSnapshots(tripId: Long): Flow<List<FuelSnapshotEntity>>

    @Insert
    suspend fun insertRefuel(event: RefuelEventEntity): Long

    @Query("SELECT * FROM refuel_events WHERE tripId = :tripId ORDER BY detectedAtMillis ASC, id ASC")
    suspend fun getRefuels(tripId: Long): List<RefuelEventEntity>

    @Query("SELECT * FROM refuel_events WHERE tripId = :tripId ORDER BY detectedAtMillis ASC, id ASC")
    fun observeRefuels(tripId: Long): Flow<List<RefuelEventEntity>>

    @Query("SELECT * FROM refuel_events WHERE id = :id")
    suspend fun getRefuel(id: Long): RefuelEventEntity?

    @Query("UPDATE refuel_events SET dismissed = :dismissed WHERE id = :id")
    suspend fun setRefuelDismissed(id: Long, dismissed: Boolean): Int

    @Query("UPDATE costs SET undetected = :undetected, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun setCostUndetected(id: Long, undetected: Boolean, updatedAtMillis: Long): Int

    @Query("UPDATE trips SET manualFuelUnitPrice = :price WHERE id = :id")
    suspend fun setManualFuelUnitPrice(id: Long, price: String?): Int
}
