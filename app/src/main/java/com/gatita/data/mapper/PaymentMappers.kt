package com.gatita.data.mapper

import com.gatita.data.local.entity.CostEntity
import com.gatita.data.local.entity.PaymentSettlementCostEntity
import com.gatita.data.local.entity.PaymentSettlementEntity
import com.gatita.data.local.entity.PaymentSettlementShareEntity
import com.gatita.data.local.entity.TripSummaryRow
import com.gatita.data.local.entity.TripWithCostsRelation
import com.gatita.domain.model.Cost
import com.gatita.domain.model.CostKind
import com.gatita.domain.model.Decimals
import com.gatita.domain.model.MeasurementQuality
import com.gatita.domain.model.PaymentSettlement
import com.gatita.domain.model.PaymentSettlementCost
import com.gatita.domain.model.PaymentShare
import com.gatita.domain.model.RecordingMetadata
import com.gatita.domain.model.SettlementBrief
import com.gatita.domain.model.SettlementKind
import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.TripSummary
import com.gatita.domain.model.TripWithCosts
import com.gatita.domain.model.VehicleSource
import java.math.BigDecimal
import java.time.LocalDate

private fun BigDecimal.text(): String = Decimals.canonical(this).toPlainString()

private fun String.decimal(): BigDecimal = Decimals.parse(this)

// ---- Cost ----

fun Cost.toEntity(): CostEntity = CostEntity(
    id = id,
    tripId = tripId,
    requestId = requestId,
    kind = kind.name,
    amountWon = amountWon,
    description = description,
    unitPrice = unitPriceWonPerLiter?.text(),
    paidOn = paidOn?.toString(),
    included = included,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun CostEntity.toDomain(): Cost = Cost(
    id = id,
    tripId = tripId,
    requestId = requestId,
    kind = CostKind.valueOf(kind),
    amountWon = amountWon,
    description = description,
    unitPriceWonPerLiter = unitPrice?.decimal(),
    paidOn = paidOn?.let(LocalDate::parse),
    included = included,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun TripWithCostsRelation.toDomain(): TripWithCosts = TripWithCosts(
    trip = trip.toDomain(),
    costs = costs.sortedWith(compareBy({ it.createdAtMillis }, { it.id })).map { it.toDomain() },
)

// ---- PaymentSettlement ----

fun PaymentSettlement.toEntity(): PaymentSettlementEntity = PaymentSettlementEntity(
    id = id,
    tripId = tripId,
    requestId = requestId,
    tripName = tripName,
    tripDate = tripDate.toString(),
    tripSource = recording.source.name,
    distanceKm = recording.distanceKm?.text(),
    recordedSeconds = recording.recordedSeconds,
    measurementQuality = recording.quality.name,
    totalWon = totalWon,
    fuelTotalWon = fuelTotalWon,
    otherTotalWon = otherTotalWon,
    people = people,
    includeDriver = includeDriver,
    splitPeople = splitPeople,
    sourceTripRevision = sourceTripRevision,
    calculationVersion = calculationVersion,
    confirmedAtMillis = confirmedAtMillis,
)

fun PaymentSettlementCost.toEntity(settlementId: Long): PaymentSettlementCostEntity = PaymentSettlementCostEntity(
    settlementId = settlementId,
    originalCostId = originalCostId,
    ordinal = ordinal,
    kind = kind.name,
    amountWon = amountWon,
    description = description,
    unitPrice = unitPriceWonPerLiter?.text(),
    paidOn = paidOn?.toString(),
    included = included,
)

fun PaymentShare.toEntity(settlementId: Long): PaymentSettlementShareEntity = PaymentSettlementShareEntity(
    settlementId = settlementId,
    ordinal = ordinal,
    displayName = displayName,
    isDriver = isDriver,
    amountWon = amountWon,
)

fun PaymentSettlementCostEntity.toDomain(): PaymentSettlementCost = PaymentSettlementCost(
    originalCostId = originalCostId,
    ordinal = ordinal,
    kind = CostKind.valueOf(kind),
    amountWon = amountWon,
    description = description,
    unitPriceWonPerLiter = unitPrice?.decimal(),
    paidOn = paidOn?.let(LocalDate::parse),
    included = included,
)

fun PaymentSettlementShareEntity.toDomain(): PaymentShare = PaymentShare(
    ordinal = ordinal,
    displayName = displayName,
    isDriver = isDriver,
    amountWon = amountWon,
)

fun PaymentSettlementEntity.toDomain(
    costs: List<PaymentSettlementCostEntity>,
    shares: List<PaymentSettlementShareEntity>,
): PaymentSettlement = PaymentSettlement(
    id = id,
    tripId = tripId,
    requestId = requestId,
    tripName = tripName,
    tripDate = LocalDate.parse(tripDate),
    recording = RecordingMetadata(
        source = VehicleSource.valueOf(tripSource),
        distanceKm = distanceKm?.decimal(),
        recordedSeconds = recordedSeconds,
        quality = MeasurementQuality.valueOf(measurementQuality),
    ),
    totalWon = totalWon,
    fuelTotalWon = fuelTotalWon,
    otherTotalWon = otherTotalWon,
    people = people,
    includeDriver = includeDriver,
    splitPeople = splitPeople,
    sourceTripRevision = sourceTripRevision,
    calculationVersion = calculationVersion,
    confirmedAtMillis = confirmedAtMillis,
    costs = costs.map { it.toDomain() },
    shares = shares.map { it.toDomain() },
)

// ---- 내역 ----

fun TripSummaryRow.toDomain(): TripSummary {
    val distance = endOdometerKm?.decimal()?.subtract(startOdometerKm.decimal())
    val settlement = when {
        paymentTotalWon != null && paymentConfirmedAtMillis != null ->
            SettlementBrief(SettlementKind.PAYMENTS, paymentTotalWon, paymentConfirmedAtMillis)

        legacyTotalWon != null && legacyConfirmedAtMillis != null ->
            SettlementBrief(SettlementKind.LEGACY_ESTIMATE, legacyTotalWon, legacyConfirmedAtMillis)

        else -> null
    }
    return TripSummary(
        tripId = tripId,
        name = name,
        tripDate = LocalDate.parse(tripDate),
        status = TripStatus.valueOf(status),
        source = VehicleSource.valueOf(source),
        people = people,
        includeDriver = includeDriver,
        revision = revision,
        startedAtMillis = startedAtMillis,
        endedAtMillis = endedAtMillis,
        distanceKm = distance?.takeIf { it.signum() > 0 }?.let(Decimals::canonical),
        costCount = costCount,
        includedCount = includedCount,
        registeredTotalWon = registeredTotalWon,
        includedTotalWon = includedTotalWon,
        settlement = settlement,
    )
}
