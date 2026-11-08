package com.gatita.feature.common

import com.gatita.domain.model.CostKind
import com.gatita.domain.model.EfficiencyReading
import com.gatita.domain.model.MeasurementQuality
import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.EfficiencyScope
import com.gatita.domain.model.EfficiencyUnit
import com.gatita.domain.model.FuelType
import com.gatita.domain.model.VehicleSource
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 표시 전용 포맷. 여기서의 반올림은 화면 문자열에만 적용되며 계산·저장 값에는 영향을 주지 않는다.
 */
object Formatters {
    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.KOREA)

    fun won(amount: Long): String = String.format(Locale.KOREA, "%,d원", amount)

    /** 소수 [maxScale]자리까지 표시용으로 반올림하고 후행 0을 없앤다. */
    fun decimal(value: BigDecimal, maxScale: Int): String {
        val rounded = value.setScale(maxScale, RoundingMode.HALF_UP)
        if (rounded.signum() == 0) return "0"
        return rounded.stripTrailingZeros().toPlainString()
    }

    fun km(value: BigDecimal): String = decimal(value, 1) + " km"

    fun liters(value: BigDecimal): String = decimal(value, 2) + " L"

    fun wonDecimal(value: BigDecimal): String = decimal(value, 2) + "원"

    fun efficiency(reading: EfficiencyReading): String =
        decimal(reading.value, 2) + " " + unitLabel(reading.unit)

    fun unitLabel(unit: EfficiencyUnit): String = when (unit) {
        EfficiencyUnit.KM_PER_LITER -> "km/L"
        EfficiencyUnit.LITERS_PER_100_KM -> "L/100km"
    }

    fun scopeLabel(scope: EfficiencyScope): String = when (scope) {
        EfficiencyScope.SEGMENT_AVERAGE -> "구간 평균"
        EfficiencyScope.CUMULATIVE_AVERAGE -> "누적 평균"
        EfficiencyScope.VEHICLE_REFERENCE -> "차량 기준 연비(추정용)"
        EfficiencyScope.UNKNOWN -> "집계 기준 미확인"
    }

    fun fuelType(type: FuelType): String = when (type) {
        FuelType.GASOLINE -> "휘발유"
        FuelType.DIESEL -> "경유"
        FuelType.LPG -> "LPG"
    }

    /** 데이터 출처. 실제 차량 값과 직접 입력·샘플을 화면에서 구분하는 데 쓴다. */
    fun source(source: VehicleSource): String = when (source) {
        VehicleSource.MANUAL -> "수동 입력"
        VehicleSource.SAMPLE -> "샘플 데이터(실제 차량 값 아님)"
        VehicleSource.PLEOS -> "차량에서 읽은 값"
    }

    fun costKind(kind: CostKind): String = when (kind) {
        CostKind.FUEL -> "주유"
        CostKind.PARKING -> "주차"
        CostKind.TOLL -> "톨비"
        CostKind.OTHER -> "기타"
    }

    fun tripStatus(status: TripStatus): String = when (status) {
        TripStatus.IN_PROGRESS -> "기록 중"
        TripStatus.FINISHED -> "정산 대기"
        TripStatus.SETTLED -> "정산 확정"
    }

    fun quality(quality: MeasurementQuality): String = when (quality) {
        MeasurementQuality.VALID -> "거리·연비 기록 완전"
        MeasurementQuality.PARTIAL -> "일부 구간 누락 또는 연비 미측정"
        MeasurementQuality.MISSING -> "주행거리 기록 없음"
    }

    fun dateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTimeFormat.format(Instant.ofEpochMilli(millis).atZone(zone))
}
