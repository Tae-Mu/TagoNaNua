package com.gatita.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gatita.data.local.entity.PaymentSettlementCostEntity
import com.gatita.data.local.entity.PaymentSettlementEntity
import com.gatita.data.local.entity.PaymentSettlementFuelIntervalEntity
import com.gatita.data.local.entity.PaymentSettlementShareEntity

/** 결제액 정산 테이블에는 INSERT와 조회만 둔다. 수정·삭제 API는 만들지 않는다. */
@Dao
interface PaymentSettlementDao {
    @Insert
    suspend fun insertSettlement(settlement: PaymentSettlementEntity): Long

    @Insert
    suspend fun insertCosts(costs: List<PaymentSettlementCostEntity>)

    @Insert
    suspend fun insertShares(shares: List<PaymentSettlementShareEntity>)

    @Insert
    suspend fun insertFuelIntervals(intervals: List<PaymentSettlementFuelIntervalEntity>)

    @Query("SELECT * FROM payment_settlement_fuel_intervals WHERE settlementId = :settlementId ORDER BY ordinal ASC")
    suspend fun getFuelIntervals(settlementId: Long): List<PaymentSettlementFuelIntervalEntity>

    /** 구간별 유류비 방식에서 유류비가 아닌 포함 비용의 합. 유류비는 비용 입력액이 아니라 구간 계산값이다. */
    @Query(
        "SELECT COALESCE(SUM(amountWon), 0) FROM payment_settlement_costs " +
            "WHERE settlementId = :settlementId AND included = 1 AND kind <> 'FUEL'",
    )
    suspend fun sumIncludedNonFuelCosts(settlementId: Long): Long

    @Query("SELECT * FROM payment_settlements WHERE requestId = :requestId LIMIT 1")
    suspend fun findByRequestId(requestId: String): PaymentSettlementEntity?

    @Query("SELECT * FROM payment_settlements WHERE tripId = :tripId LIMIT 1")
    suspend fun findByTripId(tripId: Long): PaymentSettlementEntity?

    @Query("SELECT * FROM payment_settlement_costs WHERE settlementId = :settlementId ORDER BY ordinal ASC")
    suspend fun getCosts(settlementId: Long): List<PaymentSettlementCostEntity>

    @Query("SELECT * FROM payment_settlement_shares WHERE settlementId = :settlementId ORDER BY ordinal ASC")
    suspend fun getShares(settlementId: Long): List<PaymentSettlementShareEntity>

    @Query("SELECT COALESCE(SUM(amountWon), 0) FROM payment_settlement_shares WHERE settlementId = :settlementId")
    suspend fun sumShares(settlementId: Long): Long

    @Query("SELECT COALESCE(SUM(amountWon), 0) FROM payment_settlement_costs WHERE settlementId = :settlementId AND included = 1")
    suspend fun sumIncludedCosts(settlementId: Long): Long
}
