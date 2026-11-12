package com.gatita.domain.calculator

import com.gatita.domain.model.Cost
import com.gatita.domain.model.CostKind
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.FUEL_SEGMENTS_CALCULATION_VERSION
import com.gatita.domain.model.FuelCostRef
import com.gatita.domain.model.FuelSettlementInput
import com.gatita.domain.model.FuelSettlementResult
import com.gatita.domain.model.FuelSnapshot
import com.gatita.domain.model.PaymentPreview
import com.gatita.domain.model.PaymentSettlement
import com.gatita.domain.model.PaymentSettlementCost
import com.gatita.domain.model.RefuelEvent
import com.gatita.domain.model.Trip
import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.UNSAVED_ID
import com.gatita.domain.model.domainFailure

/**
 * [FUEL_SEGMENTS_CALCULATION_VERSION] 여행의 미리보기와 확정 스냅샷을 만든다. 순수 함수다.
 * 저장된 측정·주유 이벤트·비용·분담 정책으로 [FuelSettlementCalculator]를 호출하고, 분담은 결제액 방식과 같은 정수 배분을 쓴다.
 *
 * FUEL 비용은 단가 산정에만 쓰이며 금액을 총액에 직접 더하지 않는다(그 금액의 영수증 값이 아니라 사용량 × 단가가 총액이다).
 * FUEL 비용의 포함(included) 표시는 이 방식에서 계산에 쓰이지 않는다.
 */
object FuelSettlementPlanner {

    /** 순서대로(등록 순서) 짝짓기 대상이 되는 FUEL 비용. 단가가 없는 행은 저장 규칙상 없지만 있으면 무시한다. */
    private fun fuelCostRefs(costs: List<Cost>): List<FuelCostRef> =
        costs.filter { it.kind == CostKind.FUEL }
            .sortedWith(compareBy({ it.createdAtMillis }, { it.id }))
            .mapNotNull { cost -> cost.unitPriceWonPerLiter?.let { FuelCostRef(cost.id, it, cost.undetected) } }

    private fun otherTotal(costs: List<Cost>): Long? = try {
        costs.filter { it.kind != CostKind.FUEL && it.included }
            .fold(0L) { acc, cost -> Math.addExact(acc, cost.amountWon) }
    } catch (e: ArithmeticException) {
        null
    }

    fun calculate(
        trip: Trip,
        costs: List<Cost>,
        snapshots: List<FuelSnapshot>,
        refuels: List<RefuelEvent>,
    ): DomainResult<FuelSettlementResult> {
        val other = otherTotal(costs)
            ?: return domainFailure(ErrorCode.AMOUNT_OUT_OF_RANGE, "Cost total is out of range.")
        return FuelSettlementCalculator.calculate(
            FuelSettlementInput(
                intervals = FuelIntervalBuilder.build(snapshots, refuels),
                fuelCosts = fuelCostRefs(costs),
                manualUnitPrice = trip.manualFuelUnitPrice,
                otherTotalWon = other,
            ),
        )
    }

    /** 종료 가능 여부(여행 종료 조건). 감지된 주유 수와 짝짓기 대상 FUEL 비용 수가 같아야 한다. */
    fun checkCompletion(costs: List<Cost>, refuels: List<RefuelEvent>): DomainResult<Unit> =
        FuelSettlementCalculator.checkRefuelMatching(refuels.count { !it.dismissed }, fuelCostRefs(costs))

    fun preview(
        trip: Trip,
        costs: List<Cost>,
        snapshots: List<FuelSnapshot>,
        refuels: List<RefuelEvent>,
        driverName: String?,
    ): DomainResult<PaymentPreview> {
        val policy = when (val result = CostValidator.normalizeSplitPolicy(trip.people, trip.includeDriver)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        val name = when (val result = CostValidator.normalizeDisplayName(driverName)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        val fuel = when (val result = calculate(trip, costs, snapshots, refuels)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        val (people, includeDriver) = policy
        val blocking = when {
            trip.status != TripStatus.FINISHED -> ErrorCode.TRIP_NOT_FINISHED
            else -> fuel.blockingReason
        }
        return DomainResult.Success(
            PaymentPreview(
                tripId = trip.id,
                tripRevision = trip.revision,
                tripStatus = trip.status,
                totalWon = fuel.totalWon,
                fuelTotalWon = fuel.fuelCostWon,
                otherTotalWon = fuel.otherTotalWon,
                includedCount = costs.count { it.kind == CostKind.FUEL || it.included },
                registeredCount = costs.size,
                people = people,
                splitPeople = if (includeDriver) people else people - 1,
                includeDriver = includeDriver,
                shares = PaymentSettlementCalculator.calculateShares(fuel.totalWon, people, includeDriver, name),
                canConfirm = blocking == null,
                blockingReason = blocking,
                recording = PaymentSettlementCalculator.deriveRecording(trip, emptyList()),
                fuel = fuel,
            ),
        )
    }

    fun buildSnapshot(
        trip: Trip,
        costs: List<Cost>,
        snapshots: List<FuelSnapshot>,
        refuels: List<RefuelEvent>,
        driverName: String?,
        requestId: String,
        confirmedAtMillis: Long,
    ): DomainResult<PaymentSettlement> {
        val preview = when (val result = preview(trip, costs, snapshots, refuels, driverName)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        val fuel = checkNotNull(preview.fuel)
        if (!fuel.canConfirm) {
            return domainFailure(ErrorCode.NO_INCLUDED_COSTS, "There is nothing to settle.")
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
                calculationVersion = FUEL_SEGMENTS_CALCULATION_VERSION,
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
                fuelIntervals = fuel.intervals,
            ),
        )
    }
}
