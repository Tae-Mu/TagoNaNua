package com.gatita.feature.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gatita.di.AppContainer
import com.gatita.domain.model.Cost
import com.gatita.domain.model.FUEL_SEGMENTS_CALCULATION_VERSION
import com.gatita.domain.model.CostLedger
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.RefuelEvent
import com.gatita.domain.model.Segment
import com.gatita.domain.model.Trip
import com.gatita.domain.model.TripStatus
import com.gatita.domain.repository.TripLedgerRepository
import com.gatita.domain.repository.TripRepository
import com.gatita.domain.usecase.CompleteTripCommand
import com.gatita.domain.usecase.CompleteTripUseCase
import com.gatita.domain.usecase.DeleteCostUseCase
import com.gatita.domain.usecase.DiscardEmptyTripUseCase
import com.gatita.domain.usecase.ObserveCostLedgerUseCase
import com.gatita.domain.usecase.ObserveFuelProgressUseCase
import com.gatita.domain.usecase.ObserveRefuelsUseCase
import com.gatita.domain.usecase.SetCostUndetectedUseCase
import com.gatita.domain.usecase.SetRefuelDismissedUseCase
import com.gatita.domain.usecase.TakeEndSnapshotUseCase
import com.gatita.domain.usecase.SetCostIncludedUseCase
import com.gatita.feature.common.AttemptStore
import com.gatita.feature.common.Formatters
import com.gatita.feature.common.LOAD_FAILURE_MESSAGE
import com.gatita.feature.common.SavedStateAttemptStore
import com.gatita.feature.common.SubmitGuard
import com.gatita.feature.common.UNEXPECTED_FAILURE_MESSAGE
import com.gatita.feature.common.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

data class SegmentRow(
    val seq: Int,
    val range: String,
    val distance: String,
    /** 연비를 기록하지 않은 구간은 "연비 미기록"으로 표시하며 0으로 보이지 않는다. */
    val efficiency: String,
    val source: String,
)

data class TripUiState(
    val isLoading: Boolean = true,
    val pausedAt: Long? = null,
    val isChangingPause: Boolean = false,
    val pauseError: String? = null,
    val loadError: String? = null,
    val notFound: Boolean = false,
    val trip: Trip? = null,
    val ledger: CostLedger? = null,
    val segments: List<SegmentRow> = emptyList(),
    /** 구간별 유류비 여행의 측정 기반 이동 거리·시간(구간 기록이 없을 때의 표시 값). */
    val measuredDistance: String? = null,
    val measuredMinutes: Long? = null,
    /** 감지된 주유(제외한 것 포함). 구간별 유류비 방식 여행에서만 쓴다. */
    val refuels: List<RefuelEvent> = emptyList(),
    val lastRecordedKm: String? = null,
    val recordedDistance: String? = null,
    val recordedMinutes: Long? = null,

    /** 비용 포함 선택·삭제처럼 목록을 바꾸는 작업이 진행 중이다. */
    val isMutating: Boolean = false,
    val costError: String? = null,
    /** 삭제 확인 창의 대상. */
    val deleteTarget: Cost? = null,


    val showCompleteConfirm: Boolean = false,
    val isCompleting: Boolean = false,
    val completeError: String? = null,

    val showDiscardConfirm: Boolean = false,
    val isDiscarding: Boolean = false,
    val discardError: String? = null,
)

sealed interface TripEvent {
    data class OpenSettlement(val tripId: Long) : TripEvent

    /** 빈 여행을 닫았다. 화면은 홈으로 돌아간다. */
    data object Discarded : TripEvent
}

class TripViewModel(
    private val tripId: Long,
    private val trips: TripRepository,
    private val ledgerRepository: TripLedgerRepository,
    private val observeCostLedger: ObserveCostLedgerUseCase,
    private val setCostIncluded: SetCostIncludedUseCase,
    private val deleteCost: DeleteCostUseCase,
    private val completeTripUseCase: CompleteTripUseCase,
    private val discardEmptyTripUseCase: DiscardEmptyTripUseCase,
    private val clock: Clock,
    private val attempts: AttemptStore,
    private val sessions: com.gatita.domain.repository.TripSessionRepository? = null,
    private val setPaused: com.gatita.domain.usecase.SetTripPausedUseCase? = null,
    private val observeRefuels: ObserveRefuelsUseCase? = null,
    private val setRefuelDismissed: SetRefuelDismissedUseCase? = null,
    private val setCostUndetected: SetCostUndetectedUseCase? = null,
    private val takeEndSnapshot: TakeEndSnapshotUseCase? = null,
    private val observeFuelProgress: ObserveFuelProgressUseCase? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(TripUiState())
    val state: StateFlow<TripUiState> = _state.asStateFlow()

    private val _events = Channel<TripEvent>(Channel.BUFFERED)
    val events: Flow<TripEvent> = _events.receiveAsFlow()

    private val mutateGuard = SubmitGuard()
    private val completeGuard = SubmitGuard()
    private val discardGuard = SubmitGuard()
    private var observeJob: Job? = null

    init {
        observe()
        observeFuelProgress?.let { observeUseCase ->
            viewModelScope.launch {
                observeUseCase(tripId)
                    .catch { /* 거리 표시만 못 갱신한다. 기록은 그대로다. */ }
                    .collect { progress ->
                        _state.update {
                            val distance = progress?.distanceKm?.let(Formatters::km)
                            val minutes = progress?.let { p -> p.elapsedMillis / 60000 }
                            it.copy(
                                measuredDistance = distance,
                                measuredMinutes = minutes,
                                // 구간 기록이 있으면 그 값을 우선한다.
                                recordedDistance = if (it.segments.isEmpty()) distance else it.recordedDistance,
                                recordedMinutes = if (it.segments.isEmpty()) minutes else it.recordedMinutes,
                            )
                        }
                    }
            }
        }
        observeRefuels?.let { observeUseCase ->
            viewModelScope.launch {
                observeUseCase(tripId)
                    .catch { _state.update { it.copy(costError = LOAD_FAILURE_MESSAGE) } }
                    .collect { list -> _state.update { it.copy(refuels = list) } }
            }
        }
        sessions?.let { repo -> viewModelScope.launch {
            repo.observePausedAt(tripId).catch { _state.update { it.copy(pauseError="일시정지 상태를 불러오지 못했어요.") } }
                .collect { at -> _state.update { it.copy(pausedAt=at) } }
        } }
    }

    fun changePaused(paused: Boolean) {
        val action=setPaused ?: return
        if(_state.value.isChangingPause || _state.value.isCompleting) return
        _state.update { it.copy(isChangingPause=true,pauseError=null) }
        viewModelScope.launch {
            try { when(val result=action(tripId,paused)) {
                is DomainResult.Success -> Unit
                is DomainResult.Failure -> _state.update { it.copy(pauseError=result.error.code.userMessage()) }
            } } catch(e:CancellationException) { throw e }
            catch(e:Exception) { _state.update { it.copy(pauseError="상태를 저장하지 못했어요. 다시 시도해 주세요.") } }
            finally { _state.update { it.copy(isChangingPause=false) } }
        }
    }

    /** 여행과 비용을 Room에서 구독한다. 비용 화면에서 돌아오면 자동으로 갱신된다. */
    fun observe() {
        observeJob?.cancel()
        _state.update { it.copy(isLoading = true, loadError = null) }
        observeJob = viewModelScope.launch {
            combine(ledgerRepository.observeTripWithCosts(tripId), observeCostLedger(tripId)) { withCosts, ledger ->
                withCosts?.trip to ledger
            }
                .catch { _state.update { it.copy(isLoading = false, loadError = LOAD_FAILURE_MESSAGE) } }
                .collect { (trip, ledger) -> apply(trip, ledger) }
        }
    }

    private suspend fun apply(trip: Trip?, ledger: DomainResult<CostLedger>) {
        if (trip == null) {
            _state.update { it.copy(isLoading = false, notFound = true, trip = null, ledger = null) }
            return
        }
        if (ledger !is DomainResult.Success) {
            _state.update { it.copy(isLoading = false, loadError = LOAD_FAILURE_MESSAGE) }
            return
        }
        val segments = try {
            trips.getSegments(tripId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, loadError = LOAD_FAILURE_MESSAGE) }
            return
        }
        _state.update {
            it.copy(
                isLoading = false,
                loadError = null,
                notFound = false,
                trip = trip,
                ledger = ledger.value,
                segments = segments.map(::toRow),
                recordedDistance = segments.takeIf { it.isNotEmpty() }?.sumOf { it.distanceKm }?.let(Formatters::km) ?: it.measuredDistance,
                recordedMinutes = segments.takeIf { it.isNotEmpty() }?.sumOf { (it.endedAtMillis-it.startedAtMillis).coerceAtLeast(0) }?.div(60000) ?: it.measuredMinutes,
                lastRecordedKm = segments.maxByOrNull { s -> s.seq }?.endOdometerKm?.let(Formatters::km),
            )
        }
    }

    private fun toRow(segment: Segment): SegmentRow {
        val efficiency = segment.efficiency
        return SegmentRow(
            seq = segment.seq,
            range = Formatters.km(segment.startOdometerKm) + " → " + Formatters.km(segment.endOdometerKm),
            distance = Formatters.km(segment.distanceKm),
            efficiency = if (efficiency == null) {
                "연비 미기록"
            } else {
                Formatters.efficiency(efficiency) + " (" + Formatters.scopeLabel(efficiency.scope) + ")"
            },
            source = Formatters.source(segment.source),
        )
    }

    // ---- 비용 목록 조작 ----

    /** 토글이 아니라 원하는 절대값을 보낸다. 실패하면 목록은 저장된 값 그대로다. */
    fun setIncluded(cost: Cost, included: Boolean) {
        if (!mutateGuard.tryAcquire()) return
        _state.update { it.copy(isMutating = true, costError = null) }
        viewModelScope.launch {
            try {
                when (val result = setCostIncluded(tripId, cost.id, included)) {
                    is DomainResult.Success -> Unit
                    is DomainResult.Failure -> _state.update { it.copy(costError = result.error.code.userMessage()) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(costError = UNEXPECTED_FAILURE_MESSAGE) }
            } finally {
                _state.update { it.copy(isMutating = false) }
                mutateGuard.release()
            }
        }
    }

    /** 감지된 주유를 "주유 아님"으로 제외하거나 되돌린다. 제외하면 구간이 합쳐지고 짝짓기에서 빠진다. */
    fun setRefuelDismissed(event: RefuelEvent, dismissed: Boolean) {
        val action = setRefuelDismissed ?: return
        runMutation { action(tripId, event.id, dismissed) }
    }

    /** FUEL 비용을 "감지 안 됨"(앱이 감지하지 못한 주유)으로 표시하거나 되돌린다. */
    fun setUndetected(cost: Cost, undetected: Boolean) {
        val action = setCostUndetected ?: return
        runMutation { action(tripId, cost.id, undetected) }
    }

    private fun runMutation(block: suspend () -> DomainResult<Unit>) {
        if (!mutateGuard.tryAcquire()) return
        _state.update { it.copy(isMutating = true, costError = null) }
        viewModelScope.launch {
            try {
                when (val result = block()) {
                    is DomainResult.Success -> Unit
                    is DomainResult.Failure -> _state.update { it.copy(costError = result.error.code.userMessage()) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(costError = UNEXPECTED_FAILURE_MESSAGE) }
            } finally {
                _state.update { it.copy(isMutating = false) }
                mutateGuard.release()
            }
        }
    }

    fun requestDelete(cost: Cost) = _state.update { it.copy(deleteTarget = cost, costError = null) }

    fun dismissDelete() = _state.update { it.copy(deleteTarget = null) }

    fun confirmDelete() {
        val target = _state.value.deleteTarget ?: return
        if (!mutateGuard.tryAcquire()) return
        _state.update { it.copy(deleteTarget = null, isMutating = true, costError = null) }
        viewModelScope.launch {
            try {
                when (val result = deleteCost(tripId, target.id)) {
                    is DomainResult.Success -> Unit
                    is DomainResult.Failure -> _state.update { it.copy(costError = result.error.code.userMessage()) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(costError = UNEXPECTED_FAILURE_MESSAGE) }
            } finally {
                _state.update { it.copy(isMutating = false) }
                mutateGuard.release()
            }
        }
    }

    // ---- 여행 마치기. 거리 기록이 없는 0km 여행도 비용이 있으면 마칠 수 있다. ----

    fun requestComplete() {
        val trip = _state.value.trip ?: return
        if (trip.status != TripStatus.IN_PROGRESS || _state.value.isCompleting) return
        _state.update { it.copy(showCompleteConfirm = true, completeError = null) }
    }

    fun dismissComplete() = _state.update { it.copy(showCompleteConfirm = false) }

    fun confirmComplete() {
        if (!completeGuard.tryAcquire()) return
        val atMillis = attempts.get(KEY_COMPLETE_AT)?.toLongOrNull() ?: clock.millis().also {
            attempts.put(KEY_COMPLETE_AT, it.toString())
        }
        _state.update { it.copy(showCompleteConfirm = false, isCompleting = true, completeError = null) }
        viewModelScope.launch {
            try {
                // 구간별 유류비 여행은 종료 직전 측정을 남기고 그 주행거리를 종료 값으로 쓴다. 읽지 못하면 마지막 기록 값이며 0으로 대신하지 않는다.
                // 그 밖에는 종료 주행거리를 넘기지 않아 마지막으로 기록된 값을 쓴다(거리 기록 없음 = 0km).
                val endOdometer = takeEndSnapshot?.takeIf {
                    _state.value.trip?.calculationVersion == FUEL_SEGMENTS_CALCULATION_VERSION
                }?.invoke(tripId)
                when (val result = completeTripUseCase(CompleteTripCommand(tripId, atMillis, endOdometer))) {
                    is DomainResult.Success -> {
                        attempts.put(KEY_COMPLETE_AT, null)
                        _events.send(TripEvent.OpenSettlement(tripId))
                    }

                    is DomainResult.Failure -> {
                        attempts.put(KEY_COMPLETE_AT, null)
                        _state.update { it.copy(completeError = result.error.code.userMessage()) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 저장 여부가 불확실하다. 시각을 유지해 다시 시도해도 같은 종료로 처리된다.
                _state.update { it.copy(completeError = UNEXPECTED_FAILURE_MESSAGE) }
            } finally {
                _state.update { it.copy(isCompleting = false) }
                completeGuard.release()
            }
        }
    }

    // ---- 빈 여행 닫기. 구간·비용·거리가 없는 진행 중 여행만 삭제한다(트랜잭션 안에서 다시 검사). ----

    fun requestDiscard() {
        val current = _state.value
        if (current.trip?.status != TripStatus.IN_PROGRESS || current.isDiscarding) return
        _state.update { it.copy(showDiscardConfirm = true, discardError = null) }
    }

    fun dismissDiscard() = _state.update { it.copy(showDiscardConfirm = false) }

    fun confirmDiscard() {
        if (!discardGuard.tryAcquire()) return
        _state.update { it.copy(showDiscardConfirm = false, isDiscarding = true, discardError = null) }
        viewModelScope.launch {
            try {
                when (val result = discardEmptyTripUseCase(tripId)) {
                    is DomainResult.Success -> _events.send(TripEvent.Discarded)
                    // 이미 지워진 여행이면 닫으려던 결과와 같으므로 홈으로 보낸다.
                    is DomainResult.Failure ->
                        if (result.error.code == ErrorCode.TRIP_NOT_FOUND) {
                            _events.send(TripEvent.Discarded)
                        } else {
                            // 아무것도 지워지지 않았다. 기존 기록은 화면에 그대로 남는다.
                            _state.update { it.copy(discardError = result.error.code.userMessage()) }
                        }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 트랜잭션이 롤백되어 기록은 그대로다. 같은 버튼으로 다시 시도할 수 있다.
                _state.update { it.copy(discardError = UNEXPECTED_FAILURE_MESSAGE) }
            } finally {
                _state.update { it.copy(isDiscarding = false) }
                discardGuard.release()
            }
        }
    }

    companion object {
        const val ARG_TRIP_ID = "tripId"
        private const val KEY_COMPLETE_AT = "completeAtMillis"

        fun factory(container: AppContainer, activeTripId: Long? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val handle = createSavedStateHandle()
                TripViewModel(
                    tripId = activeTripId ?: checkNotNull(handle.get<Long>(ARG_TRIP_ID)),
                    trips = container.tripRepository,
                    ledgerRepository = container.tripLedgerRepository,
                    observeCostLedger = container.observeCostLedger,
                    setCostIncluded = container.setCostIncluded,
                    deleteCost = container.deleteCost,
                    completeTripUseCase = container.completeTrip,
                    discardEmptyTripUseCase = container.discardEmptyTrip,
                    clock = container.clock,
                    attempts = SavedStateAttemptStore(handle),
                    sessions = container.tripSessionRepository,
                    setPaused = container.setTripPaused,
                    observeRefuels = container.observeRefuels,
                    setRefuelDismissed = container.setRefuelDismissed,
                    setCostUndetected = container.setCostUndetected,
                    takeEndSnapshot = container.takeEndSnapshot,
                    observeFuelProgress = container.observeFuelProgress,
                )
            }
        }
    }
}
