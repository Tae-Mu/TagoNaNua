package com.gatita.domain.usecase

import com.gatita.domain.calculator.CostValidator
import com.gatita.domain.calculator.FuelSettlementPlanner
import com.gatita.domain.calculator.PaymentSettlementCalculator
import com.gatita.domain.calculator.PaymentSummaryFormatter
import com.gatita.domain.model.Cost
import com.gatita.domain.model.CostInput
import com.gatita.domain.model.CostKind
import com.gatita.domain.model.CostLedger
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.FUEL_SEGMENTS_CALCULATION_VERSION
import com.gatita.domain.model.HistoryFilter
import com.gatita.domain.model.HistorySummary
import com.gatita.domain.model.PaymentPreview
import com.gatita.domain.model.PaymentSettlement
import com.gatita.domain.model.SettlementList
import com.gatita.domain.model.SettlementRecord
import com.gatita.domain.model.Trip
import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.UNSAVED_ID
import com.gatita.domain.model.domainFailure
import com.gatita.domain.repository.CostRepository
import com.gatita.domain.repository.EmptyTripRepository
import com.gatita.domain.repository.FuelTrackingRepository
import com.gatita.domain.repository.PaymentSettlementRepository
import com.gatita.domain.repository.SettlementRepository
import com.gatita.domain.repository.TransactionRunner
import com.gatita.domain.repository.TripLedgerRepository
import com.gatita.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/*
 * 결제액 방식(payments-v1) 유스케이스. 모든 쓰기는 하나의 트랜잭션에서 상태·revision을 다시 검사한다.
 *
 * expectedRevision은 화면이 마지막으로 본 Trip/CostLedger/PaymentPreview의 revision이다.
 * null이면 검사하지 않으며(자동 덮어쓰기 가능), 정산 확정만 필수다.
 */

private fun requestIdOrFailure(raw: String): DomainResult<String> {
    val requestId = raw.trim()
    return if (requestId.isEmpty() || requestId.length > MAX_ID_LENGTH) {
        domainFailure(ErrorCode.INVALID_REQUEST_ID, "Request id must be 1..$MAX_ID_LENGTH characters.")
    } else {
        DomainResult.Success(requestId)
    }
}

/** 비용 편집 가능 여부와 revision 검사. 문제가 없으면 null. */
private fun Trip.editFailure(expectedRevision: Long?): DomainResult.Failure? = when {
    status == TripStatus.SETTLED -> domainFailure(ErrorCode.TRIP_LOCKED, "A settled trip cannot be changed.")
    expectedRevision != null && expectedRevision != revision ->
        domainFailure(ErrorCode.STALE_REVISION, "Trip changed since it was loaded.")
    else -> null
}

private suspend fun readLedger(trips: TripRepository, costs: CostRepository, tripId: Long): DomainResult<CostLedger> {
    val trip = trips.getTrip(tripId) ?: return domainFailure(ErrorCode.TRIP_NOT_FOUND, "Trip $tripId not found.")
    return PaymentSettlementCalculator.summarize(trip, costs.getCosts(tripId))
}

private suspend fun readPreview(
    trips: TripRepository,
    costs: CostRepository,
    tripId: Long,
    driverName: String?,
    fuelTracking: FuelTrackingRepository? = null,
): DomainResult<PaymentPreview> {
    val trip = trips.getTrip(tripId) ?: return domainFailure(ErrorCode.TRIP_NOT_FOUND, "Trip $tripId not found.")
    if (trip.usesFuelSegments() && fuelTracking != null) {
        return FuelSettlementPlanner.preview(
            trip,
            costs.getCosts(tripId),
            fuelTracking.getSnapshots(tripId),
            fuelTracking.getRefuels(tripId),
            driverName,
        )
    }
    return PaymentSettlementCalculator.preview(trip, costs.getCosts(tripId), trips.getSegments(tripId), driverName)
}

private fun Trip.usesFuelSegments(): Boolean = calculationVersion == FUEL_SEGMENTS_CALCULATION_VERSION

// ------------------------------------------------------------------ 여행 완료

/**
 * [endOdometerKm]이 null이면 마지막으로 기록된 주행거리(없으면 시작 값)를 종료 값으로 쓴다. 즉 거리 기록 없이도 완료할 수 있다.
 * 거리도 비용도 없는 여행은 [ErrorCode.EMPTY_TRIP]이다. 비용만 있는 0km 여행은 완료된다.
 */
data class CompleteTripCommand(
    val tripId: Long,
    val endedAtMillis: Long,
    val endOdometerKm: BigDecimal? = null,
    val expectedRevision: Long? = null,
)

/** 기존 [FinishTripUseCase]와 달리 0km 여행과 주행거리 없는 완료를 허용한다. 같은 값으로 다시 호출하면 그대로 성공한다. */
class CompleteTripUseCase(
    private val trips: TripRepository,
    private val costs: CostRepository,
    private val transactions: TransactionRunner,
    private val fuelTracking: FuelTrackingRepository? = null,
) {
    suspend operator fun invoke(command: CompleteTripCommand): DomainResult<Trip> =
        transactions.run { complete(command) }

    private suspend fun complete(command: CompleteTripCommand): DomainResult<Trip> {
        val trip = trips.getTrip(command.tripId)
            ?: return domainFailure(ErrorCode.TRIP_NOT_FOUND, "Trip ${command.tripId} not found.")
        val requestedEnd = command.endOdometerKm?.canonical()

        when (trip.status) {
            TripStatus.SETTLED -> return domainFailure(ErrorCode.TRIP_LOCKED, "A settled trip cannot be changed.")
            TripStatus.FINISHED -> return if (trip.endedAtMillis == command.endedAtMillis &&
                (requestedEnd == null || trip.endOdometerKm == requestedEnd)
            ) {
                DomainResult.Success(trip)
            } else {
                domainFailure(ErrorCode.TRIP_NOT_IN_PROGRESS, "Trip is already completed.")
            }

            TripStatus.IN_PROGRESS -> Unit
        }
        if (command.expectedRevision != null && command.expectedRevision != trip.revision) {
            return domainFailure(ErrorCode.STALE_REVISION, "Trip changed since it was loaded.")
        }

        val last = trips.getSegments(trip.id).maxByOrNull { it.seq }
        val floor = last?.endOdometerKm ?: trip.startOdometerKm
        val floorTime = last?.endedAtMillis ?: trip.startedAtMillis
        val end = requestedEnd ?: floor

        if (end.signum() < 0) return domainFailure(ErrorCode.INVALID_ODOMETER, "Odometer must not be negative.")
        if (end < floor) {
            return domainFailure(ErrorCode.ODOMETER_DECREASED, "End odometer is lower than the last recorded odometer.")
        }
        if (command.endedAtMillis <= 0 || command.endedAtMillis < floorTime) {
            return domainFailure(ErrorCode.INVALID_TIMESTAMP, "End time is earlier than the last recorded time.")
        }
        if (end == trip.startOdometerKm && costs.getCosts(trip.id).isEmpty()) {
            return domainFailure(ErrorCode.EMPTY_TRIP, "A trip with no distance and no costs cannot be completed.")
        }
        if (trip.usesFuelSegments() && fuelTracking != null) {
            val matching = FuelSettlementPlanner.checkCompletion(
                costs.getCosts(trip.id),
                fuelTracking.getRefuels(trip.id),
            )
            if (matching is DomainResult.Failure) return matching
        }
        return DomainResult.Success(trips.markFinished(trip.id, end, command.endedAtMillis))
    }
}

/**
 * 실수로 시작한 빈 여행을 닫는다. 트랜잭션 안에서 다시 읽어 진행 중(IN_PROGRESS)이고 구간·비용·기록 거리가 모두 없을 때만
 * 해당 여행 한 건을 삭제한다. 하나라도 있으면 아무것도 지우지 않고 실패를 돌려준다.
 * 이미 없는 여행은 [ErrorCode.TRIP_NOT_FOUND]다. 완료·확정 여행의 삭제는 지원하지 않는다.
 */
class DiscardEmptyTripUseCase(
    private val trips: TripRepository,
    private val costs: CostRepository,
    private val emptyTrips: EmptyTripRepository,
    private val transactions: TransactionRunner,
) {
    suspend operator fun invoke(tripId: Long): DomainResult<Unit> = transactions.run {
        val trip = trips.getTrip(tripId)
            ?: return@run domainFailure(ErrorCode.TRIP_NOT_FOUND, "Trip $tripId not found.")
        when (trip.status) {
            TripStatus.SETTLED -> return@run domainFailure(ErrorCode.TRIP_LOCKED, "A settled trip cannot be discarded.")
            TripStatus.FINISHED ->
                return@run domainFailure(ErrorCode.TRIP_NOT_IN_PROGRESS, "Only an in-progress trip can be discarded.")

            TripStatus.IN_PROGRESS -> Unit
        }
        if (trip.endOdometerKm != null || trips.getSegments(tripId).isNotEmpty() || costs.getCosts(tripId).isNotEmpty()) {
            return@run domainFailure(ErrorCode.TRIP_NOT_EMPTY, "Trip has recorded distance or costs.")
        }
        if (!emptyTrips.deleteIfEmptyActive(tripId)) {
            return@run domainFailure(ErrorCode.TRIP_NOT_EMPTY, "Trip could not be discarded as empty.")
        }
        DomainResult.Success(Unit)
    }
}

// ------------------------------------------------------------------ 비용

/**
 * 비용 등록. [requestId]는 등록 시도 하나를 식별하는 호출자 생성 ID이며, 같은 ID·같은 내용으로 다시 호출하면 기존 비용을 돌려준다(이중 탭).
 * 같은 ID에 다른 내용이면 [ErrorCode.REQUEST_ID_REUSED]. 신규 비용은 정산에 포함(included=true)된다.
 * FUEL의 [CostInput.paidOn]이 null이면 오늘(기기 지역 날짜)로 채운다.
 */
class AddCostUseCase(
    private val trips: TripRepository,
    private val costs: CostRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend operator fun invoke(
        tripId: Long,
        input: CostInput,
        requestId: String,
        expectedRevision: Long? = null,
    ): DomainResult<Cost> {
        val id = when (val result = requestIdOrFailure(requestId)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        val valid = when (val result = CostValidator.validate(input)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        return transactions.run {
            val trip = trips.getTrip(tripId)
                ?: return@run domainFailure(ErrorCode.TRIP_NOT_FOUND, "Trip $tripId not found.")
            costs.findByRequestId(id)?.let { existing ->
                return@run if (existing.tripId == tripId && existing.matches(valid)) {
                    DomainResult.Success(existing)
                } else {
                    domainFailure(ErrorCode.REQUEST_ID_REUSED, "Request id was already used for a different cost.")
                }
            }
            trip.editFailure(expectedRevision)?.let { return@run it }

            val now = clock.millis()
            DomainResult.Success(
                costs.insert(
                    Cost(
                        id = UNSAVED_ID,
                        tripId = tripId,
                        requestId = id,
                        kind = valid.kind,
                        amountWon = valid.amountWon,
                        description = valid.description,
                        unitPriceWonPerLiter = valid.unitPriceWonPerLiter,
                        paidOn = if (valid.kind == CostKind.FUEL) valid.paidOn ?: today() else null,
                        included = true,
                        createdAtMillis = now,
                        updatedAtMillis = now,
                    ),
                ),
            )
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(zone))

    private fun Cost.matches(input: CostInput): Boolean =
        kind == input.kind &&
            amountWon == input.amountWon &&
            description == input.description &&
            unitPriceWonPerLiter == input.unitPriceWonPerLiter &&
            (input.paidOn == null || paidOn == input.paidOn)
}

/** 비용 수정. 포함 여부는 보존하고 소속 여행이 다르면 [ErrorCode.COST_NOT_FOUND]. */
class UpdateCostUseCase(
    private val trips: TripRepository,
    private val costs: CostRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend operator fun invoke(
        tripId: Long,
        costId: Long,
        input: CostInput,
        expectedRevision: Long? = null,
    ): DomainResult<Cost> {
        val valid = when (val result = CostValidator.validate(input)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        return transactions.run {
            val trip = trips.getTrip(tripId)
                ?: return@run domainFailure(ErrorCode.TRIP_NOT_FOUND, "Trip $tripId not found.")
            val existing = costs.get(costId)?.takeIf { it.tripId == tripId }
                ?: return@run domainFailure(ErrorCode.COST_NOT_FOUND, "Cost $costId not found in trip $tripId.")
            trip.editFailure(expectedRevision)?.let { return@run it }

            val updated = existing.copy(
                kind = valid.kind,
                amountWon = valid.amountWon,
                description = valid.description,
                unitPriceWonPerLiter = valid.unitPriceWonPerLiter,
                paidOn = if (valid.kind == CostKind.FUEL) {
                    valid.paidOn ?: existing.paidOn ?: LocalDate.now(clock.withZone(zone))
                } else {
                    null
                },
                updatedAtMillis = clock.millis(),
            )
            if (!costs.update(updated)) {
                return@run domainFailure(ErrorCode.COST_NOT_FOUND, "Cost $costId not found in trip $tripId.")
            }
            DomainResult.Success(updated)
        }
    }
}

/** 포함 여부를 토글이 아닌 절대값으로 설정한다. 이미 같은 값이면 아무것도 바꾸지 않는다(revision 유지). */
class SetCostIncludedUseCase(
    private val trips: TripRepository,
    private val costs: CostRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        tripId: Long,
        costId: Long,
        included: Boolean,
        expectedRevision: Long? = null,
    ): DomainResult<CostLedger> = transactions.run {
        val trip = trips.getTrip(tripId)
            ?: return@run domainFailure(ErrorCode.TRIP_NOT_FOUND, "Trip $tripId not found.")
        val existing = costs.get(costId)?.takeIf { it.tripId == tripId }
            ?: return@run domainFailure(ErrorCode.COST_NOT_FOUND, "Cost $costId not found in trip $tripId.")
        trip.editFailure(expectedRevision)?.let { return@run it }
        if (existing.included != included) {
            costs.setIncluded(costId, included, clock.millis())
        }
        readLedger(trips, costs, tripId)
    }
}

/** 비용 삭제. 확정된 여행은 [ErrorCode.TRIP_LOCKED]. 삭제 확인은 화면 책임이다. */
class DeleteCostUseCase(
    private val trips: TripRepository,
    private val costs: CostRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(tripId: Long, costId: Long, expectedRevision: Long? = null): DomainResult<CostLedger> =
        transactions.run {
            val trip = trips.getTrip(tripId)
                ?: return@run domainFailure(ErrorCode.TRIP_NOT_FOUND, "Trip $tripId not found.")
            costs.get(costId)?.takeIf { it.tripId == tripId }
                ?: return@run domainFailure(ErrorCode.COST_NOT_FOUND, "Cost $costId not found in trip $tripId.")
            trip.editFailure(expectedRevision)?.let { return@run it }
            costs.delete(costId, clock.millis())
            readLedger(trips, costs, tripId)
        }
}

/** 비용 목록·합계 구독. 여행이 없으면 [ErrorCode.TRIP_NOT_FOUND] 실패를 내보낸다. 실패를 빈 목록이나 0원으로 바꾸지 않는다. */
class ObserveCostLedgerUseCase(private val ledger: TripLedgerRepository) {
    operator fun invoke(tripId: Long): Flow<DomainResult<CostLedger>> =
        ledger.observeTripWithCosts(tripId).map { tripWithCosts ->
            if (tripWithCosts == null) {
                domainFailure(ErrorCode.TRIP_NOT_FOUND, "Trip $tripId not found.")
            } else {
                PaymentSettlementCalculator.summarize(tripWithCosts.trip, tripWithCosts.costs)
            }
        }
}

// ------------------------------------------------------------------ 분담 정책·미리보기

/**
 * 종료된(FINISHED) 여행의 인원과 운전자 포함 여부를 바꾸고 새 미리보기를 돌려준다. 인원이 1명이면 운전자 포함으로 정규화한다.
 * 진행 중이면 [ErrorCode.TRIP_NOT_FINISHED], 확정됐으면 [ErrorCode.TRIP_LOCKED]. 값이 같으면 revision을 올리지 않는다.
 */
class UpdateSplitPolicyUseCase(
    private val trips: TripRepository,
    private val costs: CostRepository,
    private val ledger: TripLedgerRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val fuelTracking: FuelTrackingRepository? = null,
) {
    suspend operator fun invoke(
        tripId: Long,
        people: Int,
        includeDriver: Boolean,
        expectedRevision: Long? = null,
        driverName: String? = null,
    ): DomainResult<PaymentPreview> {
        val (normalizedPeople, normalizedInclude) = when (val result = CostValidator.normalizeSplitPolicy(people, includeDriver)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        return transactions.run {
            val trip = trips.getTrip(tripId)
                ?: return@run domainFailure(ErrorCode.TRIP_NOT_FOUND, "Trip $tripId not found.")
            when (trip.status) {
                TripStatus.IN_PROGRESS ->
                    return@run domainFailure(ErrorCode.TRIP_NOT_FINISHED, "Trip must be completed first.")

                TripStatus.SETTLED -> return@run domainFailure(ErrorCode.TRIP_LOCKED, "A settled trip cannot be changed.")
                TripStatus.FINISHED -> Unit
            }
            if (expectedRevision != null && expectedRevision != trip.revision) {
                return@run domainFailure(ErrorCode.STALE_REVISION, "Trip changed since it was loaded.")
            }
            if (trip.people != normalizedPeople || trip.includeDriver != normalizedInclude) {
                val changed = ledger.updateSplitPolicy(
                    tripId, normalizedPeople, normalizedInclude, trip.revision, clock.millis(),
                )
                if (!changed) {
                    return@run domainFailure(ErrorCode.STALE_REVISION, "Trip changed since it was loaded.")
                }
            }
            readPreview(trips, costs, tripId, driverName, fuelTracking)
        }
    }
}

/**
 * 저장된 비용과 분담 정책으로 계산한 미리보기. 진행 중 여행은 미리볼 수 있지만 [PaymentPreview.canConfirm]이 false다.
 * 이미 확정된 여행은 [ErrorCode.ALREADY_SETTLED]이며 [GetSettlementRecordUseCase]로 저장본을 읽는다.
 */
class PreviewPaymentSettlementUseCase(
    private val trips: TripRepository,
    private val costs: CostRepository,
    private val transactions: TransactionRunner,
    private val fuelTracking: FuelTrackingRepository? = null,
) {
    suspend operator fun invoke(tripId: Long, driverName: String? = null): DomainResult<PaymentPreview> =
        transactions.run {
            val trip = trips.getTrip(tripId)
                ?: return@run domainFailure(ErrorCode.TRIP_NOT_FOUND, "Trip $tripId not found.")
            if (trip.status == TripStatus.SETTLED) {
                domainFailure(ErrorCode.ALREADY_SETTLED, "Trip already has a confirmed settlement.")
            } else {
                readPreview(trips, costs, tripId, driverName, fuelTracking)
            }
        }
}

// ------------------------------------------------------------------ 확정

/**
 * [requestId]는 확정 시도 하나를 식별하는 호출자 생성 ID(같은 시도 재전송에는 같은 ID).
 * [expectedRevision]은 사용자가 확인한 [PaymentPreview.tripRevision]이며 필수다.
 * [driverName]은 확정 명세에 고정될 운전자 표시 이름(비면 "나").
 */
data class ConfirmPaymentCommand(
    val tripId: Long,
    val requestId: String,
    val expectedRevision: Long,
    val driverName: String? = null,
)

/**
 * 결제액 정산 확정. 종료된 여행에 포함 비용이 하나 이상일 때만 가능하며, 비용 전체(제외 포함)·분담 정책·분담액·표시 이름·
 * 기록 메타데이터·계산 버전(payments-v1)을 하나의 트랜잭션으로 저장하고 여행을 SETTLED로 바꾼다.
 *
 * - 같은 요청 ID·같은 입력의 재호출은 기존 정산을 그대로 돌려준다(중복 확정 없음).
 * - 같은 요청 ID에 다른 입력은 [ErrorCode.REQUEST_ID_REUSED], 이미 확정된 여행에 다른 요청 ID는 [ErrorCode.ALREADY_SETTLED].
 * - 확인 후 비용·정책이 바뀌었으면 [ErrorCode.STALE_REVISION]. 새 미리보기를 보여주고 다시 확정해야 한다.
 * - 측정값(거리·연비) 상태는 확정을 막지 않는다.
 */
class ConfirmPaymentSettlementUseCase(
    private val trips: TripRepository,
    private val costs: CostRepository,
    private val settlements: PaymentSettlementRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val fuelTracking: FuelTrackingRepository? = null,
) {
    suspend operator fun invoke(command: ConfirmPaymentCommand): DomainResult<PaymentSettlement> {
        val requestId = when (val result = requestIdOrFailure(command.requestId)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        val driverName = when (val result = CostValidator.normalizeDisplayName(command.driverName)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        return transactions.run { confirm(command, requestId, driverName) }
    }

    private suspend fun confirm(
        command: ConfirmPaymentCommand,
        requestId: String,
        driverName: String,
    ): DomainResult<PaymentSettlement> {
        val tripId = command.tripId
        settlements.findByRequestId(requestId)?.let { existing ->
            val sameRequest = existing.tripId == tripId &&
                existing.sourceTripRevision == command.expectedRevision &&
                existing.shares.firstOrNull { it.isDriver }?.displayName == driverName
            return if (sameRequest) {
                DomainResult.Success(existing)
            } else {
                domainFailure(ErrorCode.REQUEST_ID_REUSED, "Request id was already used for a different settlement.")
            }
        }

        val trip = trips.getTrip(tripId) ?: return domainFailure(ErrorCode.TRIP_NOT_FOUND, "Trip $tripId not found.")
        when (trip.status) {
            TripStatus.SETTLED ->
                return domainFailure(ErrorCode.ALREADY_SETTLED, "Trip already has a confirmed settlement.")

            TripStatus.IN_PROGRESS ->
                return domainFailure(ErrorCode.TRIP_NOT_FINISHED, "Trip must be completed before settlement.")

            TripStatus.FINISHED -> Unit
        }
        if (trip.revision != command.expectedRevision) {
            return domainFailure(ErrorCode.STALE_REVISION, "Trip changed since the preview was shown.")
        }

        val built = if (trip.usesFuelSegments() && fuelTracking != null) {
            FuelSettlementPlanner.buildSnapshot(
                trip = trip,
                costs = costs.getCosts(tripId),
                snapshots = fuelTracking.getSnapshots(tripId),
                refuels = fuelTracking.getRefuels(tripId),
                driverName = driverName,
                requestId = requestId,
                confirmedAtMillis = clock.millis(),
            )
        } else {
            PaymentSettlementCalculator.buildSnapshot(
                trip = trip,
                costs = costs.getCosts(tripId),
                segments = trips.getSegments(tripId),
                driverName = driverName,
                requestId = requestId,
                confirmedAtMillis = clock.millis(),
            )
        }
        val snapshot = when (val result = built) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        return DomainResult.Success(settlements.saveAndMarkTripSettled(snapshot, command.expectedRevision))
    }
}

// ------------------------------------------------------------------ 조회

/**
 * 확정된 정산 기록. 결제액 정산은 [SettlementRecord.Payments], v1 추정 정산은 [SettlementRecord.Legacy]이며
 * 화면은 둘을 구분해 표시해야 한다. 정산이 없으면 [ErrorCode.SETTLEMENT_NOT_FOUND].
 */
class GetSettlementRecordUseCase(
    private val payments: PaymentSettlementRepository,
    private val legacy: SettlementRepository,
) {
    suspend operator fun invoke(tripId: Long): DomainResult<SettlementRecord> {
        payments.findByTripId(tripId)?.let { return DomainResult.Success(SettlementRecord.Payments(it)) }
        legacy.findByTripId(tripId)?.let { return DomainResult.Success(SettlementRecord.Legacy(it)) }
        return domainFailure(ErrorCode.SETTLEMENT_NOT_FOUND, "Trip $tripId has no confirmed settlement.")
    }
}

/** 저장된 이름·금액만으로 만든 공유 요약. 결제액 정산만 대상이며 v1 정산은 [ErrorCode.SETTLEMENT_NOT_FOUND]. */
class BuildPaymentSummaryUseCase(private val payments: PaymentSettlementRepository) {
    suspend operator fun invoke(tripId: Long): DomainResult<String> {
        val settlement = payments.findByTripId(tripId)
            ?: return domainFailure(ErrorCode.SETTLEMENT_NOT_FOUND, "Trip $tripId has no payment settlement.")
        return DomainResult.Success(PaymentSummaryFormatter.format(settlement))
    }
}

/** 월별 내역. 월 통계는 상태 필터와 무관하고 목록만 필터링한다. 월은 여행 시작 지역의 날짜 기준이다. */
class ObserveTripHistoryUseCase(private val ledger: TripLedgerRepository) {
    operator fun invoke(month: YearMonth, filter: HistoryFilter = HistoryFilter.ALL): Flow<HistorySummary> =
        ledger.observeSummaries().map { all ->
            val inMonth = all.filter { YearMonth.from(it.tripDate) == month }
            HistorySummary(
                month = month,
                filter = filter,
                trips = when (filter) {
                    HistoryFilter.ALL -> inMonth
                    HistoryFilter.PENDING -> inMonth.filter { it.status != TripStatus.SETTLED }
                    HistoryFilter.SETTLED -> inMonth.filter { it.status == TripStatus.SETTLED }
                },
                monthlyTripCount = inMonth.size,
                monthlyDistanceKm = inMonth.mapNotNull { it.distanceKm }.fold(BigDecimal.ZERO) { acc, km -> acc.add(km) },
                monthlySettledCount = inMonth.count { it.status == TripStatus.SETTLED },
            )
        }
}

/** 종료됐지만 미확정인 여행과 확정된 여행의 목록. 진행 중 여행은 포함하지 않는다. */
class ObserveSettlementListUseCase(private val ledger: TripLedgerRepository) {
    operator fun invoke(): Flow<SettlementList> = ledger.observeSummaries().map { all ->
        val pending = all.filter { it.status == TripStatus.FINISHED }
        SettlementList(
            pending = pending,
            settled = all.filter { it.status == TripStatus.SETTLED },
            pendingTotalWon = pending.sumOf { it.includedTotalWon },
        )
    }
}
