package com.gatita.domain.model

/** 화면이 문구로 매핑할 수 있는 실패 분류. 메시지는 개발자용 영문 설명이다. */
enum class ErrorCode {
    INVALID_ODOMETER,
    ODOMETER_DECREASED,
    INVALID_DISTANCE,
    INVALID_EFFICIENCY,
    INVALID_EFFICIENCY_SCOPE,
    INVALID_FUEL_PRICE,
    INVALID_HEADCOUNT,
    INVALID_PARTICIPANTS,
    INVALID_TIMESTAMP,
    INVALID_EVENT_ID,
    INVALID_REQUEST_ID,
    AMOUNT_OUT_OF_RANGE,

    ACTIVE_TRIP_EXISTS,
    TRIP_NOT_FOUND,
    TRIP_NOT_IN_PROGRESS,
    TRIP_NOT_FINISHED,

    SEGMENT_OVERLAP,
    SEGMENT_CONFLICT,

    /** 아래 항목은 정산 확정을 막는 불완전 데이터다. 명시적 fallback 없이는 합계를 만들지 않는다. */
    NO_SEGMENTS,
    SEGMENT_GAP,
    MEASUREMENT_FAILED_SEGMENT,
    UNKNOWN_AGGREGATION,
    AGGREGATION_MISMATCH,

    ALREADY_SETTLED,
    REQUEST_ID_REUSED,

    // ---- 결제액 방식(payments-v1) ----
    INVALID_COST_AMOUNT,
    INVALID_COST_DESCRIPTION,
    INVALID_PAID_DATE,
    INVALID_TRIP_NAME,
    COST_NOT_FOUND,

    /** 확정된 여행은 비용·분담 정책을 바꿀 수 없다. */
    TRIP_LOCKED,

    /** 화면이 본 revision이 최신이 아니다. 자동으로 덮어쓰지 말고 다시 조회해 보여준다. */
    STALE_REVISION,
    NO_INCLUDED_COSTS,

    /** 거리도 비용도 없는 여행. 완료하지 않고 닫는 대상이다. */
    EMPTY_TRIP,

    /** 구간·비용·기록 거리가 있어 빈 여행으로 닫을 수 없다. */
    TRIP_NOT_EMPTY,
    SETTLEMENT_NOT_FOUND,

    // ---- 구간별 유류비 방식(fuel-segments-v2) ----
    /** 감지된 주유 이벤트 수와 짝짓기 대상 FUEL 비용 수가 다르다. 여행을 종료할 수 없다. */
    REFUEL_COST_MISMATCH,
}

/**
 * [issues]에는 정산을 막은 모든 사유가 들어간다. 단일 오류면 [code]만 담긴다.
 * [field]는 입력 검증 오류가 가리키는 입력 이름(amountWon, unitPrice, description, paidOn, people, name ...)이다.
 */
data class DomainError(
    val code: ErrorCode,
    val message: String,
    val issues: List<ErrorCode> = listOf(code),
    val field: String? = null,
)

sealed interface DomainResult<out T> {
    data class Success<out T>(val value: T) : DomainResult<T>
    data class Failure(val error: DomainError) : DomainResult<Nothing>
}

fun domainFailure(code: ErrorCode, message: String, field: String? = null): DomainResult.Failure =
    DomainResult.Failure(DomainError(code, message, field = field))

fun <T> DomainResult<T>.getOrNull(): T? = (this as? DomainResult.Success)?.value
