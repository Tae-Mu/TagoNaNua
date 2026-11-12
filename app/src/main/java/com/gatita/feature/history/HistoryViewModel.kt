package com.gatita.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gatita.di.AppContainer
import com.gatita.domain.model.HistoryFilter
import com.gatita.domain.model.HistorySummary
import com.gatita.domain.model.SettlementKind
import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.TripSummary
import com.gatita.domain.usecase.ObserveTripHistoryUseCase
import com.gatita.feature.common.Formatters
import com.gatita.feature.common.LOAD_FAILURE_MESSAGE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId

/** 내역 묶음. 결제액 정산과 이전 추정 정산(LEGACY_ESTIMATE)은 섞지 않고 따로 보여준다. */
enum class HistorySection(val title: String) {
    OPEN("진행 중·정산 전"),
    PAYMENTS("결제액 정산 확정"),
    LEGACY("이전 추정 정산 (LEGACY_ESTIMATE · 결제액 아님)"),
}

data class HistoryRow(
    val tripId: Long,
    val status: TripStatus,
    val section: HistorySection,
    val title: String,
    val date: String,
    val statusLabel: String,
    /** 금액 앞에 붙는 설명. 금액의 의미(미확정 합계/확정 결제액/추정)를 구분한다. */
    val amountCaption: String?,
    /** 금액이 없으면 null이며 0원으로 표시하지 않는다. */
    val amount: String?,
    val distance: String?,
    val costSummary: String?,
    val source: String,
)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val month: YearMonth,
    val filter: HistoryFilter = HistoryFilter.ALL,
    val rows: List<HistoryRow> = emptyList(),
    val monthlyTripCount: Int = 0,
    val monthlyDistance: String = "",
    val monthlySettledCount: Int = 0,
    val deleteTarget: HistoryRow? = null,
    val deleting: Boolean = false,
    val deleteError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val observeHistory: ObserveTripHistoryUseCase,
    initialMonth: YearMonth,
    private val hideTrip: com.gatita.domain.usecase.HideTripUseCase? = null,
) : ViewModel() {
    private val query = MutableStateFlow(initialMonth to HistoryFilter.ALL)
    private val _state = MutableStateFlow(HistoryUiState(month = initialMonth))
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        observe()
    }

    fun observe() {
        job?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            query
                .flatMapLatest { (month, filter) -> observeHistory(month, filter) }
                .catch { _state.update { it.copy(isLoading = false, error = LOAD_FAILURE_MESSAGE) } }
                .collect { summary -> _state.update { old -> summary.toState().copy(deleteTarget=old.deleteTarget,deleting=old.deleting,deleteError=old.deleteError) } }
        }
    }

    fun requestDelete(row:HistoryRow) { if(!state.value.deleting) _state.update { it.copy(deleteTarget=row,deleteError=null) } }
    fun dismissDelete() { if(!state.value.deleting) _state.update { it.copy(deleteTarget=null) } }
    fun confirmDelete() {
        val action=hideTrip ?: return
        val target=state.value.deleteTarget ?: return
        if(state.value.deleting) return
        _state.update { it.copy(deleting=true,deleteError=null) }
        viewModelScope.launch {
            try { when(val result=action(target.tripId)) {
                is com.gatita.domain.model.DomainResult.Success -> _state.update { it.copy(deleteTarget=null) }
                is com.gatita.domain.model.DomainResult.Failure -> _state.update { it.copy(deleteError="진행 중인 여행은 먼저 마쳐 주세요.") }
            } } catch(e:kotlinx.coroutines.CancellationException) { throw e }
            catch(e:Exception) { _state.update { it.copy(deleteError="목록에서 삭제하지 못했어요. 다시 시도해 주세요.") } }
            finally { _state.update { it.copy(deleting=false) } }
        }
    }

    fun setMonth(month:YearMonth) {
        if(query.value.first==month)return
        query.update{month to it.second}
        _state.update{it.copy(isLoading=true,month=month)}
    }

    fun previousMonth() = changeMonth(-1)

    fun nextMonth() = changeMonth(1)

    private fun changeMonth(delta: Long) {
        query.update { (month, filter) -> month.plusMonths(delta) to filter }
        _state.update { it.copy(isLoading = true, month = query.value.first) }
    }

    fun setFilter(filter: HistoryFilter) {
        if(query.value.second==filter)return
        query.update { (month, _) -> month to filter }
        _state.update { it.copy(isLoading = true, filter = filter) }
    }

    private fun HistorySummary.toState() = HistoryUiState(
        isLoading = false,
        error = null,
        month = month,
        filter = filter,
        rows = trips.map { it.toRow() },
        monthlyTripCount = monthlyTripCount,
        monthlyDistance = Formatters.km(monthlyDistanceKm),
        monthlySettledCount = monthlySettledCount,
    )

    private fun TripSummary.toRow(): HistoryRow {
        val brief = settlement
        val section = when {
            brief?.kind == SettlementKind.LEGACY_ESTIMATE -> HistorySection.LEGACY
            status == TripStatus.SETTLED -> HistorySection.PAYMENTS
            else -> HistorySection.OPEN
        }
        val (caption, amount) = when {
            brief?.kind == SettlementKind.LEGACY_ESTIMATE ->
                "이전 방식 추정 유류비 (실제 결제 금액이 아님)" to Formatters.won(brief.totalWon)

            brief?.kind == SettlementKind.PAYMENTS ->
                "확정 결제액 합계" to Formatters.won(brief.totalWon)

            status == TripStatus.SETTLED -> null to null
            costCount == 0 -> "등록한 결제 비용 없음" to null
            else -> "포함한 결제 비용 합계(미확정)" to Formatters.won(includedTotalWon)
        }
        return HistoryRow(
            tripId = tripId,
            status = status,
            section = section,
            title = name,
            date = tripDate.toString(),
            statusLabel = when {
                section == HistorySection.LEGACY -> "이전 추정 정산"
                else -> Formatters.tripStatus(status)
            },
            amountCaption = caption,
            amount = amount,
            distance = distanceKm?.let { Formatters.km(it) },
            // 같은 날·같은 이름의 여행을 구분하도록 출발 시각을 앞에 붙임
            costSummary = if (section == HistorySection.LEGACY) null else Formatters.startTime(startedAtMillis) + " · " + (if (costCount == 0) "비용 없음" else "비용 ${costCount}건 · 정산 포함 ${includedCount}건"),
            source = Formatters.source(source),
        )
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HistoryViewModel(
                    observeHistory = container.observeTripHistory,
                    initialMonth = YearMonth.now(container.clock.withZone(container.zone)),
                    hideTrip = container.hideTrip,
                )
            }
        }
    }
}
