package com.gatita.feature.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gatita.core.qr.QrEncoder
import com.gatita.core.qr.QrMatrix
import com.gatita.core.qr.QrResult
import com.gatita.di.AppContainer
import com.gatita.domain.model.CostKind
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.PaymentSettlement
import com.gatita.domain.model.SettlementRecord
import com.gatita.domain.usecase.BuildPaymentSummaryUseCase
import com.gatita.domain.usecase.GetSettlementRecordUseCase
import com.gatita.feature.common.Formatters
import com.gatita.feature.common.LOAD_FAILURE_MESSAGE
import com.gatita.feature.common.userMessage
import com.gatita.feature.settlement.SettlementView
import com.gatita.feature.settlement.ShareLine
import com.gatita.feature.settlement.toView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface QrState {
    data object None : QrState

    data class Ready(val matrix: QrMatrix) : QrState

    /** 요약이 QR 용량을 넘었다. 텍스트는 화면에 그대로 보이며 QR만 만들지 못했다. */
    data object TooLong : QrState
}

data class CostLine(val title: String, val amount: String, val details: List<String>, val included: Boolean)

/** 확정된 결제액 정산의 표시용 값. 저장된 스냅샷만 옮긴 것이며 다시 계산하지 않는다. */
data class PaymentResultView(
    val tripName: String,
    val tripDate: String,
    val total: String,
    val fuelTotal: String,
    val otherTotal: String,
    val peopleLine: String,
    val shares: List<ShareLine>,
    val costs: List<CostLine>,
    val distance: String,
    val quality: String,
    val source: String,
    val calculationVersion: String,
    val perPerson: String = "",
    val splitPeople: Int = 0,
    val includeDriver: Boolean = true,
)

sealed interface ResultContentState {
    data class Payments(val view: PaymentResultView, val summaryText: String, val qr: QrState) : ResultContentState

    /** v1 추정 정산. 읽기 전용이며 공유 QR을 만들지 않는다. */
    data class Legacy(val view: SettlementView) : ResultContentState
}

data class ResultUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    /** 확정된 정산이 없는 경우(빈 상태). */
    val notFound: Boolean = false,
    val confirmedAt: String = "",
    val content: ResultContentState? = null,
)

class ResultViewModel(
    private val tripId: Long,
    private val getRecord: GetSettlementRecordUseCase,
    private val buildSummary: BuildPaymentSummaryUseCase,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(ResultUiState())
    val state: StateFlow<ResultUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            try {
                when (val result = getRecord(tripId)) {
                    is DomainResult.Failure -> _state.update {
                        if (result.error.code == ErrorCode.SETTLEMENT_NOT_FOUND) {
                            it.copy(isLoading = false, notFound = true)
                        } else {
                            it.copy(isLoading = false, loadError = result.error.code.userMessage())
                        }
                    }

                    is DomainResult.Success -> when (val record = result.value) {
                        is SettlementRecord.Legacy -> _state.update {
                            ResultUiState(
                                isLoading = false,
                                confirmedAt = Formatters.friendlyDateTime(record.settlement.confirmedAtMillis),
                                content = ResultContentState.Legacy(record.settlement.calculation.toView()),
                            )
                        }

                        is SettlementRecord.Payments -> presentPayments(record.settlement)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, loadError = LOAD_FAILURE_MESSAGE) }
            }
        }
    }

    private suspend fun presentPayments(settlement: PaymentSettlement) {
        val summary = buildSummary(tripId)
        if (summary !is DomainResult.Success) {
            _state.update { it.copy(isLoading = false, loadError = LOAD_FAILURE_MESSAGE) }
            return
        }
        val text = summary.value
        val qr = withContext(computeDispatcher) {
            when (val encoded = QrEncoder.encode(text)) {
                is QrResult.Success -> QrState.Ready(encoded.matrix)
                is QrResult.TooLong, is QrResult.Empty -> QrState.TooLong
            }
        }
        _state.update {
            ResultUiState(
                isLoading = false,
                confirmedAt = Formatters.friendlyDateTime(settlement.confirmedAtMillis),
                content = ResultContentState.Payments(toView(settlement), text, qr),
            )
        }
    }

    private fun toView(s: PaymentSettlement) = PaymentResultView(
        tripName = s.tripName,
        tripDate = s.tripDate.toString(),
        total = Formatters.won(s.totalWon),
        perPerson = s.shares.filter { s.includeDriver || !it.isDriver }.map { it.amountWon }.let { amounts ->
            val low=amounts.minOrNull()?:0;val high=amounts.maxOrNull()?:0
            if(low==high)Formatters.won(low) else "${Formatters.won(low).removeSuffix("원")}–${Formatters.won(high)}"
        },
        splitPeople=s.splitPeople,
        includeDriver=s.includeDriver,
        fuelTotal = Formatters.won(s.fuelTotalWon),
        otherTotal = Formatters.won(s.otherTotalWon),
        peopleLine = "${s.people}명" +
            if (s.includeDriver) "" else " (운전자 제외, ${s.splitPeople}명이 분담)",
        shares = s.shares.map { ShareLine(it.displayName, Formatters.won(it.amountWon)) },
        costs = s.costs.map { cost ->
            CostLine(
                title = cost.description?.takeIf { it.isNotBlank() }?.let { "${Formatters.costKind(cost.kind)} · $it" } ?: Formatters.costKind(cost.kind),
                amount = Formatters.won(cost.amountWon),
                details = buildList {
                    if (cost.kind == CostKind.FUEL) {
                        cost.unitPriceWonPerLiter?.let { add("리터당 " + Formatters.wonDecimal(it)) }
                        cost.fuelLiters?.let { add("주유량 약 " + Formatters.decimal(it, 3) + " L") }
                        cost.paidOn?.let { add("주유일 " + Formatters.shortDate(it)) }
                    }
                },
                included = cost.included,
            )
        },
        distance = s.recording.distanceKm?.let { Formatters.km(it) } ?: "기록 없음",
        quality = Formatters.quality(s.recording.quality),
        source = Formatters.source(s.recording.source),
        calculationVersion = s.calculationVersion,
    )

    companion object {
        const val ARG_TRIP_ID = "tripId"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ResultViewModel(
                    tripId = checkNotNull(createSavedStateHandle().get<Long>(ARG_TRIP_ID)),
                    getRecord = container.getSettlementRecord,
                    buildSummary = container.buildPaymentSummary,
                )
            }
        }
    }
}
