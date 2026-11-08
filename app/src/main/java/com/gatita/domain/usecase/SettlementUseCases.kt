package com.gatita.domain.usecase

import com.gatita.domain.calculator.SettlementCalculator
import com.gatita.domain.model.CalculationMethod
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.Settlement
import com.gatita.domain.model.SettlementCalculation
import com.gatita.domain.model.SettlementInput
import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.UNSAVED_ID
import com.gatita.domain.model.domainFailure
import com.gatita.domain.repository.SettlementRepository
import com.gatita.domain.repository.TransactionRunner
import com.gatita.domain.repository.TripRepository
import java.time.Clock

/** 저장 없이 계산 근거를 보여주는 미리보기. 확정 전에 화면에서 항목·분담액을 확인하는 데 쓴다. */
class PreviewSettlementUseCase(
    private val trips: TripRepository,
    private val calculator: SettlementCalculator,
) {
    suspend operator fun invoke(tripId: Long, input: SettlementInput): DomainResult<SettlementCalculation> {
        val trip = trips.getTrip(tripId)
            ?: return domainFailure(ErrorCode.TRIP_NOT_FOUND, "Trip $tripId not found.")
        return calculator.calculate(trip, trips.getSegments(tripId), normalize(input))
    }
}

/**
 * [requestId]는 확정 버튼 한 번의 시도를 식별하는 호출자 생성 ID다. 화면은 같은 시도를 재전송할 때 같은 ID를 써야 한다.
 */
data class ConfirmSettlementCommand(
    val tripId: Long,
    val requestId: String,
    val input: SettlementInput,
)

/**
 * 정산 확정. 계산·스냅샷 저장·여행 상태 변경을 하나의 트랜잭션으로 수행한다.
 *
 * - 같은 요청 ID와 같은 입력으로 다시 호출하면 기존 정산을 그대로 돌려준다(새 기록 없음).
 * - 같은 요청 ID에 다른 입력이면 [ErrorCode.REQUEST_ID_REUSED], 이미 확정된 여행에 다른 요청 ID면 [ErrorCode.ALREADY_SETTLED].
 */
class ConfirmSettlementUseCase(
    private val trips: TripRepository,
    private val settlements: SettlementRepository,
    private val transactions: TransactionRunner,
    private val calculator: SettlementCalculator,
    private val clock: Clock,
) {
    suspend operator fun invoke(command: ConfirmSettlementCommand): DomainResult<Settlement> {
        val requestId = command.requestId.trim()
        if (requestId.isEmpty() || requestId.length > MAX_ID_LENGTH) {
            return domainFailure(ErrorCode.INVALID_REQUEST_ID, "Request id must be 1..$MAX_ID_LENGTH characters.")
        }
        return transactions.run { confirm(command.tripId, requestId, normalize(command.input)) }
    }

    private suspend fun confirm(tripId: Long, requestId: String, input: SettlementInput): DomainResult<Settlement> {
        settlements.findByRequestId(requestId)?.let { existing ->
            return if (existing.tripId == tripId && matches(existing.calculation, input)) {
                DomainResult.Success(existing)
            } else {
                domainFailure(ErrorCode.REQUEST_ID_REUSED, "Request id was already used for a different settlement.")
            }
        }

        val trip = trips.getTrip(tripId)
            ?: return domainFailure(ErrorCode.TRIP_NOT_FOUND, "Trip $tripId not found.")
        when (trip.status) {
            TripStatus.SETTLED ->
                return domainFailure(ErrorCode.ALREADY_SETTLED, "Trip already has a confirmed settlement.")

            TripStatus.IN_PROGRESS ->
                return domainFailure(ErrorCode.TRIP_NOT_FINISHED, "Trip must be finished before settlement.")

            TripStatus.FINISHED -> Unit
        }

        val calculation = when (val result = calculator.calculate(trip, trips.getSegments(tripId), input)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        val saved = settlements.saveAndMarkTripSettled(
            Settlement(
                id = UNSAVED_ID,
                tripId = tripId,
                requestId = requestId,
                confirmedAtMillis = clock.millis(),
                calculation = calculation,
            ),
        )
        return DomainResult.Success(saved)
    }

    private fun matches(existing: SettlementCalculation, input: SettlementInput): Boolean =
        existing.fuelPrice == input.fuelPrice &&
            existing.headcount == input.headcount &&
            existing.shares.map { it.participantName } == (input.participantNames ?: List(input.headcount) { null }) &&
            fallbackMatches(existing, input)

    /**
     * 대체 연비는 REFERENCE_ESTIMATE로 확정된 경우에만 실제 적용되므로, 그때만 요청값과 보존된 적용 연비(불변 계산 항목)를 비교한다.
     * 구간 합산으로 확정된 정산에서는 대체 연비가 쓰이지 않아 비교 대상이 아니다.
     */
    private fun fallbackMatches(existing: SettlementCalculation, input: SettlementInput): Boolean {
        if (existing.method != CalculationMethod.REFERENCE_ESTIMATE) return true
        return existing.items.singleOrNull()?.efficiency == input.fallbackEfficiency
    }
}

private fun normalize(input: SettlementInput): SettlementInput = input.copy(
    fuelPrice = input.fuelPrice.copy(pricePerLiterWon = input.fuelPrice.pricePerLiterWon.canonical()),
    participantNames = input.participantNames?.map { it.trim() },
    fallbackEfficiency = input.fallbackEfficiency?.canonical(),
)
