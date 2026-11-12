package com.gatita.domain.usecase

import com.gatita.domain.calculator.StopDetector
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.FUEL_SEGMENTS_CALCULATION_VERSION
import com.gatita.domain.model.FuelRules
import com.gatita.domain.model.FuelSnapshot
import com.gatita.domain.model.FuelSnapshotKind
import com.gatita.domain.model.RefuelDetector
import com.gatita.domain.model.RefuelEvent
import com.gatita.domain.model.Trip
import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.UNSAVED_ID
import com.gatita.domain.model.domainFailure
import com.gatita.domain.repository.FuelTrackingRepository
import com.gatita.domain.repository.TransactionRunner
import com.gatita.domain.repository.TripRepository
import com.gatita.domain.vehicle.VehicleDataSource
import com.gatita.domain.vehicle.VehicleReadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Clock

/*
 * 구간별 유류비 방식(fuel-segments-v2)의 차량 측정 기록과 사용자 보정 유스케이스.
 * 측정을 읽지 못해도 기존 기록을 지우거나 0으로 채우지 않고 그 시점의 기록만 건너뛴다.
 */

/**
 * 진행 중 v2 여행의 현재 차량 값을 한 번 읽어 저장한다. 읽지 못하면 null(저장 없음).
 * 직전 측정보다 연료 %가 [FuelRules.REFUEL_PERCENT_THRESHOLD] 이상 오르면 주유 이벤트를 함께 저장한다.
 * 주행거리가 직전 측정보다 줄었으면 잘못된 값이므로 저장하지 않는다.
 */
class RecordFuelSnapshotUseCase(
    private val vehicle: VehicleDataSource,
    private val trips: TripRepository,
    private val fuel: FuelTrackingRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(tripId: Long, forceKind: FuelSnapshotKind? = null): FuelSnapshot? {
        val read = vehicle.readSnapshot()
        if (read !is VehicleReadResult.Success) return null
        val reading = read.snapshot
        return transactions.run {
            val trip = trips.getTrip(tripId)
            if (trip == null || trip.status != TripStatus.IN_PROGRESS ||
                trip.calculationVersion != FUEL_SEGMENTS_CALCULATION_VERSION
            ) {
                return@run null
            }
            val history = fuel.getSnapshots(tripId)
            val last = history.lastOrNull()
            if (reading.odometerKm.signum() < 0) return@run null
            if (last != null && reading.odometerKm < last.odometerKm) return@run null

            val takenAt = clock.millis()
            val draft = FuelSnapshot(
                id = UNSAVED_ID,
                tripId = tripId,
                takenAtMillis = takenAt,
                kind = forceKind ?: if (last == null) FuelSnapshotKind.START else FuelSnapshotKind.PERIODIC,
                odometerKm = reading.odometerKm,
                fuelPercent = reading.fuelPercent,
                efficiency = reading.efficiency,
                parked = reading.parked,
                source = reading.source,
            )
            val kind = if (draft.kind == FuelSnapshotKind.PERIODIC &&
                StopDetector.isNewStop(history, draft, FuelRules.PARK_STOP_SECONDS)
            ) {
                FuelSnapshotKind.STOP
            } else {
                draft.kind
            }
            val saved = fuel.insertSnapshot(draft.copy(kind = kind))

            val before = history.lastOrNull { it.fuelPercent != null }?.fuelPercent
            val after = saved.fuelPercent
            if (before != null && after != null && RefuelDetector.isRefuel(before, after, FuelRules.REFUEL_PERCENT_THRESHOLD)) {
                fuel.insertRefuel(
                    RefuelEvent(
                        id = UNSAVED_ID,
                        tripId = tripId,
                        detectedAtMillis = takenAt,
                        percentBefore = before,
                        percentAfter = after,
                    ),
                )
            }
            saved
        }
    }
}

/**
 * 여행 종료 직전 마지막 측정을 남기고 종료 주행거리로 쓸 값을 돌려준다. 읽지 못하면 마지막 저장 측정의 값, 그것도 없으면 null이다
 * (null이면 종료 유스케이스가 기존 기록 값으로 처리한다. 0으로 대신하지 않는다).
 */
class TakeEndSnapshotUseCase(
    private val record: RecordFuelSnapshotUseCase,
    private val fuel: FuelTrackingRepository,
) {
    suspend operator fun invoke(tripId: Long): BigDecimal? =
        record(tripId, FuelSnapshotKind.END)?.odometerKm ?: fuel.getSnapshots(tripId).lastOrNull()?.odometerKm
}

private fun Trip.editable(): DomainResult.Failure? =
    if (status == TripStatus.SETTLED) domainFailure(ErrorCode.TRIP_LOCKED, "A settled trip cannot be changed.") else null

/** 감지된 주유를 "주유 아님"으로 제외하거나 되돌린다. 확정된 여행은 바꿀 수 없다. */
class SetRefuelDismissedUseCase(
    private val trips: TripRepository,
    private val fuel: FuelTrackingRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(tripId: Long, eventId: Long, dismissed: Boolean): DomainResult<Unit> =
        transactions.run {
            val trip = trips.getTrip(tripId)
                ?: return@run domainFailure(ErrorCode.TRIP_NOT_FOUND, "Trip $tripId not found.")
            trip.editable()?.let { return@run it }
            val event = fuel.getRefuels(tripId).firstOrNull { it.id == eventId }
                ?: return@run domainFailure(ErrorCode.TRIP_NOT_FOUND, "Refuel event $eventId not found.")
            if (event.dismissed != dismissed) fuel.setRefuelDismissed(eventId, dismissed, clock.millis())
            DomainResult.Success(Unit)
        }
}

/** FUEL 비용을 "감지 안 됨"(앱이 주유를 감지하지 못한 주유)으로 표시하거나 되돌린다. */
class SetCostUndetectedUseCase(
    private val trips: TripRepository,
    private val fuel: FuelTrackingRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(tripId: Long, costId: Long, undetected: Boolean): DomainResult<Unit> =
        transactions.run {
            val trip = trips.getTrip(tripId)
                ?: return@run domainFailure(ErrorCode.TRIP_NOT_FOUND, "Trip $tripId not found.")
            trip.editable()?.let { return@run it }
            if (!fuel.setCostUndetected(costId, undetected, clock.millis())) {
                return@run domainFailure(ErrorCode.COST_NOT_FOUND, "Cost $costId not found.")
            }
            DomainResult.Success(Unit)
        }
}

/** 주유가 없는 여행의 직접 입력 단가(원/L). null이면 지운다. 0 이하는 거부한다. 정산을 막지는 않는다. */
class SetManualFuelUnitPriceUseCase(
    private val trips: TripRepository,
    private val fuel: FuelTrackingRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(tripId: Long, price: BigDecimal?): DomainResult<Unit> {
        if (price != null && price.signum() <= 0) {
            return domainFailure(ErrorCode.INVALID_FUEL_PRICE, "Unit price must be positive.")
        }
        return transactions.run {
            val trip = trips.getTrip(tripId)
                ?: return@run domainFailure(ErrorCode.TRIP_NOT_FOUND, "Trip $tripId not found.")
            trip.editable()?.let { return@run it }
            fuel.setManualFuelUnitPrice(tripId, price?.canonical(), clock.millis())
            DomainResult.Success(Unit)
        }
    }
}

class ObserveRefuelsUseCase(private val fuel: FuelTrackingRepository) {
    operator fun invoke(tripId: Long): Flow<List<RefuelEvent>> = fuel.observeRefuels(tripId)
}

/** 측정 저장본으로 본 이동 거리(km)와 경과 시간. 측정이 둘 미만이면 null이며 0으로 보이지 않는다. */
data class FuelProgress(val distanceKm: BigDecimal, val elapsedMillis: Long)

class ObserveFuelProgressUseCase(private val fuel: FuelTrackingRepository) {
    operator fun invoke(tripId: Long): Flow<FuelProgress?> = fuel.observeSnapshots(tripId).map { list ->
        val first = list.firstOrNull()
        val last = list.lastOrNull()
        if (first == null || last == null || first.id == last.id) {
            null
        } else {
            FuelProgress(last.odometerKm.subtract(first.odometerKm).max(BigDecimal.ZERO), (last.takenAtMillis - first.takenAtMillis).coerceAtLeast(0))
        }
    }
}

/**
 * 진행 중 v2 여행이 있는 동안 [intervalMillis]마다 차량 값을 기록한다. 앱이 켜져 있는 동안만 동작한다.
 * 첫 읽기는 START 측정이다. 읽기 실패·예외는 다음 주기에 다시 시도하며 기록을 만들지 않는다.
 */
class FuelRecordingLoop(
    private val activeTrip: Flow<Trip?>,
    private val record: RecordFuelSnapshotUseCase,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
) {
    suspend fun run() {
        activeTrip.collectLatest { trip ->
            if (trip == null || trip.calculationVersion != FUEL_SEGMENTS_CALCULATION_VERSION) return@collectLatest
            while (true) {
                try {
                    record(trip.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 이번 주기 기록만 건너뛴다.
                }
                delay(intervalMillis)
            }
        }
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 60_000L
    }
}
