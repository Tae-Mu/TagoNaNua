package com.gatita.feature.common

import com.gatita.domain.model.DomainError
import com.gatita.domain.model.ErrorCode

/** 정산을 막는 불완전 데이터 사유. 명시적 기준 연비 없이는 합계를 만들 수 없다. */
val BLOCKING_DATA_CODES: Set<ErrorCode> = setOf(
    ErrorCode.NO_SEGMENTS,
    ErrorCode.SEGMENT_GAP,
    ErrorCode.MEASUREMENT_FAILED_SEGMENT,
    ErrorCode.UNKNOWN_AGGREGATION,
)

fun DomainError.isIncompleteData(): Boolean = code in BLOCKING_DATA_CODES

/** 도메인 오류 코드를 사용자 문구로 바꾼다. 개발자용 영문 메시지는 화면에 노출하지 않는다. */
fun ErrorCode.userMessage(): String = when (this) {
    ErrorCode.INVALID_ODOMETER -> "주행거리는 0 이상이어야 합니다."
    ErrorCode.ODOMETER_DECREASED -> "입력한 주행거리가 이전 기록보다 작습니다. 값을 다시 확인하세요."
    ErrorCode.INVALID_DISTANCE -> "이동 거리가 0보다 커야 합니다. 이전 기록과 같은 값인지 확인하세요."
    ErrorCode.INVALID_EFFICIENCY -> "연비는 0보다 커야 합니다."
    ErrorCode.INVALID_EFFICIENCY_SCOPE -> "연비 종류가 이 용도에 맞지 않습니다."
    ErrorCode.INVALID_FUEL_PRICE -> "리터당 유가를 확인하세요."
    ErrorCode.INVALID_HEADCOUNT -> "인원을 확인하세요."
    ErrorCode.INVALID_PARTICIPANTS -> "참여자 정보를 확인하세요."
    ErrorCode.INVALID_TIMESTAMP -> "시각이 이전 기록보다 앞섭니다. 기기 시계를 확인하세요."
    ErrorCode.INVALID_EVENT_ID, ErrorCode.INVALID_REQUEST_ID -> "요청 식별자가 올바르지 않습니다. 다시 시도하세요."
    ErrorCode.AMOUNT_OUT_OF_RANGE -> "계산된 금액이 너무 큽니다. 입력값을 확인하세요."
    ErrorCode.ACTIVE_TRIP_EXISTS -> "이미 진행 중인 여행이 있습니다."
    ErrorCode.TRIP_NOT_FOUND -> "여행 기록을 찾을 수 없습니다."
    ErrorCode.TRIP_NOT_IN_PROGRESS -> "이미 종료된 여행에는 구간을 추가할 수 없습니다."
    ErrorCode.TRIP_NOT_FINISHED -> "여행을 먼저 종료해야 정산할 수 있습니다."
    ErrorCode.SEGMENT_OVERLAP -> "이전 구간과 겹칩니다. 주행거리를 확인하세요."
    ErrorCode.SEGMENT_CONFLICT -> "같은 요청이 다른 내용으로 이미 저장되어 있습니다."
    ErrorCode.NO_SEGMENTS -> "저장된 구간이 없습니다."
    ErrorCode.SEGMENT_GAP -> "기록되지 않은 주행거리 구간이 있습니다."
    ErrorCode.MEASUREMENT_FAILED_SEGMENT -> "연비를 측정하지 못한 구간이 있습니다."
    ErrorCode.UNKNOWN_AGGREGATION -> "연비의 집계 기준을 확인할 수 없는 구간이 있습니다."
    ErrorCode.AGGREGATION_MISMATCH -> "연비 집계 기준이 서로 맞지 않습니다."
    ErrorCode.ALREADY_SETTLED -> "이미 확정된 정산이 있습니다."
    ErrorCode.REQUEST_ID_REUSED -> "직전 저장 시도가 이미 반영되었을 수 있습니다. 목록을 확인한 뒤 필요하면 다시 시도하세요."
    ErrorCode.INVALID_COST_AMOUNT -> "금액은 1원 이상 10,000,000원 이하의 정수여야 합니다."
    ErrorCode.INVALID_COST_DESCRIPTION -> "내용이 너무 길거나 사용할 수 없는 문자가 있습니다."
    ErrorCode.INVALID_PAID_DATE -> "날짜를 확인하세요."
    ErrorCode.INVALID_TRIP_NAME -> "이름이 너무 길거나 사용할 수 없는 문자가 있습니다."
    ErrorCode.COST_NOT_FOUND -> "비용 내역을 찾을 수 없습니다. 목록을 새로 불러오세요."
    ErrorCode.TRIP_LOCKED -> "정산이 확정된 여행은 수정할 수 없습니다."
    ErrorCode.STALE_REVISION -> "다른 곳에서 내용이 바뀌었습니다. 최신 내용을 확인한 뒤 다시 시도하세요."
    ErrorCode.NO_INCLUDED_COSTS -> "정산에 포함한 비용이 없습니다. 비용을 선택하세요."
    ErrorCode.EMPTY_TRIP -> "주행거리도 비용도 없는 여행은 마칠 수 없습니다. 비용을 추가하거나 구간을 기록하세요."
    ErrorCode.TRIP_NOT_EMPTY -> "구간이나 비용이 기록된 여행은 빈 여행으로 닫을 수 없습니다. 기록은 그대로 남아 있습니다."
    ErrorCode.SETTLEMENT_NOT_FOUND -> "확정된 정산을 찾을 수 없습니다."
    ErrorCode.REFUEL_COST_MISMATCH -> "감지한 주유 횟수와 주유 비용 건수가 달라 여행을 마칠 수 없어요. 비용 목록에서 두 수를 맞춰 주세요."
}

const val UNEXPECTED_FAILURE_MESSAGE = "처리 중 오류가 발생했습니다. 입력한 내용은 그대로 남아 있습니다. 잠시 후 다시 시도하세요."
const val LOAD_FAILURE_MESSAGE = "저장된 기록을 불러오지 못했습니다."
