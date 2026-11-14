package com.gatita.domain.calculator

import com.gatita.domain.model.Cost
import com.gatita.domain.model.CostKind
import com.gatita.domain.model.CostLedger
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.EfficiencyScope
import com.gatita.domain.model.MeasurementQuality
import com.gatita.domain.model.PAYMENTS_CALCULATION_VERSION
import com.gatita.domain.model.PaymentPreview
import com.gatita.domain.model.PaymentSettlement
import com.gatita.domain.model.PaymentSettlementCost
import com.gatita.domain.model.PaymentShare
import com.gatita.domain.model.RecordingMetadata
import com.gatita.domain.model.Segment
import com.gatita.domain.model.Trip
import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.UNSAVED_ID
import com.gatita.domain.model.Decimals
import com.gatita.domain.model.domainFailure
import java.math.BigDecimal

object ShareLabels {
    const val DEFAULT_DRIVER_NAME = "나"

    fun passenger(ordinal: Int): String = "동승자 $ordinal"
}

/**
 * 결제액 정산 계산. 정산 총액은 포함된 실제 결제 금액의 합계이며 주행거리·연비·유가는 사용하지 않는다.
 * 주행 측정값은 [deriveRecording]으로 기록 메타데이터에만 쓰이고, 측정이 없거나 잘못돼도 계산은 실패하지 않는다.
 */
object PaymentSettlementCalculator {

    /** Long 합계. 오버플로하면 null. */
    private fun checkedSum(amounts: List<Long>): Long? = try {
        amounts.fold(0L) { acc, amount -> Math.addExact(acc, amount) }
    } catch (e: ArithmeticException) {
        null
    }

    private val overflow = domainFailure(ErrorCode.AMOUNT_OUT_OF_RANGE, "Cost total is out of range.")

    fun summarize(trip: Trip, costs: List<Cost>): DomainResult<CostLedger> {
        val registered = checkedSum(costs.map { it.amountWon }) ?: return overflow
        val included = checkedSum(costs.filter { it.included }.map { it.amountWon }) ?: return overflow
        val byKind = CostKind.entries.associateWith { kind ->
            checkedSum(costs.filter { it.kind == kind }.map { it.amountWon }) ?: return overflow
        }
        return DomainResult.Success(
            CostLedger(
                tripId = trip.id,
                tripStatus = trip.status,
                tripRevision = trip.revision,
                editable = trip.status != TripStatus.SETTLED,
                costs = costs,
                count = costs.size,
                includedCount = costs.count { it.included },
                registeredTotalWon = registered,
                includedTotalWon = included,
                excludedTotalWon = registered - included,
                totalsByKind = byKind,
            ),
        )
    }

    /**
     * 총액을 인원에게 정수 원으로 나눈다. 운전자 포함이면 [people]명, 제외면 운전자(ordinal 0)는 0원이고 동승자만 나눈다.
     * 나머지는 대상자 순서대로 1원씩 더하며 합계는 항상 [totalWon]과 같다. 인자는 [CostValidator.normalizeSplitPolicy]를 거친 값이어야 한다.
     */
    fun calculateShares(totalWon: Long, people: Int, includeDriver: Boolean, driverName: String): List<PaymentShare> {
        require(people in 1..Int.MAX_VALUE) { "people must be positive" }
        val driverIncluded = includeDriver || people == 1
        val splitPeople = if (driverIncluded) people else people - 1
        val amounts = ShareAllocator.allocate(totalWon, splitPeople)
        return List(people) { ordinal ->
            val amount = when {
                driverIncluded -> amounts[ordinal]
                ordinal == 0 -> 0L
                else -> amounts[ordinal - 1]
            }
            PaymentShare(
                ordinal = ordinal,
                displayName = if (ordinal == 0) driverName else ShareLabels.passenger(ordinal),
                isDriver = ordinal == 0,
                amountWon = amount,
            )
        }
    }

    fun preview(trip: Trip, costs: List<Cost>, segments: List<Segment>, driverName: String?): DomainResult<PaymentPreview> {
        val policy = when (val result = CostValidator.normalizeSplitPolicy(trip.people, trip.includeDriver)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        val name = when (val result = CostValidator.normalizeDisplayName(driverName)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        val included = costs.filter { it.included }
        val total = checkedSum(included.map { it.amountWon }) ?: return overflow
        val fuel = checkedSum(included.filter { it.kind == CostKind.FUEL }.map { it.amountWon }) ?: return overflow
        val (people, includeDriver) = policy
        val blocking = when {
            trip.status != TripStatus.FINISHED -> ErrorCode.TRIP_NOT_FINISHED
            included.isEmpty() -> ErrorCode.NO_INCLUDED_COSTS
            else -> null
        }
        return DomainResult.Success(
            PaymentPreview(
                tripId = trip.id,
                tripRevision = trip.revision,
                tripStatus = trip.status,
                totalWon = total,
                fuelTotalWon = fuel,
                otherTotalWon = total - fuel,
                includedCount = included.size,
                registeredCount = costs.size,
                people = people,
                splitPeople = if (includeDriver) people else people - 1,
                includeDriver = includeDriver,
                shares = calculateShares(total, people, includeDriver, name),
                canConfirm = blocking == null,
                blockingReason = blocking,
                recording = deriveRecording(trip, segments),
            ),
        )
    }

    /**
     * 확정용 스냅샷. 제외된 비용까지 모두 복사하고 분담 정책·표시 이름·기록 메타데이터를 고정한다.
     * 포함 비용이 없으면 [ErrorCode.NO_INCLUDED_COSTS]. 호출자는 trip이 FINISHED이고 revision이 맞는지 먼저 확인해야 한다.
     */
    fun buildSnapshot(
        trip: Trip,
        costs: List<Cost>,
        segments: List<Segment>,
        driverName: String?,
        requestId: String,
        confirmedAtMillis: Long,
    ): DomainResult<PaymentSettlement> {
        val preview = when (val result = preview(trip, costs, segments, driverName)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        if (preview.includedCount == 0) {
            return domainFailure(ErrorCode.NO_INCLUDED_COSTS, "At least one included cost is required.")
        }
        check(preview.shares.sumOf { it.amountWon } == preview.totalWon) { "Shares must add up to the total." }
        return DomainResult.Success(
            PaymentSettlement(
                id = UNSAVED_ID,
                tripId = trip.id,
                requestId = requestId,
                tripName = trip.name,
                tripDate = trip.tripDate,
                recording = preview.recording,
                totalWon = preview.totalWon,
                fuelTotalWon = preview.fuelTotalWon,
                otherTotalWon = preview.otherTotalWon,
                people = preview.people,
                includeDriver = preview.includeDriver,
                splitPeople = preview.splitPeople,
                sourceTripRevision = trip.revision,
                calculationVersion = PAYMENTS_CALCULATION_VERSION,
                confirmedAtMillis = confirmedAtMillis,
                costs = costs.mapIndexed { index, cost ->
                    PaymentSettlementCost(
                        originalCostId = cost.id,
                        ordinal = index,
                        kind = cost.kind,
                        amountWon = cost.amountWon,
                        description = cost.description,
                        unitPriceWonPerLiter = cost.unitPriceWonPerLiter,
                        paidOn = cost.paidOn,
                        included = cost.included,
                    )
                },
                shares = preview.shares,
            ),
        )
    }

    /**
     * 기록 메타데이터 산출. 실패하지 않는다.
     * 거리는 종료된 여행의 주행거리계 구간이 양수일 때만 값을 가지며, 그렇지 않으면 null(MISSING)이다.
     */
    fun deriveRecording(trip: Trip, segments: List<Segment>): RecordingMetadata {
        val ordered = segments.sortedBy { it.seq }
        val end = trip.endOdometerKm
        val span = end?.subtract(trip.startOdometerKm)
        val distance = span?.takeIf { it.signum() > 0 }?.let(Decimals::canonical)
        val seconds = if (ordered.isEmpty()) {
            null
        } else {
            ordered.sumOf { ((it.endedAtMillis - it.startedAtMillis).coerceAtLeast(0)) / 1000 }
        }
        val quality = when {
            distance == null -> MeasurementQuality.MISSING
            isFullyMeasured(trip, ordered, end) -> MeasurementQuality.VALID
            else -> MeasurementQuality.PARTIAL
        }
        return RecordingMetadata(trip.source, distance, seconds, quality)
    }

    private fun isFullyMeasured(trip: Trip, ordered: List<Segment>, end: BigDecimal?): Boolean {
        if (ordered.isEmpty() || end == null) return false
        var cursor: BigDecimal = trip.startOdometerKm
        for (segment in ordered) {
            if (segment.startOdometerKm.compareTo(cursor) != 0) return false
            val efficiency = segment.efficiency ?: return false
            if (efficiency.scope == EfficiencyScope.UNKNOWN) return false
            cursor = segment.endOdometerKm
        }
        return cursor.compareTo(end) == 0
    }
}
