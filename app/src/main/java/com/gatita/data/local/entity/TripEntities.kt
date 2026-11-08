package com.gatita.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 거리·연비·금액은 BigDecimal을 정규화한 문자열로 저장해 부동소수점 손실을 피한다.
 *
 * [activeSlot]은 진행 중일 때만 1, 그 외에는 NULL이다. 유일 인덱스(NULL은 중복 허용)로 진행 중 여행을 하나로 제한한다.
 */
@Entity(
    tableName = "trips",
    indices = [Index(value = ["activeSlot"], unique = true)],
)
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val status: String,
    val source: String,
    val startOdometerKm: String,
    val startedAtMillis: Long,
    val endOdometerKm: String?,
    val endedAtMillis: Long?,
    val activeSlot: Int?,
    // ---- v2(결제액 방식) 컬럼. 기본값은 v1 행을 보존한 마이그레이션이 채우는 값과 같다. ----
    @ColumnInfo(defaultValue = "'새로운 여행'") val name: String = "새로운 여행",
    /** ISO yyyy-MM-dd. 기본값은 v1 행을 올릴 때 startedAtMillis(UTC)로 덮어쓰는 자리표시값이다. */
    @ColumnInfo(defaultValue = "'1970-01-01'") val tripDate: String = "1970-01-01",
    @ColumnInfo(defaultValue = "'UTC'") val zoneId: String = "UTC",
    @ColumnInfo(defaultValue = "''") val vehicleLabel: String = "",
    @ColumnInfo(defaultValue = "1") val people: Int = 1,
    @ColumnInfo(defaultValue = "1") val includeDriver: Boolean = true,
    @ColumnInfo(defaultValue = "0") val revision: Long = 0,
    @ColumnInfo(defaultValue = "0") val createdAtMillis: Long = 0,
    @ColumnInfo(defaultValue = "0") val updatedAtMillis: Long = 0,
)

/**
 * 연비 컬럼은 원본 값·단위·집계 범위·집계 키를 그대로 보존한다. [efficiencyValue]가 NULL이면 측정 실패 구간이다.
 * baseline 컬럼은 누적 평균일 때 구간 시작 시점 측정값이며 단위·범위·키는 끝 시점 값을 따른다.
 */
@Entity(
    tableName = "segments",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["tripId", "seq"], unique = true),
        Index(value = ["tripId", "eventId"], unique = true),
    ],
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val seq: Int,
    val eventId: String,
    val startOdometerKm: String,
    val endOdometerKm: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val efficiencyValue: String?,
    val efficiencyUnit: String?,
    val efficiencyScope: String?,
    val aggregationKey: String?,
    val cumulativeDistanceKm: String?,
    val baselineEfficiencyValue: String?,
    val baselineCumulativeDistanceKm: String?,
    val source: String,
)
