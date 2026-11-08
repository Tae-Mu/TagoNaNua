package com.gatita.domain.calculator

import com.gatita.domain.model.CalculationMethod
import com.gatita.domain.model.Decimals
import com.gatita.domain.model.DomainError
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.EfficiencyMath
import com.gatita.domain.model.EfficiencyScope
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.Segment
import com.gatita.domain.model.SettlementCalculation
import com.gatita.domain.model.SettlementInput
import com.gatita.domain.model.SettlementItem
import com.gatita.domain.model.SettlementShare
import com.gatita.domain.model.Trip
import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.domainFailure
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * 종료된 여행의 구간 기록으로 유류비와 분담액을 계산하는 순수 함수 모음.
 *
 * 구간이 전체 여행 거리를 빠짐없이 덮고 모두 측정·집계 기준이 확인된 경우에만 구간 합산을 한다.
 * 누락(gap)·측정 실패·집계 기준 미확인이 있으면 합계를 만들지 않고 실패를 돌려준다.
 * 호출자가 [SettlementInput.fallbackEfficiency]를 명시한 경우에만 차량 기준 연비로 전체 거리를 추정하며,
 * 이때 결과에 [CalculationMethod.REFERENCE_ESTIMATE]와 사유가 기록된다.
 */
class SettlementCalculator {

    fun calculate(trip: Trip, segments: List<Segment>, input: SettlementInput): DomainResult<SettlementCalculation> {
        val tripEnd = trip.endOdometerKm
        if (trip.status == TripStatus.IN_PROGRESS || tripEnd == null) {
            return domainFailure(ErrorCode.TRIP_NOT_FINISHED, "Trip must be finished before settlement.")
        }
        validateInput(input)?.let { return DomainResult.Failure(it) }

        val ordered = segments.sortedBy { it.seq }
        val blocking = linkedSetOf<ErrorCode>()

        // 구간 연속성: 겹침·역행은 데이터 오류, 빈 구간은 정산을 막는 누락이다.
        var cursor = trip.startOdometerKm
        if (ordered.isEmpty()) blocking += ErrorCode.NO_SEGMENTS
        for (segment in ordered) {
            if (segment.endOdometerKm <= segment.startOdometerKm) {
                return domainFailure(ErrorCode.INVALID_DISTANCE, "Segment ${segment.seq} has no positive distance.")
            }
            if (segment.startOdometerKm < cursor) {
                return domainFailure(ErrorCode.SEGMENT_OVERLAP, "Segment ${segment.seq} overlaps the previous one.")
            }
            if (segment.startOdometerKm > cursor) blocking += ErrorCode.SEGMENT_GAP
            cursor = segment.endOdometerKm
        }
        if (cursor > tripEnd) {
            return domainFailure(ErrorCode.ODOMETER_DECREASED, "Segments extend beyond the trip end odometer.")
        }
        if (ordered.isNotEmpty() && cursor < tripEnd) blocking += ErrorCode.SEGMENT_GAP

        // 구간별 사용량. 측정 실패·집계 기준 미확인은 0으로 처리하지 않고 정산을 막는다.
        val fuelBySeq = HashMap<Int, BigDecimal>()
        for (segment in ordered) {
            val efficiency = segment.efficiency
            if (efficiency == null) {
                blocking += ErrorCode.MEASUREMENT_FAILED_SEGMENT
                continue
            }
            if (efficiency.scope == EfficiencyScope.UNKNOWN) {
                blocking += ErrorCode.UNKNOWN_AGGREGATION
                continue
            }
            when (val liters = EfficiencyMath.segmentFuelLiters(segment.distanceKm, efficiency, segment.baseline)) {
                is DomainResult.Success -> fuelBySeq[segment.seq] = liters.value
                is DomainResult.Failure -> return liters
            }
        }

        val price = Decimals.canonical(input.fuelPrice.pricePerLiterWon)
        val items: List<SettlementItem>
        val method: CalculationMethod
        val reasons: List<ErrorCode>

        if (blocking.isEmpty()) {
            method = CalculationMethod.SEGMENT_SUM
            reasons = emptyList()
            items = ordered.mapIndexed { index, segment ->
                val liters = fuelBySeq.getValue(segment.seq)
                SettlementItem(
                    index = index + 1,
                    segmentSeq = segment.seq,
                    distanceKm = Decimals.canonical(segment.distanceKm),
                    efficiency = checkNotNull(segment.efficiency),
                    baseline = segment.baseline,
                    fuelLiters = Decimals.canonical(liters),
                    unitPriceWonPerLiter = price,
                    costWon = Decimals.canonical(liters.multiply(price)),
                    source = segment.source,
                )
            }
        } else {
            val reference = input.fallbackEfficiency
                ?: return DomainResult.Failure(
                    DomainError(
                        blocking.first(),
                        "Segment data is incomplete; settlement cannot be confirmed without an explicit fallback.",
                        blocking.toList(),
                    ),
                )
            if (reference.scope != EfficiencyScope.VEHICLE_REFERENCE) {
                return domainFailure(
                    ErrorCode.INVALID_EFFICIENCY_SCOPE,
                    "Fallback efficiency must have VEHICLE_REFERENCE scope.",
                )
            }
            EfficiencyMath.validate(reference)?.let { return DomainResult.Failure(it) }
            val distance = tripEnd.subtract(trip.startOdometerKm)
            if (distance.signum() <= 0) {
                return domainFailure(ErrorCode.INVALID_DISTANCE, "Trip has no positive distance.")
            }
            val liters = EfficiencyMath.fuelLiters(distance, reference)
            method = CalculationMethod.REFERENCE_ESTIMATE
            reasons = blocking.toList()
            items = listOf(
                SettlementItem(
                    index = 1,
                    segmentSeq = null,
                    distanceKm = Decimals.canonical(distance),
                    efficiency = reference.copy(
                        value = Decimals.canonical(reference.value),
                        cumulativeDistanceKm = reference.cumulativeDistanceKm?.let(Decimals::canonical),
                    ),
                    baseline = null,
                    fuelLiters = Decimals.canonical(liters),
                    unitPriceWonPerLiter = price,
                    costWon = Decimals.canonical(liters.multiply(price)),
                    source = trip.source,
                ),
            )
        }

        val totalLiters = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.fuelLiters) }
        val rawTotal = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.costWon) }
        val totalWon = try {
            rawTotal.setScale(0, Decimals.TOTAL_ROUNDING).longValueExact()
        } catch (e: ArithmeticException) {
            return domainFailure(ErrorCode.AMOUNT_OUT_OF_RANGE, "Total cost is out of range.")
        }

        val amounts = ShareAllocator.allocate(totalWon, input.headcount)
        val shares = amounts.mapIndexed { index, amount ->
            SettlementShare(
                participantIndex = index + 1,
                participantName = input.participantNames?.get(index)?.trim(),
                amountWon = amount,
            )
        }

        return DomainResult.Success(
            SettlementCalculation(
                method = method,
                fallbackReasons = reasons,
                fuelPrice = input.fuelPrice.copy(pricePerLiterWon = price),
                headcount = input.headcount,
                totalFuelLiters = Decimals.canonical(totalLiters),
                rawTotalCostWon = Decimals.canonical(rawTotal),
                totalCostWon = totalWon,
                precisionPolicy = Decimals.PRECISION_POLICY,
                items = items,
                shares = shares,
            ),
        )
    }

    private fun validateInput(input: SettlementInput): DomainError? {
        if (input.fuelPrice.pricePerLiterWon.signum() <= 0) {
            return DomainError(ErrorCode.INVALID_FUEL_PRICE, "Fuel price must be greater than zero.")
        }
        input.fuelPrice.priceDate?.let {
            try {
                LocalDate.parse(it)
            } catch (e: DateTimeParseException) {
                return DomainError(ErrorCode.INVALID_FUEL_PRICE, "Fuel price date must be yyyy-MM-dd.")
            }
        }
        if (input.headcount !in 1..MAX_HEADCOUNT) {
            return DomainError(ErrorCode.INVALID_HEADCOUNT, "Headcount must be between 1 and $MAX_HEADCOUNT.")
        }
        input.participantNames?.let { names ->
            if (names.size != input.headcount ||
                names.any { it.isBlank() || it.trim().length > MAX_NAME_LENGTH || it.any(Char::isISOControl) }
            ) {
                return DomainError(
                    ErrorCode.INVALID_PARTICIPANTS,
                    "Participant names must match headcount, be 1..$MAX_NAME_LENGTH characters and contain no control characters.",
                )
            }
        }
        return null
    }

    companion object {
        const val MAX_HEADCOUNT = 100
        const val MAX_NAME_LENGTH = 30
    }
}
