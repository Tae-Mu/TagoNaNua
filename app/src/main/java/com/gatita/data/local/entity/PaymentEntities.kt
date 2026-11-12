package com.gatita.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * 여행별 비용(주유·주차·톨비·기타). 주유량은 파생값이라 저장하지 않는다.
 * 종류별 null 규칙과 금액 범위는 [com.gatita.data.local.migration.ImmutabilityTriggers]의 트리거가 DB에서도 보강한다.
 */
@Entity(
    tableName = "costs",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["tripId", "createdAtMillis", "id"]),
        Index(value = ["tripId", "included"]),
        Index(value = ["requestId"], unique = true),
    ],
)
data class CostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val requestId: String,
    val kind: String,
    val amountWon: Long,
    val description: String?,
    /** FUEL만. 정규화한 십진 문자열(원/L). */
    val unitPrice: String?,
    /** FUEL만. ISO yyyy-MM-dd. */
    val paidOn: String?,
    @ColumnInfo(defaultValue = "1") val included: Boolean = true,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    /** FUEL만 의미가 있다. 앱이 감지하지 못한 주유의 비용이면 true(주유 이벤트와 짝짓지 않는다). */
    @ColumnInfo(defaultValue = "0") val undetected: Boolean = false,
)

/** 여행과 비용을 하나의 쿼리 시점으로 읽는 관계. 비용 순서는 보장되지 않으므로 호출자가 정렬한다. */
data class TripWithCostsRelation(
    @Embedded val trip: TripEntity,
    @Relation(parentColumn = "id", entityColumn = "tripId") val costs: List<CostEntity>,
)

/**
 * 결제액 방식으로 확정한 정산(payments-v1). v1 추정 정산 테이블 `settlements`와 별개이며 둘 중 하나만 여행에 존재한다.
 * 저장 후 UPDATE/DELETE는 트리거로 거부한다.
 */
@Entity(
    tableName = "payment_settlements",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["tripId"], unique = true),
        Index(value = ["requestId"], unique = true),
    ],
)
data class PaymentSettlementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val requestId: String,
    val tripName: String,
    val tripDate: String,
    val tripSource: String,
    /** 기록 메타데이터. 측정이 없으면 NULL이며 0으로 저장하지 않는다. */
    val distanceKm: String?,
    val recordedSeconds: Long?,
    val measurementQuality: String,
    val totalWon: Long,
    val fuelTotalWon: Long,
    val otherTotalWon: Long,
    val people: Int,
    val includeDriver: Boolean,
    val splitPeople: Int,
    val sourceTripRevision: Long,
    val calculationVersion: String,
    val confirmedAtMillis: Long,
)

/** 확정 당시 비용 복사본(제외된 비용 포함). [originalCostId]는 추적용 값이며 costs에 FK를 걸지 않는다. */
@Entity(
    tableName = "payment_settlement_costs",
    foreignKeys = [
        ForeignKey(
            entity = PaymentSettlementEntity::class,
            parentColumns = ["id"],
            childColumns = ["settlementId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["settlementId", "originalCostId"], unique = true),
        Index(value = ["settlementId", "ordinal"], unique = true),
    ],
)
data class PaymentSettlementCostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val settlementId: Long,
    val originalCostId: Long,
    val ordinal: Int,
    val kind: String,
    val amountWon: Long,
    val description: String?,
    val unitPrice: String?,
    val paidOn: String?,
    val included: Boolean,
)

/** 운전자(ordinal 0)는 제외해도 0원 행으로 보존한다. */
@Entity(
    tableName = "payment_settlement_shares",
    primaryKeys = ["settlementId", "ordinal"],
    foreignKeys = [
        ForeignKey(
            entity = PaymentSettlementEntity::class,
            parentColumns = ["id"],
            childColumns = ["settlementId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class PaymentSettlementShareEntity(
    val settlementId: Long,
    val ordinal: Int,
    val displayName: String,
    val isDriver: Boolean,
    val amountWon: Long,
)

/** 내역 목록용 조회 행. 집계는 쿼리가 하고 거리 계산은 매퍼가 한다. */
data class TripSummaryRow(
    val tripId: Long,
    val name: String,
    val tripDate: String,
    val status: String,
    val source: String,
    val people: Int,
    val includeDriver: Boolean,
    val revision: Long,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val startOdometerKm: String,
    val endOdometerKm: String?,
    val costCount: Int,
    val includedCount: Int,
    val registeredTotalWon: Long,
    val includedTotalWon: Long,
    val paymentTotalWon: Long?,
    val paymentConfirmedAtMillis: Long?,
    val legacyTotalWon: Long?,
    val legacyConfirmedAtMillis: Long?,
)
