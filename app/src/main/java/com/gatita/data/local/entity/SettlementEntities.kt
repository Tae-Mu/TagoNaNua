package com.gatita.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 정산 시점의 유가 스냅샷. 저장 후 변경하지 않는다. */
@Entity(tableName = "fuel_prices")
data class FuelPriceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fuelType: String,
    val pricePerLiterWon: String,
    val priceDate: String?,
    val source: String,
)

/** 확정 정산. 여행당 하나, 요청 ID당 하나로 제한해 중복 확정을 막는다. 저장 후 UPDATE/DELETE는 트리거로 거부한다. */
@Entity(
    tableName = "settlements",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = FuelPriceEntity::class,
            parentColumns = ["id"],
            childColumns = ["fuelPriceId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["tripId"], unique = true),
        Index(value = ["requestId"], unique = true),
        Index(value = ["fuelPriceId"]),
    ],
)
data class SettlementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val requestId: String,
    val method: String,
    /** 쉼표로 구분한 ErrorCode 이름. 구간 합산이면 빈 문자열. */
    val fallbackReasons: String,
    val fuelPriceId: Long,
    val headcount: Int,
    val totalFuelLiters: String,
    val rawTotalCostWon: String,
    val totalCostWon: Long,
    val precisionPolicy: String,
    val confirmedAtMillis: Long,
)

@Entity(
    tableName = "settlement_items",
    foreignKeys = [
        ForeignKey(
            entity = SettlementEntity::class,
            parentColumns = ["id"],
            childColumns = ["settlementId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["settlementId", "itemIndex"], unique = true)],
)
data class SettlementItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val settlementId: Long,
    val itemIndex: Int,
    /** NULL이면 전체 여행 fallback 추정 항목. */
    val segmentSeq: Int?,
    val distanceKm: String,
    val efficiencyValue: String,
    val efficiencyUnit: String,
    val efficiencyScope: String,
    val aggregationKey: String?,
    val cumulativeDistanceKm: String?,
    val baselineEfficiencyValue: String?,
    val baselineCumulativeDistanceKm: String?,
    val fuelLiters: String,
    val unitPriceWonPerLiter: String,
    val costWon: String,
    val source: String,
)

@Entity(
    tableName = "settlement_shares",
    foreignKeys = [
        ForeignKey(
            entity = SettlementEntity::class,
            parentColumns = ["id"],
            childColumns = ["settlementId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["settlementId", "participantIndex"], unique = true)],
)
data class SettlementShareEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val settlementId: Long,
    val participantIndex: Int,
    val participantName: String?,
    val amountWon: Long,
)
