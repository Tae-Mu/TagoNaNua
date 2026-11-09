package com.gatita.feature.settlement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gatita.di.AppContainer
import com.gatita.domain.calculator.ShareLabels
import com.gatita.domain.model.CostLimits
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.PaymentPreview
import com.gatita.domain.usecase.ConfirmPaymentCommand
import com.gatita.domain.usecase.ConfirmPaymentSettlementUseCase
import com.gatita.domain.usecase.PreviewPaymentSettlementUseCase
import com.gatita.domain.usecase.UpdateSplitPolicyUseCase
import com.gatita.feature.common.AttemptStore
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
import java.util.UUID

data class ShareRow(val label: String, val amountWon: Long, val isDriver: Boolean)

data class SettlementUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val notFound: Boolean = false,
    /** 도메인이 계산한 미리보기. 총액은 포함한 실제 결제 금액의 합계다. */
    val preview: PaymentPreview? = null,
    /** 입력한 운전자 표시 이름. 확정하면 명세에 고정된다. 비우면 "나". */
    val driverNameInput: String = "",
    val nameError: String? = null,
    val shares: List<ShareRow> = emptyList(),

    val isUpdatingPolicy: Boolean = false,
    val policyError: String? = null,

    val showConfirm: Boolean = false,
    val isConfirming: Boolean = false,
    val confirmError: String? = null,
)

sealed interface SettlementEvent {
    data class OpenResult(val tripId: Long) : SettlementEvent
}

class SettlementViewModel(
    private val tripId: Long,
    private val previewPayment: PreviewPaymentSettlementUseCase,
    private val updateSplitPolicy: UpdateSplitPolicyUseCase,
    private val confirmPayment: ConfirmPaymentSettlementUseCase,
    private val attempts: AttemptStore,
    private val settings: com.gatita.domain.repository.SettingsRepository? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(SettlementUiState())
    val state: StateFlow<SettlementUiState> = _state.asStateFlow()

    private val _events = Channel<SettlementEvent>(Channel.BUFFERED)
    val events: Flow<SettlementEvent> = _events.receiveAsFlow()

    private val policyGuard = SubmitGuard()
    private val confirmGuard = SubmitGuard()

    init {
        if(settings == null) reload() else viewModelScope.launch {
            try { val defaults = settings.get(); _state.update { it.copy(driverNameInput=defaults.driverName) } }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { /* Defaults are optional; settlement remains available. */ }
            reload()
        }
    }

    /** 저장된 비용·분담 정책으로 미리보기를 다시 계산한다. 비용 화면에서 돌아올 때도 호출한다. */
    fun reload() {
        if (_state.value.isConfirming || _state.value.isUpdatingPolicy) return
        _state.update { if (it.preview == null) it.copy(isLoading = true, loadError = null) else it }
        viewModelScope.launch {
            try {
                when (val result = previewPayment(tripId, normalizedName(_state.value.driverNameInput))) {
                    is DomainResult.Success -> showPreview(result.value)
                    is DomainResult.Failure -> onPreviewFailure(result.error.code)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, loadError = UNEXPECTED_FAILURE_MESSAGE) }
            }
        }
    }

    private suspend fun onPreviewFailure(code: ErrorCode) {
        when (code) {
            ErrorCode.ALREADY_SETTLED -> _events.send(SettlementEvent.OpenResult(tripId))
            ErrorCode.TRIP_NOT_FOUND -> _state.update { it.copy(isLoading = false, notFound = true, preview = null) }
            // 입력해 둔 이름이 잘못된 경우에도 미리보기는 기본 이름으로 보여준다.
            ErrorCode.INVALID_PARTICIPANTS -> {
                _state.update { it.copy(nameError = code.userMessage()) }
                when (val retry = previewPayment(tripId, null)) {
                    is DomainResult.Success -> showPreview(retry.value)
                    is DomainResult.Failure -> _state.update { it.copy(isLoading = false, loadError = retry.error.code.userMessage()) }
                }
            }

            else -> _state.update { it.copy(isLoading = false, loadError = code.userMessage()) }
        }
    }

    private fun showPreview(preview: PaymentPreview) {
        _state.update {
            it.copy(
                isLoading = false,
                loadError = null,
                notFound = false,
                preview = preview,
                shares = rows(preview, it.driverNameInput),
            )
        }
    }

    private fun normalizedName(raw: String): String? = raw.trim().ifEmpty { null }

    /** 표시용 이름 치환만 한다. 금액은 도메인이 계산한 값 그대로다. */
    private fun rows(preview: PaymentPreview, nameInput: String): List<ShareRow> {
        val driverLabel = nameInput.trim().ifEmpty { ShareLabels.DEFAULT_DRIVER_NAME }
        return preview.shares.map { ShareRow(if (it.isDriver) driverLabel else it.displayName, it.amountWon, it.isDriver) }
    }

    fun onDriverNameChange(text: String) {
        val trimmed = text.trim()
        val error = when {
            trimmed.any(Char::isISOControl) -> "사용할 수 없는 문자가 있습니다."
            trimmed.codePointCount(0, trimmed.length) > CostLimits.DISPLAY_NAME_MAX_LENGTH ->
                "표시 이름은 ${CostLimits.DISPLAY_NAME_MAX_LENGTH}자 이하로 입력하세요."
            else -> null
        }
        _state.update {
            it.copy(
                driverNameInput = text,
                nameError = error,
                confirmError = null,
                shares = it.preview?.let { p -> rows(p, text) } ?: it.shares,
            )
        }
    }

    // ---- 분담 정책 ----

    fun onPeopleChange(people: Int) {
        val preview = _state.value.preview ?: return
        applyPolicy(people, preview.includeDriver)
    }

    fun onIncludeDriverChange(includeDriver: Boolean) {
        val preview = _state.value.preview ?: return
        applyPolicy(preview.people, includeDriver)
    }

    private fun applyPolicy(people: Int, includeDriver: Boolean) {
        val preview = _state.value.preview ?: return
        if (_state.value.isConfirming) return
        if (!policyGuard.tryAcquire()) return
        _state.update { it.copy(isUpdatingPolicy = true, policyError = null, confirmError = null) }
        viewModelScope.launch {
            try {
                val result = updateSplitPolicy(
                    tripId = tripId,
                    people = people,
                    includeDriver = includeDriver,
                    expectedRevision = preview.tripRevision,
                    driverName = normalizedName(_state.value.driverNameInput),
                )
                when (result) {
                    is DomainResult.Success -> showPreview(result.value)
                    is DomainResult.Failure -> {
                        // 저장되지 않았다. 마지막으로 저장된 값이 그대로 보이며 최신 상태를 다시 읽는다.
                        _state.update { it.copy(policyError = result.error.code.userMessage()) }
                        if (result.error.code == ErrorCode.STALE_REVISION) refreshAfterConflict()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(policyError = UNEXPECTED_FAILURE_MESSAGE) }
            } finally {
                _state.update { it.copy(isUpdatingPolicy = false) }
                policyGuard.release()
            }
        }
    }

    private suspend fun refreshAfterConflict() {
        when (val result = previewPayment(tripId, normalizedName(_state.value.driverNameInput))) {
            is DomainResult.Success -> showPreview(result.value)
            is DomainResult.Failure -> onPreviewFailure(result.error.code)
        }
    }

    // ---- 확정 ----

    fun requestConfirm() {
        val state = _state.value
        val preview = state.preview ?: return
        if (!preview.canConfirm || state.isConfirming || state.isUpdatingPolicy || state.nameError != null) return
        _state.update { it.copy(showConfirm = true, confirmError = null) }
    }

    fun dismissConfirm() = _state.update { it.copy(showConfirm = false) }

    fun confirm() {
        if (!confirmGuard.tryAcquire()) return
        val preview = _state.value.preview
        if (preview == null || !preview.canConfirm) {
            _state.update { it.copy(showConfirm = false) }
            confirmGuard.release()
            return
        }
        // 같은 revision에 대한 재시도는 같은 요청 ID를 써서 중복 확정을 막는다. revision이 바뀌면 새 ID다.
        val revisionKey = preview.tripRevision.toString()
        val requestId = attempts.get(KEY_REQUEST_ID)?.takeIf { attempts.get(KEY_REVISION) == revisionKey }
            ?: UUID.randomUUID().toString().also {
                attempts.put(KEY_REQUEST_ID, it)
                attempts.put(KEY_REVISION, revisionKey)
            }
        val driverName = normalizedName(_state.value.driverNameInput)

        _state.update { it.copy(showConfirm = false, isConfirming = true, confirmError = null) }
        viewModelScope.launch {
            try {
                val command = ConfirmPaymentCommand(tripId, requestId, preview.tripRevision, driverName)
                when (val result = confirmPayment(command)) {
                    is DomainResult.Success -> {
                        clearConfirmAttempt()
                        _events.send(SettlementEvent.OpenResult(tripId))
                    }

                    is DomainResult.Failure -> onConfirmFailure(result.error.code)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 확정 여부가 불확실하다. 요청 ID를 유지해 같은 화면에서 다시 확정해도 중복 저장되지 않는다.
                _state.update {
                    it.copy(confirmError = "확정 결과를 확인하지 못했습니다. 입력한 내용은 그대로입니다. 다시 확정을 눌러도 중복 저장되지 않습니다.")
                }
            } finally {
                _state.update { it.copy(isConfirming = false) }
                confirmGuard.release()
            }
        }
    }

    private suspend fun onConfirmFailure(code: ErrorCode) {
        // 도메인 실패는 아무것도 저장되지 않았다는 뜻이다.
        clearConfirmAttempt()
        when (code) {
            ErrorCode.ALREADY_SETTLED -> _events.send(SettlementEvent.OpenResult(tripId))
            ErrorCode.INVALID_PARTICIPANTS -> _state.update { it.copy(nameError = code.userMessage()) }
            ErrorCode.STALE_REVISION, ErrorCode.NO_INCLUDED_COSTS, ErrorCode.TRIP_NOT_FINISHED -> {
                _state.update {
                    it.copy(
                        confirmError = if (code == ErrorCode.STALE_REVISION) {
                            "비용이나 분담이 바뀌어 확정하지 않았습니다. 아래 새 미리보기를 확인한 뒤 다시 확정하세요."
                        } else {
                            code.userMessage()
                        },
                    )
                }
                refreshAfterConflict()
            }

            else -> _state.update { it.copy(confirmError = code.userMessage()) }
        }
    }

    private fun clearConfirmAttempt() {
        attempts.put(KEY_REQUEST_ID, null)
        attempts.put(KEY_REVISION, null)
    }

    companion object {
        const val ARG_TRIP_ID = "tripId"
        private const val KEY_REQUEST_ID = "confirmRequestId"
        private const val KEY_REVISION = "confirmRevision"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val handle = createSavedStateHandle()
                SettlementViewModel(
                    tripId = checkNotNull(handle.get<Long>(ARG_TRIP_ID)),
                    previewPayment = container.previewPaymentSettlement,
                    updateSplitPolicy = container.updateSplitPolicy,
                    confirmPayment = container.confirmPaymentSettlement,
                    attempts = SavedStateAttemptStore(handle),
                    settings = container.settingsRepository,
                )
            }
        }
    }
}
