package com.gatita.data.repository

import androidx.room.withTransaction
import com.gatita.data.local.AppDatabase
import com.gatita.data.local.entity.SettlementEntity
import com.gatita.data.mapper.toDomain
import com.gatita.data.mapper.toEntity
import com.gatita.domain.model.Settlement
import com.gatita.domain.repository.SettlementRepository

class RoomSettlementRepository(private val db: AppDatabase) : SettlementRepository {
    private val settlementDao = db.settlementDao()
    private val tripDao = db.tripDao()

    override suspend fun findByRequestId(requestId: String): Settlement? =
        settlementDao.findByRequestId(requestId)?.let { load(it) }

    override suspend fun findByTripId(tripId: Long): Settlement? =
        settlementDao.findByTripId(tripId)?.let { load(it) }

    override suspend fun saveAndMarkTripSettled(settlement: Settlement): Settlement = db.withTransaction {
        val calculation = settlement.calculation
        val priceId = settlementDao.insertFuelPrice(calculation.fuelPrice.toEntity())
        val settlementId = settlementDao.insertSettlement(settlement.toEntity(priceId))
        settlementDao.insertItems(calculation.items.map { it.toEntity(settlementId) })
        settlementDao.insertShares(calculation.shares.map { it.toEntity(settlementId) })
        // FINISHED가 아니면 예외로 위의 모든 삽입을 되돌린다.
        check(tripDao.markSettled(settlement.tripId) == 1) {
            "Trip ${settlement.tripId} is not in FINISHED state."
        }
        settlement.copy(id = settlementId)
    }

    private suspend fun load(entity: SettlementEntity): Settlement = entity.toDomain(
        price = settlementDao.getFuelPrice(entity.fuelPriceId),
        items = settlementDao.getItems(entity.id),
        shares = settlementDao.getShares(entity.id),
    )
}
