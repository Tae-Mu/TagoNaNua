package com.gatita.feature.cost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gatita.di.AppContainer
import com.gatita.domain.model.Cost
import com.gatita.domain.model.CostInput
import com.gatita.domain.model.CostKind
import com.gatita.domain.model.DomainError
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.UNSAVED_ID
import com.gatita.domain.repository.CostRepository
import com.gatita.domain.usecase.AddCostUseCase
import com.gatita.domain.usecase.UpdateCostUseCase
import com.gatita.feature.common.AttemptStore
import com.gatita.feature.common.InputParsers
import com.gatita.feature.common.LOAD_FAILURE_MESSAGE
import com.gatita.feature.common.ParsedDate
import com.gatita.feature.common.ParsedNumber
import com.gatita.feature.common.ParsedWon
import com.gatita.feature.common.SavedStateAttemptStore
import com.gatita.feature.common.SubmitGuard
import com.gatita.feature.common.UNEXPECTED_FAILURE_MESSAGE
import com.gatita.feature.common.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

data class CostFormUiState(
    /** 수정 대상 비용을 읽는 중. 새 비용이면 false. */
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val notFound: Boolean = false,
    val isEdit: Boolean = false,

    val kind: CostKind = CostKind.FUEL,
    val amountInput: String = "",
    val descriptionInput: String = "",
    val unitPriceInput: String = "",
    /** 비우면 등록 당시 날짜가 저장된다(주유만). */
    val paidOnInput: String = "",

    val amountError: String? = null,
    val descriptionError: String? = null,
    val unitPriceError: String? = null,
    val paidOnError: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
)

sealed interface CostFormEvent {
    data object Saved : CostFormEvent
}

/**
 * 비용 등록/수정 폼. 저장에 실패해도 입력은 그대로 남고, 성공했을 때만 [CostFormEvent.Saved]로 화면을 닫는다.
 * 종류를 바꿔도 입력값은 유지하며, 저장할 때 종류에 맞는 필드만 검증·저장된다.
 */
class CostFormViewModel(
    private val tripId: Long,
    private val costId: Long,
    private val costs: CostRepository,
    private val addCost: AddCostUseCase,
    private val updateCost: UpdateCostUseCase,
    private val attempts: AttemptStore,
) : ViewModel() {
    private val _state = MutableStateFlow(CostFormUiState(isEdit = costId != UNSAVED_ID, isLoading = costId != UNSAVED_ID))
    val state: StateFlow<CostFormUiState> = _state.asStateFlow()

    private val _events = Channel<CostFormEvent>(Channel.BUFFERED)
    val events: Flow<CostFormEvent> = _events.receiveAsFlow()

    private val saveGuard = SubmitGuard()

    init {
        if (costId != UNSAVED_ID) load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            try {
                val cost = costs.get(costId)?.takeIf { it.tripId == tripId }
                if (cost == null) {
                    _state.update { it.copy(isLoading = false, notFound = true) }
                } else {
                    _state.update { cost.toForm(it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, loadError = LOAD_FAILURE_MESSAGE) }
            }
        }
    }

    private fun Cost.toForm(base: CostFormUiState) = base.copy(
        isLoading = false,
        loadError = null,
        notFound = false,
        kind = kind,
        amountInput = amountWon.toString(),
        descriptionInput = description.orEmpty(),
        unitPriceInput = unitPriceWonPerLiter?.toPlainString().orEmpty(),
        paidOnInput = paidOn?.toString().orEmpty(),
    )

    fun onKindChange(kind: CostKind) = _state.update { it.copy(kind = kind, saveError = null) }

    fun onAmountChange(text: String) = _state.update { it.copy(amountInput = text, amountError = null, saveError = null) }

    fun onDescriptionChange(text: String) =
        _state.update { it.copy(descriptionInput = text, descriptionError = null, saveError = null) }

    fun onUnitPriceChange(text: String) =
        _state.update { it.copy(unitPriceInput = text, unitPriceError = null, saveError = null) }

    fun onPaidOnChange(text: String) = _state.update { it.copy(paidOnInput = text, paidOnError = null, saveError = null) }

    fun save() {
        val current = _state.value
        if (current.isLoading || current.notFound || current.loadError != null) return
        if (!saveGuard.tryAcquire()) return

        val amount = InputParsers.wonAmount(current.amountInput)
        val isFuel = current.kind == CostKind.FUEL
        val price = if (isFuel) InputParsers.pricePerLiter(current.unitPriceInput) else null
        val paidOn = if (isFuel) InputParsers.isoDate(current.paidOnInput) else ParsedDate.Ok(null)

        val amountError = (amount as? ParsedWon.Invalid)?.message
        val priceError = (price as? ParsedNumber.Invalid)?.message
        val paidOnError = (paidOn as? ParsedDate.Invalid)?.message
        if (amountError != null || priceError != null || paidOnError != null) {
            _state.update {
                it.copy(amountError = amountError, unitPriceError = priceError, paidOnError = paidOnError)
            }
            saveGuard.release()
            return
        }
        val input = CostInput(
            kind = current.kind,
            amountWon = (amount as ParsedWon.Ok).value,
            description = current.descriptionInput,
            unitPriceWonPerLiter = (price as? ParsedNumber.Ok)?.value,
            paidOn = (paidOn as ParsedDate.Ok).value,
        )

        // 같은 등록 시도의 재전송이 같은 요청 ID를 쓰도록 먼저 저장한다. 도메인 실패 후에는 새 ID를 쓴다.
        val requestId = attempts.get(KEY_REQUEST_ID) ?: UUID.randomUUID().toString().also {
            attempts.put(KEY_REQUEST_ID, it)
        }

        _state.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                val result: DomainResult<Cost> = if (costId == UNSAVED_ID) {
                    addCost(tripId, input, requestId)
                } else {
                    updateCost(tripId, costId, input)
                }
                when (result) {
                    is DomainResult.Success -> {
                        attempts.put(KEY_REQUEST_ID, null)
                        _events.send(CostFormEvent.Saved)
                    }

                    is DomainResult.Failure -> {
                        // REQUEST_ID_REUSED는 직전 시도가 이미 저장됐을 수 있다는 뜻이므로 ID를 유지한다.
                        if (result.error.code != ErrorCode.REQUEST_ID_REUSED) attempts.put(KEY_REQUEST_ID, null)
                        showFailure(result.error)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 저장 여부가 불확실하다. 입력과 요청 ID를 유지해 같은 입력으로 다시 저장해도 중복되지 않는다.
                _state.update { it.copy(saveError = UNEXPECTED_FAILURE_MESSAGE) }
            } finally {
                _state.update { it.copy(isSaving = false) }
                saveGuard.release()
            }
        }
    }

    private fun showFailure(error: DomainError) {
        val message = error.code.userMessage()
        _state.update {
            when (error.field) {
                "amountWon" -> it.copy(amountError = message)
                "description" -> it.copy(descriptionError = message)
                "unitPrice" -> it.copy(unitPriceError = message)
                "paidOn" -> it.copy(paidOnError = message)
                else -> it.copy(saveError = message)
            }
        }
    }

    companion object {
        const val ARG_TRIP_ID = "tripId"
        const val ARG_COST_ID = "costId"
        private const val KEY_REQUEST_ID = "costRequestId"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val handle = createSavedStateHandle()
                CostFormViewModel(
                    tripId = checkNotNull(handle.get<Long>(ARG_TRIP_ID)),
                    costId = handle.get<Long>(ARG_COST_ID) ?: UNSAVED_ID,
                    costs = container.costRepository,
                    addCost = container.addCost,
                    updateCost = container.updateCost,
                    attempts = SavedStateAttemptStore(handle),
                )
            }
        }
    }
}
