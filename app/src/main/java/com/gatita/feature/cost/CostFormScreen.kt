package com.gatita.feature.cost

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatita.core.ui.component.ActionButton
import com.gatita.core.ui.component.ChoiceChips
import com.gatita.core.ui.component.EmptyBlock
import com.gatita.core.ui.component.ErrorBlock
import com.gatita.core.ui.component.ErrorMessage
import com.gatita.core.ui.component.GatitaScaffold
import com.gatita.core.ui.component.InfoCard
import com.gatita.core.ui.component.LoadingBlock
import com.gatita.core.ui.component.NumberField
import com.gatita.core.ui.component.TextInput
import com.gatita.domain.model.CostKind
import com.gatita.feature.common.Formatters

@Composable
fun CostFormRoute(viewModel: CostFormViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CostFormEvent.Saved -> onSaved()
            }
        }
    }
    CostFormContent(
        state = state,
        onBack = onBack,
        onRetryLoad = viewModel::load,
        onKindChange = viewModel::onKindChange,
        onAmountChange = viewModel::onAmountChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onUnitPriceChange = viewModel::onUnitPriceChange,
        onPaidOnChange = viewModel::onPaidOnChange,
        onSave = viewModel::save,
    )
}

@Composable
fun CostFormContent(
    state: CostFormUiState,
    onBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onKindChange: (CostKind) -> Unit,
    onAmountChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onUnitPriceChange: (String) -> Unit,
    onPaidOnChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    GatitaScaffold(title = if (state.isEdit) "비용 수정" else "비용 추가", onBack = onBack) {
        when {
            state.isLoading -> LoadingBlock("비용을 불러오는 중")
            state.loadError != null -> ErrorBlock(state.loadError, onRetryLoad)
            state.notFound -> EmptyBlock("비용을 찾을 수 없습니다", "삭제되었거나 다른 여행의 비용입니다. 뒤로 가서 목록을 확인하세요.")
            else -> {
                val locked = state.isSaving
                InfoCard {
                    Text(
                        "실제로 결제한 금액을 직접 입력합니다. 카드·하이패스 내역을 자동으로 가져오지 않으며, 이 앱은 결제나 송금을 하지 않습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Text("종류", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(
                    options = CostKind.entries.toList(),
                    selected = state.kind,
                    label = { Formatters.costKind(it) },
                    onSelect = onKindChange,
                    enabled = !locked,
                )

                NumberField(
                    label = "결제 금액 (원)",
                    value = state.amountInput,
                    onValueChange = onAmountChange,
                    errorText = state.amountError,
                    supporting = "1원 이상 10,000,000원 이하의 정수",
                    enabled = !locked,
                    imeAction = ImeAction.Next,
                )

                if (state.kind == CostKind.FUEL) {
                    TextInput(
                        label = "주유소 (선택)",
                        value = state.descriptionInput,
                        onValueChange = onDescriptionChange,
                        errorText = state.descriptionError,
                        supporting = "50자까지",
                        enabled = !locked,
                    )
                    NumberField(
                        label = "리터당 가격 (원/L)",
                        value = state.unitPriceInput,
                        onValueChange = onUnitPriceChange,
                        errorText = state.unitPriceError,
                        supporting = "주유량 표시에만 쓰입니다. 정산 금액은 위 결제 금액 그대로입니다. 소수 둘째 자리까지.",
                        enabled = !locked,
                    )
                    TextInput(
                        label = "주유일 (선택)",
                        value = state.paidOnInput,
                        onValueChange = onPaidOnChange,
                        errorText = state.paidOnError,
                        supporting = "2026-11-09 형식. 비우면 저장하는 날짜가 들어갑니다.",
                        enabled = !locked,
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    )
                } else {
                    TextInput(
                        label = when (state.kind) {
                            CostKind.PARKING -> "주차 장소 (선택)"
                            CostKind.TOLL -> "톨게이트·구간 (선택)"
                            else -> "내용 (선택)"
                        },
                        value = state.descriptionInput,
                        onValueChange = onDescriptionChange,
                        errorText = state.descriptionError,
                        supporting = "100자까지. 비워도 저장할 수 있습니다.",
                        enabled = !locked,
                        imeAction = ImeAction.Done,
                    )
                }

                state.saveError?.let { ErrorMessage(it) }
                ActionButton(
                    text = if (state.isEdit) "수정 저장" else "비용 저장",
                    onClick = onSave,
                    busy = state.isSaving,
                    busyText = "저장하는 중…",
                )
            }
        }
    }
}
