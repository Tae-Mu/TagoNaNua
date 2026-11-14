package com.gatita

import com.gatita.domain.model.Segment
import com.gatita.domain.model.Settlement
import com.gatita.domain.model.Trip
import com.gatita.domain.model.TripStatus
import com.gatita.domain.repository.SettlementRepository
import com.gatita.domain.repository.TransactionRunner
import com.gatita.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal

/** Room 없이 유스케이스 규칙을 검증하기 위한 인메모리 저장소. DB 제약 동작은 androidTest의 Room 테스트가 검증한다. */
class FakeStore {
    val trips = linkedMapOf<Long, Trip>()
    val segments = mutableListOf<Segment>()
    val settlements = mutableListOf<Settlement>()
    var nextId = 1L
}

class FakeTripRepository(private val store: FakeStore) : TripRepository {
    override suspend fun insertTrip(trip: Trip): Trip {
        check(trip.status != TripStatus.IN_PROGRESS || store.trips.values.none { it.status == TripStatus.IN_PROGRESS }) {
            "unique active trip violated"
        }
        return trip.copy(id = store.nextId++).also { store.trips[it.id] = it }
    }

    override suspend fun getTrip(tripId: Long): Trip? = store.trips[tripId]

    override suspend fun getActiveTrip(): Trip? = store.trips.values.firstOrNull { it.status == TripStatus.IN_PROGRESS }

    override fun observeActiveTrip(): Flow<Trip?> = flow { emit(getActiveTrip()) }

    override fun observeTrips(): Flow<List<Trip>> = flow { emit(store.trips.values.toList()) }

    override suspend fun markFinished(tripId: Long, endOdometerKm: BigDecimal, endedAtMillis: Long): Trip {
        val trip = checkNotNull(store.trips[tripId])
        check(trip.status == TripStatus.IN_PROGRESS) { "not in progress" }
        return trip.copy(status = TripStatus.FINISHED, endOdometerKm = endOdometerKm, endedAtMillis = endedAtMillis)
            .also { store.trips[tripId] = it }
    }

    override suspend fun getSegments(tripId: Long): List<Segment> =
        store.segments.filter { it.tripId == tripId }.sortedBy { it.seq }

    override suspend fun findSegmentByEventId(tripId: Long, eventId: String): Segment? =
        store.segments.firstOrNull { it.tripId == tripId && it.eventId == eventId }

    override suspend fun appendSegment(segment: Segment): Segment {
        check(store.segments.none { it.tripId == segment.tripId && (it.seq == segment.seq || it.eventId == segment.eventId) }) {
            "unique segment violated"
        }
        return segment.copy(id = store.nextId++).also { store.segments += it }
    }
}

class FakeSettlementRepository(private val store: FakeStore) : SettlementRepository {
    override suspend fun findByRequestId(requestId: String): Settlement? =
        store.settlements.firstOrNull { it.requestId == requestId }

    override suspend fun findByTripId(tripId: Long): Settlement? =
        store.settlements.firstOrNull { it.tripId == tripId }

    override suspend fun saveAndMarkTripSettled(settlement: Settlement): Settlement {
        check(store.settlements.none { it.tripId == settlement.tripId || it.requestId == settlement.requestId }) {
            "unique settlement violated"
        }
        val trip = checkNotNull(store.trips[settlement.tripId])
        check(trip.status == TripStatus.FINISHED) { "not finished" }
        val saved = settlement.copy(id = store.nextId++)
        store.settlements += saved
        store.trips[trip.id] = trip.copy(status = TripStatus.SETTLED)
        return saved
    }
}

class SerialTransactionRunner : TransactionRunner {
    private val mutex = Mutex()
    override suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
}
