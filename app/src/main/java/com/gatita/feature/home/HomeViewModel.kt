package com.gatita.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gatita.di.AppContainer
import com.gatita.domain.model.CostLimits
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.Trip
import com.gatita.domain.model.TripSummary
import com.gatita.domain.model.VehicleSource
import com.gatita.domain.usecase.ObserveSettlementListUseCase
import com.gatita.domain.usecase.RestoreActiveTripUseCase
import com.gatita.domain.usecase.StartTripCommand
import com.gatita.domain.usecase.StartTripUseCase
import com.gatita.feature.common.InputParsers
import com.gatita.feature.common.LOAD_FAILURE_MESSAGE
import com.gatita.feature.common.ParsedNumber
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

data class HomeUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    /** Room에서 복구한 기록 중인 여행. 없으면 null. */
    val activeTrip: Trip? = null,
    /** 마쳤지만 정산을 확정하지 않은 여행. 포함 비용 합계는 아직 확정 전 금액이다. */
    val pendingTrips: List<TripSummary> = emptyList(),
    val pendingTotalWon: Long = 0,
    val nameInput: String = "",
    val nameError: String? = null,
    val startOdometerInput: String = "",
    val startOdometerError: String? = null,
    val isStarting: Boolean = false,
    val startError: String? = null,
)

sealed interface HomeEvent {
    data class OpenTrip(val tripId: Long) : HomeEvent
}

class HomeViewModel(
    private val restoreActiveTrip: RestoreActiveTripUseCase,
    private val observeSettlementList: ObserveSettlementListUseCase,
    private val startTripUseCase: StartTripUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    private val startGuard = SubmitGuard()
    private var observeJob: Job? = null

    init {
        observe()
    }

    /** 앱 시작 시 Room에서 기록 중인 여행을 복구하고 이후 변경을 따라간다. 실패하면 재시도할 수 있다. */
    fun observe() {
        observeJob?.cancel()
        _state.update { it.copy(isLoading = true, loadError = null) }
        observeJob = viewModelScope.launch {
            combine(restoreActiveTrip.observe(), observeSettlementList()) { active, list -> active to list }
                .catch { _state.update { it.copy(isLoading = false, loadError = LOAD_FAILURE_MESSAGE) } }
                .collect { (active, list) ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            loadError = null,
                            activeTrip = active,
                            pendingTrips = list.pending,
                            pendingTotalWon = list.pendingTotalWon,
                        )
                    }
                }
        }
    }

    fun onNameChange(text: String) {
        _state.update { it.copy(nameInput = text, nameError = null, startError = null) }
    }

    fun onStartOdometerChange(text: String) {
        _state.update { it.copy(startOdometerInput = text, startOdometerError = null, startError = null) }
    }

    fun startTrip() {
        if (!startGuard.tryAcquire()) return
        val current = _state.value
        val parsed = InputParsers.odometerKm(current.startOdometerInput, "계기판 주행거리")
        val nameError = if (current.nameInput.trim().codePointCount(0, current.nameInput.trim().length) >
            CostLimits.TRIP_NAME_MAX_LENGTH
        ) {
            "여행 이름은 ${CostLimits.TRIP_NAME_MAX_LENGTH}자 이하로 입력하세요."
        } else {
            null
        }
        if (parsed !is ParsedNumber.Ok || nameError != null) {
            _state.update {
                it.copy(startOdometerError = (parsed as? ParsedNumber.Invalid)?.message, nameError = nameError)
            }
            startGuard.release()
            return
        }
        _state.update { it.copy(isStarting = true, startError = null, startOdometerError = null, nameError = null) }
        viewModelScope.launch {
            try {
                // 주행거리는 사용자가 직접 입력한 값이므로 출처를 MANUAL로 저장한다. 인원·운전자 포함은 정산 단계에서 정한다.
                val command = StartTripCommand(
                    startOdometerKm = parsed.value,
                    startedAtMillis = clock.millis(),
                    source = VehicleSource.MANUAL,
                    name = current.nameInput,
                )
                when (val result = startTripUseCase(command)) {
                    is DomainResult.Success -> {
                        _state.update { it.copy(startOdometerInput = "", nameInput = "") }
                        _events.send(HomeEvent.OpenTrip(result.value.id))
                    }

                    is DomainResult.Failure -> {
                        val code = result.error.code
                        when {
                            code == ErrorCode.INVALID_TRIP_NAME ->
                                _state.update { it.copy(nameError = code.userMessage()) }

                            code == ErrorCode.ACTIVE_TRIP_EXISTS -> _state.update {
                                it.copy(startError = "이미 기록 중인 여행이 있습니다. '기록 중인 여행'에서 이어가세요.")
                            }

                            else -> _state.update { it.copy(startError = code.userMessage()) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 저장 여부가 불확실하다. 입력은 유지하고, 기록 중인 여행은 Flow가 Room 기준으로 다시 보여준다.
                _state.update { it.copy(startError = UNEXPECTED_FAILURE_MESSAGE) }
            } finally {
                _state.update { it.copy(isStarting = false) }
                startGuard.release()
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    restoreActiveTrip = container.restoreActiveTrip,
                    observeSettlementList = container.observeSettlementList,
                    startTripUseCase = container.startTrip,
                    clock = container.clock,
                )
            }
        }
    }
}
