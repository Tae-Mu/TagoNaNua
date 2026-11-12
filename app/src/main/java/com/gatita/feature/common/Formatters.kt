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

    // [목적] 소수 금액(리터당 가격 등)을 천 단위 쉼표와 함께 표시 (예: 1,700원)
    // [이유] 다른 금액은 모두 쉼표가 있는데 리터당 가격만 '1700원'으로 나와 화면 안에서 숫자 표기가 어긋났음
    // [사이드 임팩트] wonDecimal을 쓰는 정산 근거 줄·결과 화면의 표기가 쉼표 포함으로 바뀜 (값은 그대로)
    // [수정 시 고려사항] 표시 전용. 계산·저장에는 쓰지 말 것
    fun wonDecimal(value: BigDecimal): String =
        java.text.DecimalFormat("#,##0.##").format(value.setScale(2, RoundingMode.HALF_UP)) + "원"

    // [목적] 화면에 보여주는 날짜·시각을 읽기 쉬운 말로 표시 (예: 11월 12일, 11월 12일 오전 4:12)
    // [이유] 여행 기록·정산 결과 화면만 '2026-11-12 04:12' 같은 기계식 표기라 목록의 '11월 12일 · 오전 4:10'과 어긋났음
    // [사이드 임팩트] 없음 (표시 전용. 공유 요약 글은 기존 dateTime을 그대로 씀)
    // [수정 시 고려사항] 연도를 뺐으므로 해가 바뀐 기록을 구분해야 하는 화면에는 쓰지 말 것
    fun shortDate(date: java.time.LocalDate): String = "${date.monthValue}월 ${date.dayOfMonth}일"

    fun friendlyDateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("M월 d일 a h:mm", Locale.KOREA))

    fun efficiency(reading: EfficiencyReading): String =
        decimal(reading.value, 2) + " " + unitLabel(reading.unit)

    fun unitLabel(unit: EfficiencyUnit): String = when (unit) {
        EfficiencyUnit.KM_PER_LITER -> "km/L"
        EfficiencyUnit.LITERS_PER_100_KM -> "L/100km"
        EfficiencyUnit.KM_PER_KWH -> "km/kWh"
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
        CostKind.PARKING -> "주차비"
        CostKind.TOLL -> "톨비"
        CostKind.OTHER -> "기타 비용"
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

    // [목적] 여행 출발 시각을 목록용 짧은 문구로 표시 (예: 오전 5:40 출발)
    // [이유] 이름을 비운 여행은 모두 '새로운 여행'이라 같은 날 여러 건이면 목록에서 구분되지 않아, 출발 시각을 부제에 넣음
    // [사이드 임팩트] 없음 (표시 전용)
    // [수정 시 고려사항] 기기 시간대 기준. startedAtMillis는 여행 시작 시 한 번 기록되는 값
    fun startTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("a h:mm", Locale.KOREA)) + " 출발"

    fun dateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTimeFormat.format(Instant.ofEpochMilli(millis).atZone(zone))
}
