package com.gatita.data.repository

import androidx.room.withTransaction
import com.gatita.data.local.AppDatabase
import com.gatita.data.mapper.toDomain
import com.gatita.data.mapper.toEntity
import com.gatita.domain.model.Decimals
import com.gatita.domain.model.FuelSnapshot
import com.gatita.domain.model.RefuelEvent
import com.gatita.domain.repository.FuelTrackingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

class RoomFuelTrackingRepository(private val db: AppDatabase) : FuelTrackingRepository {
    private val dao = db.fuelTrackingDao()
    private val tripDao = db.tripDao()
    private val costDao = db.costDao()

    override suspend fun insertSnapshot(snapshot: FuelSnapshot): FuelSnapshot =
        snapshot.copy(id = dao.insertSnapshot(snapshot.toEntity()))

    override suspend fun getSnapshots(tripId: Long): List<FuelSnapshot> =
        dao.getSnapshots(tripId).map { it.toDomain() }

    override fun observeSnapshots(tripId: Long): Flow<List<FuelSnapshot>> =
        dao.observeSnapshots(tripId).map { list -> list.map { it.toDomain() } }

    override suspend fun insertRefuel(event: RefuelEvent): RefuelEvent =
        event.copy(id = dao.insertRefuel(event.toEntity()))

    override suspend fun getRefuels(tripId: Long): List<RefuelEvent> =
        dao.getRefuels(tripId).map { it.toDomain() }

    override fun observeRefuels(tripId: Long): Flow<List<RefuelEvent>> =
        dao.observeRefuels(tripId).map { list -> list.map { it.toDomain() } }

    override suspend fun setRefuelDismissed(eventId: Long, dismissed: Boolean, updatedAtMillis: Long): Boolean =
        db.withTransaction {
            val tripId = dao.getRefuel(eventId)?.tripId ?: return@withTransaction false
            if (dao.setRefuelDismissed(eventId, dismissed) != 1) return@withTransaction false
            check(tripDao.bumpRevision(tripId, updatedAtMillis) == 1) { "Trip $tripId not found." }
            true
        }

    override suspend fun setCostUndetected(costId: Long, undetected: Boolean, updatedAtMillis: Long): Boolean =
        db.withTransaction {
            val tripId = costDao.get(costId)?.tripId ?: return@withTransaction false
            if (dao.setCostUndetected(costId, undetected, updatedAtMillis) != 1) return@withTransaction false
            check(tripDao.bumpRevision(tripId, updatedAtMillis) == 1) { "Trip $tripId not found." }
            true
        }

    override suspend fun setManualFuelUnitPrice(tripId: Long, price: BigDecimal?, updatedAtMillis: Long): Boolean =
        db.withTransaction {
            val text = price?.let { Decimals.canonical(it).toPlainString() }
            if (dao.setManualFuelUnitPrice(tripId, text) != 1) return@withTransaction false
            check(tripDao.bumpRevision(tripId, updatedAtMillis) == 1) { "Trip $tripId not found." }
            true
        }
}
