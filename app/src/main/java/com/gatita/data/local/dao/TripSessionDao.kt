package com.gatita.data.local.dao

import androidx.room.*
import com.gatita.data.local.entity.TripSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao interface TripSessionDao {
    @Query("SELECT pausedAt FROM trip_sessions WHERE tripId = :tripId") fun observePausedAt(tripId:Long):Flow<Long?>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(value:TripSessionEntity)
    @Query("UPDATE trip_sessions SET pausedAt = :at WHERE tripId = :tripId") suspend fun setPaused(tripId:Long,at:Long?)
    @Query("UPDATE trip_sessions SET hidden = 1 WHERE tripId = :tripId") suspend fun hide(tripId:Long)
    @Query("SELECT tripId FROM trip_sessions WHERE hidden = 1") fun observeHidden():Flow<List<Long>>
    @Transaction suspend fun save(value:TripSessionEntity) { insert(value);setPaused(value.tripId,value.pausedAt) }
    @Transaction suspend fun hideTrip(tripId:Long) { insert(TripSessionEntity(tripId,null));hide(tripId) }
}
