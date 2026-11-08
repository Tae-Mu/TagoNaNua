package com.gatita.feature.settlement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatita.core.ui.component.ActionButton
import com.gatita.core.ui.component.EmptyBlock
import com.gatita.core.ui.component.ErrorBlock
import com.gatita.core.ui.component.ErrorMessage
import com.gatita.core.ui.component.GatitaScaffold
import com.gatita.core.ui.component.InfoCard
import com.gatita.core.ui.component.LabeledValue
import com.gatita.core.ui.component.LoadingBlock
import com.gatita.core.ui.component.NoticeMessage
import com.gatita.core.ui.component.SecondaryButton
import com.gatita.core.ui.component.SectionTitle
import com.gatita.core.ui.component.SourceLabel
import com.gatita.core.ui.component.StepperRow
import com.gatita.core.ui.component.SwitchRow
import com.gatita.core.ui.component.TextInput
import com.gatita.domain.model.CostLimits
import com.gatita.domain.model.PaymentPreview
import com.gatita.feature.common.Formatters
import com.gatita.feature.common.userMessage

@Composable
fun SettlementRoute(
    viewModel: SettlementViewModel,
    onBack: () -> Unit,
    onOpenResult: (Long) -> Unit,
    onOpenTrip: (Long) -> Unit,
    tripId: Long,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettlementEvent.OpenResult -> onOpenResult(event.tripId)
            }
        }
    }
    // 비용 화면에서 돌아오면 저장된 비용 기준으로 미리보기를 다시 계산한다.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reload() }
    SettlementContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::reload,
        onDriverNameChange = viewModel::onDriverNameChange,
        onPeopleChange = viewModel::onPeopleChange,
        onIncludeDriverChange = viewModel::onIncludeDriverChange,
        onRequestConfirm = viewModel::requestConfirm,
        onConfirm = viewModel::confirm,
        onDismissConfirm = viewModel::dismissConfirm,
        onEditCosts = { onOpenTrip(tripId) },
    )
}

@Composable
fun SettlementContent(
    state: SettlementUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDriverNameChange: (String) -> Unit,
    onPeopleChange: (Int) -> Unit,
    onIncludeDriverChange: (Boolean) -> Unit,
    onRequestConfirm: () -> Unit,
    onConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    onEditCosts: () -> Unit,
) {
    GatitaScaffold(title = "정산 미리보기", onBack = onBack) {
        val preview = state.preview
        when {
            state.isLoading -> LoadingBlock("정산을 계산하는 중")
            state.notFound -> EmptyBlock("여행을 찾을 수 없습니다", "삭제되었거나 저장되지 않은 여행입니다. 홈으로 돌아가세요.")
            preview == null -> ErrorBlock(state.loadError ?: "정산을 불러오지 못했습니다.", onRetry)
            else -> PreviewBody(
                state, preview, onDriverNameChange, onPeopleChange, onIncludeDriverChange,
                onRequestConfirm, onEditCosts,
            )
        }
    }

    if (state.showConfirm && state.preview != null) {
        AlertDialog(
            onDismissRequest = onDismissConfirm,
            title = { Text("정산을 확정할까요?") },
            text = {
                Text(
                    "정산에 포함한 실제 결제 금액 합계 ${Formatters.won(state.preview.totalWon)}을(를) " +
                        "${state.preview.splitPeople}명이 나눈 결과를 저장합니다. 확정하면 비용·인원·분담액을 수정할 수 없습니다. " +
                        "송금이나 입금은 이 앱에서 처리되지 않습니다.",
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) { Text("확정") }
            },
            dismissButton = {
                TextButton(onClick = onDismissConfirm, modifier = Modifier.heightIn(min = 48.dp)) { Text("취소") }
            },
        )
    }
}

@Composable
private fun PreviewBody(
    state: SettlementUiState,
    preview: PaymentPreview,
    onDriverNameChange: (String) -> Unit,
    onPeopleChange: (Int) -> Unit,
    onIncludeDriverChange: (Boolean) -> Unit,
    onRequestConfirm: () -> Unit,
    onEditCosts: () -> Unit,
) {
    val locked = state.isUpdatingPolicy || state.isConfirming

    InfoCard {
        Text("정산 총액 (포함한 실제 결제 금액의 합계)", style = MaterialTheme.typography.labelLarge)
        Text(Formatters.won(preview.totalWon), style = MaterialTheme.typography.displaySmall)
        Text(
            "포함 ${preview.includedCount}건 / 등록 ${preview.registeredCount}건 · 주유 ${Formatters.won(preview.fuelTotalWon)}" +
                " · 주차·톨비·기타 ${Formatters.won(preview.otherTotalWon)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "거리·연비·유가로 계산한 추정 금액은 더하지 않습니다.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    SecondaryButton("비용 확인·수정·포함 선택", onEditCosts, enabled = !locked)

    SectionTitle("나누기")
    StepperRow(
        label = "인원 (운전자 포함)",
        value = preview.people,
        range = CostLimits.MIN_PEOPLE..CostLimits.MAX_PEOPLE,
        onValueChange = onPeopleChange,
        enabled = !locked,
    )
    SwitchRow(
        label = "운전자도 분담",
        checked = preview.includeDriver,
        onCheckedChange = onIncludeDriverChange,
        enabled = !locked && preview.people > 1,
        supporting = if (preview.people == 1) {
            "1명이면 운전자 본인이 모두 부담하므로 항상 포함됩니다."
        } else {
            "끄면 운전자는 0원이고 동승자 ${preview.people - 1}명이 나눕니다."
        },
    )
    if (state.isUpdatingPolicy) NoticeMessage("분담 방식을 저장하는 중…")
    state.policyError?.let { ErrorMessage(it) }

    SectionTitle("분담액")
    TextInput(
        label = "내 표시 이름 (선택)",
        value = state.driverNameInput,
        onValueChange = onDriverNameChange,
        errorText = state.nameError,
        supporting = "20자까지. 비우면 '나'. 확정하면 명세에 고정됩니다.",
        enabled = !locked,
    )
    state.shares.forEach { share ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                share.label + if (share.isDriver && !preview.includeDriver) " (운전자 제외)" else "",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(Formatters.won(share.amountWon), style = MaterialTheme.typography.titleMedium)
        }
    }
    Text(
        "${preview.splitPeople}명이 나눕니다. 정수 원 단위로 나누고, 남는 원은 순서대로 1원씩 더해 합계가 총액과 같게 맞춥니다.",
        style = MaterialTheme.typography.bodySmall,
    )

    SectionTitle("기록 정보 (참고용)")
    InfoCard {
        LabeledValue(
            "주행거리",
            preview.recording.distanceKm?.let { Formatters.km(it) } ?: "기록 없음(0으로 보지 않음)",
        )
        Text(Formatters.quality(preview.recording.quality), style = MaterialTheme.typography.bodyMedium)
        SourceLabel(Formatters.source(preview.recording.source))
        Text("이 정보는 정산 금액에 영향을 주지 않습니다.", style = MaterialTheme.typography.bodySmall)
    }

    state.confirmError?.let { ErrorMessage(it) }
    if (!preview.canConfirm) {
        preview.blockingReason?.let { ErrorMessage(it.userMessage()) }
    }
    ActionButton(
        text = "정산 확정",
        onClick = onRequestConfirm,
        busy = state.isConfirming,
        busyText = "확정하는 중…",
        enabled = preview.canConfirm && !state.isUpdatingPolicy && state.nameError == null,
    )
}
