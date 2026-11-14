package com.gatita.data.mapper

import com.gatita.data.local.entity.FuelPriceEntity
import com.gatita.data.local.entity.SegmentEntity
import com.gatita.data.local.entity.SettlementEntity
import com.gatita.data.local.entity.SettlementItemEntity
import com.gatita.data.local.entity.SettlementShareEntity
import com.gatita.data.local.entity.TripEntity
import com.gatita.domain.model.CalculationMethod
import com.gatita.domain.model.Decimals
import com.gatita.domain.model.EfficiencyReading
import com.gatita.domain.model.EfficiencyScope
import com.gatita.domain.model.EfficiencyUnit
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.FuelPrice
import com.gatita.domain.model.FuelType
import com.gatita.domain.model.PriceSource
import com.gatita.domain.model.Segment
import com.gatita.domain.model.Settlement
import com.gatita.domain.model.SettlementCalculation
import com.gatita.domain.model.SettlementItem
import com.gatita.domain.model.SettlementShare
import com.gatita.domain.model.Trip
import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.VehicleSource
import java.math.BigDecimal
import java.time.LocalDate

private fun BigDecimal.text(): String = Decimals.canonical(this).toPlainString()

private fun String.decimal(): BigDecimal = Decimals.parse(this)

// ---- Trip ----

fun Trip.toEntity(): TripEntity = TripEntity(
    id = id,
    status = status.name,
    source = source.name,
    startOdometerKm = startOdometerKm.text(),
    startedAtMillis = startedAtMillis,
    endOdometerKm = endOdometerKm?.text(),
    endedAtMillis = endedAtMillis,
    activeSlot = if (status == TripStatus.IN_PROGRESS) ACTIVE_SLOT else null,
    name = name,
    tripDate = tripDate.toString(),
    zoneId = zoneId,
    vehicleLabel = vehicleLabel,
    people = people,
    includeDriver = includeDriver,
    revision = revision,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    calculationVersion = calculationVersion,
    manualFuelUnitPrice = manualFuelUnitPrice?.text(),
)

fun TripEntity.toDomain(): Trip = Trip(
    id = id,
    status = TripStatus.valueOf(status),
    source = VehicleSource.valueOf(source),
    startOdometerKm = startOdometerKm.decimal(),
    startedAtMillis = startedAtMillis,
    endOdometerKm = endOdometerKm?.decimal(),
    endedAtMillis = endedAtMillis,
    name = name,
    tripDate = LocalDate.parse(tripDate),
    zoneId = zoneId,
    vehicleLabel = vehicleLabel,
    people = people,
    includeDriver = includeDriver,
    revision = revision,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    calculationVersion = calculationVersion,
    manualFuelUnitPrice = manualFuelUnitPrice?.decimal(),
)

const val ACTIVE_SLOT = 1

// ---- Segment ----

fun Segment.toEntity(): SegmentEntity = SegmentEntity(
    id = id,
    tripId = tripId,
    seq = seq,
    eventId = eventId,
    startOdometerKm = startOdometerKm.text(),
    endOdometerKm = endOdometerKm.text(),
    startedAtMillis = startedAtMillis,
    endedAtMillis = endedAtMillis,
    efficiencyValue = efficiency?.value?.text(),
    efficiencyUnit = efficiency?.unit?.name,
    efficiencyScope = efficiency?.scope?.name,
    aggregationKey = efficiency?.aggregationKey,
    cumulativeDistanceKm = efficiency?.cumulativeDistanceKm?.text(),
    baselineEfficiencyValue = baseline?.value?.text(),
    baselineCumulativeDistanceKm = baseline?.cumulativeDistanceKm?.text(),
    source = source.name,
)

fun SegmentEntity.toDomain(): Segment {
    val end = efficiencyValue?.let {
        EfficiencyReading(
            value = it.decimal(),
            unit = EfficiencyUnit.valueOf(checkNotNull(efficiencyUnit)),
            scope = EfficiencyScope.valueOf(checkNotNull(efficiencyScope)),
            aggregationKey = aggregationKey,
            cumulativeDistanceKm = cumulativeDistanceKm?.decimal(),
        )
    }
    val base = baselineEfficiencyValue?.let {
        val reference = checkNotNull(end) { "baseline without efficiency in segment $id" }
        reference.copy(value = it.decimal(), cumulativeDistanceKm = baselineCumulativeDistanceKm?.decimal())
    }
    return Segment(
        id = id,
        tripId = tripId,
        seq = seq,
        eventId = eventId,
        startOdometerKm = startOdometerKm.decimal(),
        endOdometerKm = endOdometerKm.decimal(),
        startedAtMillis = startedAtMillis,
        endedAtMillis = endedAtMillis,
        efficiency = end,
        baseline = base,
        source = VehicleSource.valueOf(source),
    )
}

// ---- Settlement ----

fun FuelPrice.toEntity(): FuelPriceEntity = FuelPriceEntity(
    fuelType = type.name,
    pricePerLiterWon = pricePerLiterWon.text(),
    priceDate = priceDate,
    source = source.name,
)

fun FuelPriceEntity.toDomain(): FuelPrice = FuelPrice(
    type = FuelType.valueOf(fuelType),
    pricePerLiterWon = pricePerLiterWon.decimal(),
    priceDate = priceDate,
    source = PriceSource.valueOf(source),
)

fun Settlement.toEntity(fuelPriceId: Long): SettlementEntity = SettlementEntity(
    id = id,
    tripId = tripId,
    requestId = requestId,
    method = calculation.method.name,
    fallbackReasons = calculation.fallbackReasons.joinToString(",") { it.name },
    fuelPriceId = fuelPriceId,
    headcount = calculation.headcount,
    totalFuelLiters = calculation.totalFuelLiters.text(),
    rawTotalCostWon = calculation.rawTotalCostWon.text(),
    totalCostWon = calculation.totalCostWon,
    precisionPolicy = calculation.precisionPolicy,
    confirmedAtMillis = confirmedAtMillis,
)

fun SettlementItem.toEntity(settlementId: Long): SettlementItemEntity = SettlementItemEntity(
    settlementId = settlementId,
    itemIndex = index,
    segmentSeq = segmentSeq,
    distanceKm = distanceKm.text(),
    efficiencyValue = efficiency.value.text(),
    efficiencyUnit = efficiency.unit.name,
    efficiencyScope = efficiency.scope.name,
    aggregationKey = efficiency.aggregationKey,
    cumulativeDistanceKm = efficiency.cumulativeDistanceKm?.text(),
    baselineEfficiencyValue = baseline?.value?.text(),
    baselineCumulativeDistanceKm = baseline?.cumulativeDistanceKm?.text(),
    fuelLiters = fuelLiters.text(),
    unitPriceWonPerLiter = unitPriceWonPerLiter.text(),
    costWon = costWon.text(),
    source = source.name,
)

fun SettlementItemEntity.toDomain(): SettlementItem {
    val end = EfficiencyReading(
        value = efficiencyValue.decimal(),
        unit = EfficiencyUnit.valueOf(efficiencyUnit),
        scope = EfficiencyScope.valueOf(efficiencyScope),
        aggregationKey = aggregationKey,
        cumulativeDistanceKm = cumulativeDistanceKm?.decimal(),
    )
    val base = baselineEfficiencyValue?.let {
        end.copy(value = it.decimal(), cumulativeDistanceKm = baselineCumulativeDistanceKm?.decimal())
    }
    return SettlementItem(
        index = itemIndex,
        segmentSeq = segmentSeq,
        distanceKm = distanceKm.decimal(),
        efficiency = end,
        baseline = base,
        fuelLiters = fuelLiters.decimal(),
        unitPriceWonPerLiter = unitPriceWonPerLiter.decimal(),
        costWon = costWon.decimal(),
        source = VehicleSource.valueOf(source),
    )
}

fun SettlementShare.toEntity(settlementId: Long): SettlementShareEntity = SettlementShareEntity(
    settlementId = settlementId,
    participantIndex = participantIndex,
    participantName = participantName,
    amountWon = amountWon,
)

fun SettlementShareEntity.toDomain(): SettlementShare = SettlementShare(
    participantIndex = participantIndex,
    participantName = participantName,
    amountWon = amountWon,
)

fun SettlementEntity.toDomain(
    price: FuelPriceEntity,
    items: List<SettlementItemEntity>,
    shares: List<SettlementShareEntity>,
): Settlement = Settlement(
    id = id,
    tripId = tripId,
    requestId = requestId,
    confirmedAtMillis = confirmedAtMillis,
    calculation = SettlementCalculation(
        method = CalculationMethod.valueOf(method),
        fallbackReasons = if (fallbackReasons.isEmpty()) {
            emptyList()
        } else {
            fallbackReasons.split(",").map { ErrorCode.valueOf(it) }
        },
        fuelPrice = price.toDomain(),
        headcount = headcount,
        totalFuelLiters = totalFuelLiters.decimal(),
        rawTotalCostWon = rawTotalCostWon.decimal(),
        totalCostWon = totalCostWon,
        precisionPolicy = precisionPolicy,
        items = items.map { it.toDomain() },
        shares = shares.map { it.toDomain() },
    ),
)
