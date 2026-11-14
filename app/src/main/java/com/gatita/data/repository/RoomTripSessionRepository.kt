package com.gatita.data.repository

import com.gatita.data.local.dao.TripSessionDao
import com.gatita.data.local.entity.TripSessionEntity
import com.gatita.domain.repository.TripSessionRepository

class RoomTripSessionRepository(private val dao:TripSessionDao):TripSessionRepository {
    override suspend fun hideFromLists(tripId:Long)=dao.hideTrip(tripId)
    override fun observePausedAt(tripId:Long)=dao.observePausedAt(tripId)
    override suspend fun setPausedAt(tripId:Long,pausedAt:Long?)=dao.save(TripSessionEntity(tripId,pausedAt))
}
