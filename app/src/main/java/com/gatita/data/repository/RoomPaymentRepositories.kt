package com.gatita.data.repository

import androidx.room.withTransaction
import com.gatita.data.local.AppDatabase
import com.gatita.data.local.entity.PaymentSettlementEntity
import com.gatita.data.mapper.toDomain
import com.gatita.data.mapper.toEntity
import com.gatita.domain.model.Cost
import com.gatita.domain.model.PaymentSettlement
import com.gatita.domain.model.TripSummary
import com.gatita.domain.model.TripWithCosts
import com.gatita.domain.repository.CostRepository
import com.gatita.domain.repository.PaymentSettlementRepository
import com.gatita.domain.repository.TripLedgerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCostRepository(private val db: AppDatabase) : CostRepository {
    private val dao = db.costDao()
    private val tripDao = db.tripDao()

    override suspend fun insert(cost: Cost): Cost = db.withTransaction {
        val id = dao.insert(cost.toEntity())
        bump(cost.tripId, cost.updatedAtMillis)
        cost.copy(id = id)
    }

    override suspend fun update(cost: Cost): Boolean = db.withTransaction {
        val entity = cost.toEntity()
        val changed = dao.updateContent(
            id = entity.id,
            kind = entity.kind,
            amountWon = entity.amountWon,
            description = entity.description,
            unitPrice = entity.unitPrice,
            paidOn = entity.paidOn,
            updatedAtMillis = entity.updatedAtMillis,
        )
        if (changed == 1) bump(cost.tripId, cost.updatedAtMillis)
        changed == 1
    }

    override suspend fun setIncluded(costId: Long, included: Boolean, updatedAtMillis: Long): Boolean =
        db.withTransaction {
            val tripId = dao.get(costId)?.tripId ?: return@withTransaction false
            val changed = dao.setIncluded(costId, included, updatedAtMillis)
            if (changed == 1) bump(tripId, updatedAtMillis)
            changed == 1
        }

    override suspend fun delete(costId: Long, updatedAtMillis: Long): Boolean = db.withTransaction {
        val tripId = dao.get(costId)?.tripId ?: return@withTransaction false
        val changed = dao.delete(costId)
        if (changed == 1) bump(tripId, updatedAtMillis)
        changed == 1
    }

    override suspend fun get(costId: Long): Cost? = dao.get(costId)?.toDomain()

    override suspend fun findByRequestId(requestId: String): Cost? = dao.findByRequestId(requestId)?.toDomain()

    override suspend fun getCosts(tripId: Long): List<Cost> = dao.getCosts(tripId).map { it.toDomain() }

    private suspend fun bump(tripId: Long, updatedAtMillis: Long) {
        check(tripDao.bumpRevision(tripId, updatedAtMillis) == 1) { "Trip $tripId not found." }
    }
}

class RoomTripLedgerRepository(private val db: AppDatabase) : TripLedgerRepository {
    private val costDao = db.costDao()
    private val tripDao = db.tripDao()

    override fun observeTripWithCosts(tripId: Long): Flow<TripWithCosts?> =
        costDao.observeTripWithCosts(tripId).map { it?.toDomain() }

    override suspend fun updateSplitPolicy(
        tripId: Long,
        people: Int,
        includeDriver: Boolean,
        expectedRevision: Long,
        updatedAtMillis: Long,
    ): Boolean = tripDao.updateSplitPolicy(tripId, people, includeDriver, expectedRevision, updatedAtMillis) == 1

    override fun observeSummaries(): Flow<List<TripSummary>> =
        costDao.observeSummaries().map { rows -> rows.map { it.toDomain() } }
}

class RoomPaymentSettlementRepository(private val db: AppDatabase) : PaymentSettlementRepository {
    private val dao = db.paymentSettlementDao()
    private val tripDao = db.tripDao()

    override suspend fun findByRequestId(requestId: String): PaymentSettlement? =
        dao.findByRequestId(requestId)?.let { load(it) }

    override suspend fun findByTripId(tripId: Long): PaymentSettlement? =
        dao.findByTripId(tripId)?.let { load(it) }

    override suspend fun saveAndMarkTripSettled(
        settlement: PaymentSettlement,
        expectedTripRevision: Long,
    ): PaymentSettlement = db.withTransaction {
        // 상태·revision이 어긋나면 예외로 이후 삽입 전체를 되돌린다(여행당·요청 ID당 하나의 유일 제약도 같은 효과).
        check(tripDao.markSettledIfRevision(settlement.tripId, expectedTripRevision, settlement.confirmedAtMillis) == 1) {
            "Trip ${settlement.tripId} is not FINISHED at revision $expectedTripRevision."
        }
        val settlementId = dao.insertSettlement(settlement.toEntity())
        dao.insertCosts(settlement.costs.map { it.toEntity(settlementId) })
        dao.insertShares(settlement.shares.map { it.toEntity(settlementId) })

        check(dao.sumShares(settlementId) == settlement.totalWon) { "Stored shares do not add up to the total." }
        check(dao.sumIncludedCosts(settlementId) == settlement.totalWon) {
            "Stored included costs do not add up to the total."
        }
        settlement.copy(id = settlementId)
    }

    private suspend fun load(entity: PaymentSettlementEntity): PaymentSettlement = entity.toDomain(
        costs = dao.getCosts(entity.id),
        shares = dao.getShares(entity.id),
    )
}
