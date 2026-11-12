package com.gatita.data.mapper

import com.gatita.data.local.entity.FuelSnapshotEntity
import com.gatita.data.local.entity.RefuelEventEntity
import com.gatita.domain.model.Decimals
import com.gatita.domain.model.EfficiencyReading
import com.gatita.domain.model.EfficiencyScope
import com.gatita.domain.model.EfficiencyUnit
import com.gatita.domain.model.FuelSnapshot
import com.gatita.domain.model.FuelSnapshotKind
import com.gatita.domain.model.RefuelEvent
import com.gatita.domain.model.VehicleSource
import java.math.BigDecimal

private fun BigDecimal.text(): String = Decimals.canonical(this).toPlainString()

private fun String.decimal(): BigDecimal = Decimals.parse(this)

fun FuelSnapshot.toEntity(): FuelSnapshotEntity = FuelSnapshotEntity(
    id = id,
    tripId = tripId,
    takenAtMillis = takenAtMillis,
    kind = kind.name,
    odometerKm = odometerKm.text(),
    fuelPercent = fuelPercent,
    efficiencyValue = efficiency?.value?.text(),
    efficiencyUnit = efficiency?.unit?.name,
    efficiencyScope = efficiency?.scope?.name,
    aggregationKey = efficiency?.aggregationKey,
    cumulativeDistanceKm = efficiency?.cumulativeDistanceKm?.text(),
    parked = parked,
    source = source.name,
)

fun FuelSnapshotEntity.toDomain(): FuelSnapshot = FuelSnapshot(
    id = id,
    tripId = tripId,
    takenAtMillis = takenAtMillis,
    kind = FuelSnapshotKind.valueOf(kind),
    odometerKm = odometerKm.decimal(),
    fuelPercent = fuelPercent,
    efficiency = efficiencyValue?.let {
        EfficiencyReading(
            value = it.decimal(),
            unit = EfficiencyUnit.valueOf(checkNotNull(efficiencyUnit)),
            scope = EfficiencyScope.valueOf(checkNotNull(efficiencyScope)),
            aggregationKey = aggregationKey,
            cumulativeDistanceKm = cumulativeDistanceKm?.decimal(),
        )
    },
    parked = parked,
    source = VehicleSource.valueOf(source),
)

fun RefuelEvent.toEntity(): RefuelEventEntity = RefuelEventEntity(
    id = id,
    tripId = tripId,
    detectedAtMillis = detectedAtMillis,
    percentBefore = percentBefore,
    percentAfter = percentAfter,
    dismissed = dismissed,
)

fun RefuelEventEntity.toDomain(): RefuelEvent = RefuelEvent(
    id = id,
    tripId = tripId,
    detectedAtMillis = detectedAtMillis,
    percentBefore = percentBefore,
    percentAfter = percentAfter,
    dismissed = dismissed,
)
