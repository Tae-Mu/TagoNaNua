package com.gatita.data.repository

import androidx.room.withTransaction
import com.gatita.data.local.AppDatabase
import com.gatita.data.mapper.toDomain
import com.gatita.data.mapper.toEntity
import com.gatita.domain.model.Decimals
import com.gatita.domain.model.Segment
import com.gatita.domain.model.Trip
import com.gatita.domain.repository.EmptyTripRepository
import com.gatita.domain.repository.TransactionRunner
import com.gatita.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

class RoomTransactionRunner(private val db: AppDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}

class RoomTripRepository(private val db: AppDatabase) : TripRepository, EmptyTripRepository {
    private val dao = db.tripDao()

    override suspend fun insertTrip(trip: Trip): Trip {
        val id = dao.insert(trip.toEntity())
        return trip.copy(id = id)
    }

    override suspend fun getTrip(tripId: Long): Trip? = dao.get(tripId)?.toDomain()

    override suspend fun getActiveTrip(): Trip? = dao.getActive()?.toDomain()

    override fun observeActiveTrip(): Flow<Trip?> = dao.observeActive().map { it?.toDomain() }

    override fun observeTrips(): Flow<List<Trip>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun markFinished(tripId: Long, endOdometerKm: BigDecimal, endedAtMillis: Long): Trip =
        db.withTransaction {
            val endText = Decimals.canonical(endOdometerKm).toPlainString()
            val updated = dao.markFinished(tripId, endText, endedAtMillis)
            check(updated == 1) { "Trip $tripId is not in progress." }
            checkNotNull(dao.get(tripId)).toDomain()
        }

    override suspend fun getSegments(tripId: Long): List<Segment> = dao.getSegments(tripId).map { it.toDomain() }

    override suspend fun findSegmentByEventId(tripId: Long, eventId: String): Segment? =
        dao.findSegmentByEventId(tripId, eventId)?.toDomain()

    /** 구간 변경도 여행 revision을 올려 오래된 화면의 확정·수정을 막는다. */
    override suspend fun appendSegment(segment: Segment): Segment = db.withTransaction {
        val id = dao.insertSegment(segment.toEntity())
        dao.bumpRevision(segment.tripId, segment.endedAtMillis)
        segment.copy(id = id)
    }

    override suspend fun deleteIfEmptyActive(tripId: Long): Boolean = dao.deleteIfEmptyActive(tripId) == 1
}
