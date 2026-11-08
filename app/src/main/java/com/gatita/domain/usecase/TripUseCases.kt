package com.gatita.domain.usecase

import com.gatita.domain.calculator.CostValidator
import com.gatita.domain.model.Decimals
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.EfficiencyMath
import com.gatita.domain.model.EfficiencyReading
import com.gatita.domain.model.EfficiencyScope
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.Segment
import com.gatita.domain.model.Trip
import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.UNSAVED_ID
import com.gatita.domain.model.VehicleSource
import com.gatita.domain.model.domainFailure
import com.gatita.domain.repository.TransactionRunner
import com.gatita.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

const val MAX_ID_LENGTH = 128
internal const val MAX_VEHICLE_LABEL_LENGTH = 50

internal fun BigDecimal.canonical(): BigDecimal = Decimals.canonical(this)

internal fun EfficiencyReading.canonical(): EfficiencyReading = copy(
    value = value.canonical(),
    aggregationKey = aggregationKey?.trim(),
    cumulativeDistanceKm = cumulativeDistanceKm?.canonical(),
)

data class StartTripCommand(
    val startOdometerKm: BigDecimal,
    val startedAtMillis: Long,
    val source: VehicleSource,
    /** 비어 있으면 [com.gatita.domain.model.DEFAULT_TRIP_NAME]. 최대 32자. */
    val name: String? = null,
    /** 운전자 포함 인원 1..8. 이후 종료된 여행에서 [UpdateSplitPolicyUseCase]로 바꿀 수 있다. */
    val people: Int = 1,
    val includeDriver: Boolean = true,
    val vehicleLabel: String = "",
)

/**
 * 진행 중 여행이 있으면 새로 시작하지 않고 [ErrorCode.ACTIVE_TRIP_EXISTS]를 돌려준다. 화면은 기존 여행을 복구한다.
 * 여행 날짜는 시작 시각을 [zone]에서 해석한 날짜로 고정된다.
 */
class StartTripUseCase(
    private val trips: TripRepository,
    private val transactions: TransactionRunner,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend operator fun invoke(command: StartTripCommand): DomainResult<Trip> {
        if (command.startOdometerKm.signum() < 0) {
            return domainFailure(ErrorCode.INVALID_ODOMETER, "Odometer must not be negative.")
        }
        if (command.startedAtMillis <= 0) {
            return domainFailure(ErrorCode.INVALID_TIMESTAMP, "Start time is invalid.")
        }
        val name = when (val result = CostValidator.normalizeTripName(command.name)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        val (people, includeDriver) = when (val result = CostValidator.normalizeSplitPolicy(command.people, command.includeDriver)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        val vehicleLabel = command.vehicleLabel.trim()
        if (vehicleLabel.length > MAX_VEHICLE_LABEL_LENGTH || vehicleLabel.any(Char::isISOControl)) {
            return domainFailure(ErrorCode.INVALID_TRIP_NAME, "Vehicle label is invalid.", "vehicleLabel")
        }
        return transactions.run {
            if (trips.getActiveTrip() != null) {
                domainFailure(ErrorCode.ACTIVE_TRIP_EXISTS, "An in-progress trip already exists.")
            } else {
                DomainResult.Success(
                    trips.insertTrip(
                        Trip(
                            id = UNSAVED_ID,
                            status = TripStatus.IN_PROGRESS,
                            source = command.source,
                            startOdometerKm = command.startOdometerKm.canonical(),
                            startedAtMillis = command.startedAtMillis,
                            endOdometerKm = null,
                            endedAtMillis = null,
                            name = name,
                            tripDate = Instant.ofEpochMilli(command.startedAtMillis).atZone(zone).toLocalDate(),
                            zoneId = zone.id,
                            vehicleLabel = vehicleLabel,
                            people = people,
                            includeDriver = includeDriver,
                            revision = 0,
                            createdAtMillis = command.startedAtMillis,
                            updatedAtMillis = command.startedAtMillis,
                        ),
                    ),
                )
            }
        }
    }
}

/** 앱 재실행 시 진행 중 여행 복구와 과거 내역 조회. 값은 항상 Room에서 읽는다. */
class RestoreActiveTripUseCase(private val trips: TripRepository) {
    suspend operator fun invoke(): Trip? = trips.getActiveTrip()

    fun observe(): Flow<Trip?> = trips.observeActiveTrip()
}

/**
 * 구간 저장 요청.
 *
 * [eventId]는 같은 구간 이벤트(예: 시동 OFF 알림)를 식별하는 호출자 생성 ID다. 같은 ID로 다시 저장하면 새 기록을 만들지 않는다.
 * [startOdometerKm]/[startedAtMillis]가 null이면 직전 구간(또는 여행 시작)의 끝을 이어받는다.
 * [efficiency]가 null이면 연비 측정 실패 구간으로 저장하며 0으로 바꾸지 않는다.
 */
data class SaveSegmentCommand(
    val tripId: Long,
    val eventId: String,
    val endOdometerKm: BigDecimal,
    val endedAtMillis: Long,
    val efficiency: EfficiencyReading?,
    val baseline: EfficiencyReading? = null,
    val source: VehicleSource,
    val startOdometerKm: BigDecimal? = null,
    val startedAtMillis: Long? = null,
)

class SaveSegmentUseCase(
    private val trips: TripRepository,
    private val transactions: TransactionRunner,
) {
    suspend operator fun invoke(command: SaveSegmentCommand): DomainResult<Segment> {
        val eventId = command.eventId.trim()
        if (eventId.isEmpty() || eventId.length > MAX_ID_LENGTH) {
            return domainFailure(ErrorCode.INVALID_EVENT_ID, "Event id must be 1..$MAX_ID_LENGTH characters.")
        }
        return transactions.run { save(command, eventId) }
    }

    private suspend fun save(command: SaveSegmentCommand, eventId: String): DomainResult<Segment> {
        val trip = trips.getTrip(command.tripId)
            ?: return domainFailure(ErrorCode.TRIP_NOT_FOUND, "Trip ${command.tripId} not found.")

        val end = command.endOdometerKm.canonical()
        val efficiency = command.efficiency?.canonical()
        val baseline = command.baseline?.canonical()

        // 중복 이벤트: 같은 내용이면 기존 기록을 돌려주고, 다른 내용이면 충돌로 거부한다.
        trips.findSegmentByEventId(trip.id, eventId)?.let { existing ->
            val sameContent = existing.endOdometerKm == end &&
                existing.endedAtMillis == command.endedAtMillis &&
                existing.efficiency == efficiency &&
                existing.baseline == baseline &&
                existing.source == command.source &&
                (command.startOdometerKm == null || existing.startOdometerKm == command.startOdometerKm.canonical()) &&
                (command.startedAtMillis == null || existing.startedAtMillis == command.startedAtMillis)
            return if (sameContent) {
                DomainResult.Success(existing)
            } else {
                domainFailure(ErrorCode.SEGMENT_CONFLICT, "Event id was already used with different segment data.")
            }
        }

        if (trip.status != TripStatus.IN_PROGRESS) {
            return domainFailure(ErrorCode.TRIP_NOT_IN_PROGRESS, "Segments can only be added to an in-progress trip.")
        }

        val existing = trips.getSegments(trip.id)
        val last = existing.maxByOrNull { it.seq }
        val previousEnd = last?.endOdometerKm ?: trip.startOdometerKm
        val previousEndTime = last?.endedAtMillis ?: trip.startedAtMillis

        val start = command.startOdometerKm?.canonical() ?: previousEnd
        val startedAt = command.startedAtMillis ?: previousEndTime

        if (start.signum() < 0 || end.signum() < 0) {
            return domainFailure(ErrorCode.INVALID_ODOMETER, "Odometer must not be negative.")
        }
        if (start < previousEnd) {
            return domainFailure(ErrorCode.SEGMENT_OVERLAP, "Segment starts before the previous segment ended.")
        }
        if (end < start) {
            return domainFailure(ErrorCode.ODOMETER_DECREASED, "End odometer is lower than the start odometer.")
        }
        if (end == start) {
            return domainFailure(ErrorCode.INVALID_DISTANCE, "Segment distance must be greater than zero.")
        }
        if (startedAt < previousEndTime || command.endedAtMillis < startedAt) {
            return domainFailure(ErrorCode.INVALID_TIMESTAMP, "Segment times must not go backwards.")
        }

        validateEfficiency(end.subtract(start), efficiency, baseline)?.let { return it }

        val saved = trips.appendSegment(
            Segment(
                id = UNSAVED_ID,
                tripId = trip.id,
                seq = (last?.seq ?: 0) + 1,
                eventId = eventId,
                startOdometerKm = start,
                endOdometerKm = end,
                startedAtMillis = startedAt,
                endedAtMillis = command.endedAtMillis,
                efficiency = efficiency,
                baseline = baseline,
                source = command.source,
            ),
        )
        return DomainResult.Success(saved)
    }

    private fun validateEfficiency(
        distanceKm: BigDecimal,
        efficiency: EfficiencyReading?,
        baseline: EfficiencyReading?,
    ): DomainResult.Failure? {
        if (efficiency == null) {
            return if (baseline != null) {
                domainFailure(ErrorCode.AGGREGATION_MISMATCH, "A failed measurement must not carry a baseline.")
            } else {
                null
            }
        }
        if (efficiency.scope == EfficiencyScope.VEHICLE_REFERENCE) {
            return domainFailure(
                ErrorCode.INVALID_EFFICIENCY_SCOPE,
                "Vehicle reference efficiency cannot be stored on a segment.",
            )
        }
        // 알 수 없는 집계 기준은 원본을 보존하되 값 자체는 검증한다.
        if (efficiency.scope == EfficiencyScope.UNKNOWN) {
            return EfficiencyMath.validate(efficiency)?.let { DomainResult.Failure(it) }
                ?: if (baseline != null) {
                    domainFailure(ErrorCode.AGGREGATION_MISMATCH, "Unknown-scope efficiency must not carry a baseline.")
                } else {
                    null
                }
        }
        return EfficiencyMath.segmentFuelLiters(distanceKm, efficiency, baseline) as? DomainResult.Failure
    }
}

data class FinishTripCommand(
    val tripId: Long,
    val endOdometerKm: BigDecimal,
    val endedAtMillis: Long,
)

/**
 * 여행 종료. 마지막 구간 이후 거리를 구간으로 저장하지 않았다면 빈 구간으로 남으며, 정산 단계에서 누락으로 보고된다.
 * 이미 같은 값으로 종료된 여행에 다시 호출하면 그대로 성공한다.
 */
class FinishTripUseCase(
    private val trips: TripRepository,
    private val transactions: TransactionRunner,
) {
    suspend operator fun invoke(command: FinishTripCommand): DomainResult<Trip> =
        transactions.run { finish(command) }

    private suspend fun finish(command: FinishTripCommand): DomainResult<Trip> {
        val trip = trips.getTrip(command.tripId)
            ?: return domainFailure(ErrorCode.TRIP_NOT_FOUND, "Trip ${command.tripId} not found.")
        val end = command.endOdometerKm.canonical()

        if (trip.status != TripStatus.IN_PROGRESS) {
            return if (trip.endOdometerKm == end && trip.endedAtMillis == command.endedAtMillis) {
                DomainResult.Success(trip)
            } else {
                domainFailure(ErrorCode.TRIP_NOT_IN_PROGRESS, "Trip is already finished.")
            }
        }

        val last = trips.getSegments(trip.id).maxByOrNull { it.seq }
        val floor = last?.endOdometerKm ?: trip.startOdometerKm
        val floorTime = last?.endedAtMillis ?: trip.startedAtMillis

        if (end.signum() < 0) {
            return domainFailure(ErrorCode.INVALID_ODOMETER, "Odometer must not be negative.")
        }
        if (end < floor) {
            return domainFailure(ErrorCode.ODOMETER_DECREASED, "End odometer is lower than the last recorded odometer.")
        }
        if (end == trip.startOdometerKm) {
            return domainFailure(ErrorCode.INVALID_DISTANCE, "Trip distance must be greater than zero.")
        }
        if (command.endedAtMillis < floorTime) {
            return domainFailure(ErrorCode.INVALID_TIMESTAMP, "End time is earlier than the last recorded time.")
        }
        return DomainResult.Success(trips.markFinished(trip.id, end, command.endedAtMillis))
    }
}
