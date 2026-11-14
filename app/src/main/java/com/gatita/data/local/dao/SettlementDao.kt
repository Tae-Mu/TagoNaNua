package com.gatita.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gatita.data.local.entity.FuelPriceEntity
import com.gatita.data.local.entity.SettlementEntity
import com.gatita.data.local.entity.SettlementItemEntity
import com.gatita.data.local.entity.SettlementShareEntity

/** 정산 테이블에는 INSERT와 조회만 둔다. 수정·삭제 API는 만들지 않는다. */
@Dao
interface SettlementDao {
    @Insert
    suspend fun insertFuelPrice(price: FuelPriceEntity): Long

    @Insert
    suspend fun insertSettlement(settlement: SettlementEntity): Long

    @Insert
    suspend fun insertItems(items: List<SettlementItemEntity>)

    @Insert
    suspend fun insertShares(shares: List<SettlementShareEntity>)

    @Query("SELECT * FROM settlements WHERE requestId = :requestId LIMIT 1")
    suspend fun findByRequestId(requestId: String): SettlementEntity?

    @Query("SELECT * FROM settlements WHERE tripId = :tripId LIMIT 1")
    suspend fun findByTripId(tripId: Long): SettlementEntity?

    @Query("SELECT * FROM fuel_prices WHERE id = :id")
    suspend fun getFuelPrice(id: Long): FuelPriceEntity

    @Query("SELECT * FROM settlement_items WHERE settlementId = :settlementId ORDER BY itemIndex ASC")
    suspend fun getItems(settlementId: Long): List<SettlementItemEntity>

    @Query("SELECT * FROM settlement_shares WHERE settlementId = :settlementId ORDER BY participantIndex ASC")
    suspend fun getShares(settlementId: Long): List<SettlementShareEntity>
}
