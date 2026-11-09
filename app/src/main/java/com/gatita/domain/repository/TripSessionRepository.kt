package com.gatita.domain.repository

import kotlinx.coroutines.flow.Flow

/** A paused trip remains the single active trip; no fabricated vehicle state is stored. */
interface TripSessionRepository {
    fun observePausedAt(tripId: Long): Flow<Long?>
    suspend fun hideFromLists(tripId:Long)
    suspend fun setPausedAt(tripId: Long, pausedAt: Long?)
}
