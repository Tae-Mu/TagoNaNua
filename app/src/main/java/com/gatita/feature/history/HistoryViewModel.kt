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
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val observeHistory: ObserveTripHistoryUseCase,
    initialMonth: YearMonth,
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
                .collect { summary -> _state.update { summary.toState() } }
        }
    }

    fun previousMonth() = changeMonth(-1)

    fun nextMonth() = changeMonth(1)

    private fun changeMonth(delta: Long) {
        query.update { (month, filter) -> month.plusMonths(delta) to filter }
        _state.update { it.copy(isLoading = true, month = query.value.first) }
    }

    fun setFilter(filter: HistoryFilter) {
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
            costSummary = if (section == HistorySection.LEGACY) null else "비용 ${costCount}건 중 ${includedCount}건 포함",
            source = Formatters.source(source),
        )
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HistoryViewModel(
                    observeHistory = container.observeTripHistory,
                    initialMonth = YearMonth.now(container.clock.withZone(container.zone)),
                )
            }
        }
    }
}
