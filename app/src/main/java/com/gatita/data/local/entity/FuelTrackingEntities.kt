package com.gatita.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 구간별 유류비 방식의 차량 측정 저장본. 연비 컬럼이 모두 NULL이면 측정 실패이며 0으로 저장하지 않는다.
 * 여행을 지우면(빈 여행 삭제) 함께 지운다. 확정 정산은 이 테이블을 참조하지 않고 결과를 복사해 보존한다.
 */
@Entity(
    tableName = "fuel_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tripId", "takenAtMillis", "id"])],
)
data class FuelSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val takenAtMillis: Long,
    val kind: String,
    val odometerKm: String,
    val fuelPercent: Int?,
    val efficiencyValue: String?,
    val efficiencyUnit: String?,
    val efficiencyScope: String?,
    val aggregationKey: String?,
    val cumulativeDistanceKm: String?,
    val parked: Boolean?,
    val source: String,
)

@Entity(
    tableName = "refuel_events",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tripId", "detectedAtMillis", "id"])],
)
data class RefuelEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val detectedAtMillis: Long,
    val percentBefore: Int,
    val percentAfter: Int,
    @ColumnInfo(defaultValue = "0") val dismissed: Boolean = false,
)

/** 확정 당시 구간별 계산 결과 복사본. 저장 후 UPDATE/DELETE는 트리거로 거부한다. */
@Entity(
    tableName = "payment_settlement_fuel_intervals",
    foreignKeys = [
        ForeignKey(
            entity = PaymentSettlementEntity::class,
            parentColumns = ["id"],
            childColumns = ["settlementId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["settlementId", "ordinal"], unique = true)],
)
data class PaymentSettlementFuelIntervalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val settlementId: Long,
    val ordinal: Int,
    val distanceKm: String,
    val usage: String?,
    val unitPrice: String?,
    /** 반올림 전 구간 유류비. 제외된 구간은 NULL. */
    val costExact: String?,
    val exclusion: String?,
    val exclusionDetail: String?,
)
