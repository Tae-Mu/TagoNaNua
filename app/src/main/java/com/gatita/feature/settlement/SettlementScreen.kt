package com.gatita.feature.settlement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.gatita.core.ui.component.*
import com.gatita.core.ui.theme.Navy

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
    GatitaScaffold(title = "정산 미리보기", onBack = onBack, subtitle="함께 쓴 비용을 확인하고, 편하게 나눠요.") {
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
    var breakdown by rememberSaveable { mutableStateOf(false) }
    SurfaceSummary(preview)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
        Panel(Modifier.weight(1f)) {
            SectionTitle("함께 나누는 기준")
            StepperRow("함께 탄 인원",preview.people,1..8,onPeopleChange,enabled=!locked)
            SwitchRow("본인도 정산에 포함",preview.includeDriver,onIncludeDriverChange,
                enabled=!locked&&preview.people>1,supporting=if(preview.people==1)"혼자 여행할 때는 본인이 부담해요." else "끄면 동승자 ${preview.people-1}명이 나눠요.")
            TextInput("내 이름 · 선택",state.driverNameInput,onDriverNameChange,errorText=state.nameError,enabled=!locked)
            if(state.isUpdatingPolicy)Note("분담 방식을 저장하고 있어요…")
            state.policyError?.let{ErrorMessage(it)}
            Action("비용 내역 확인",primary=false,enabled=!locked,onClick=onEditCosts)
        }
        Panel(Modifier.weight(1f)) {
            SectionTitle("각자 나눌 금액")
            Note("${preview.splitPeople}명 · 원 단위로 나눠요")
            val participantAmounts=preview.shares.filter{preview.includeDriver||!it.isDriver}.map{it.amountWon}
            val minimum=participantAmounts.minOrNull()?:0
            val maximum=participantAmounts.maxOrNull()?:0
            Text(if(minimum==maximum)Formatters.won(minimum) else "${Formatters.won(minimum)} ~ ${Formatters.won(maximum)}",fontSize=26.sp)
            TextButton({breakdown=!breakdown}){Text(if(breakdown)"개인별 명세 접기" else "개인별 전체 명세 보기")}
            if(breakdown)state.shares.forEach {share->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Text(share.label+if(share.isDriver&&!preview.includeDriver)" (제외)" else "",Modifier.weight(1f))
                    Text(Formatters.won(share.amountWon))
                }
            }
            Note("나누고 남은 금액은 참여자 순서대로 1원씩 배분해요.")
            HorizontalDivider()
            Text("분담액 합계   ${Formatters.won(preview.totalWon)}",fontSize=20.sp)
        }
    }
    Note("주행거리 ${preview.recording.distanceKm?.let{Formatters.km(it)}?:"기록 없음"} · ${Formatters.source(preview.recording.source)}")
    state.loadError?.let{ErrorMessage(it)}
    state.confirmError?.let{ErrorMessage(it)}
    if(!preview.canConfirm)preview.blockingReason?.let{ErrorMessage(it.userMessage())}
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)) {
        Box(Modifier.weight(1f)) { Note("✓  확정 전에 비용과 인원을 한 번 더 확인해 주세요.") }
        Action(if(state.isConfirming) "저장 중…" else "이 금액으로 확정",arrow=true,
            enabled=preview.canConfirm&&!locked&&state.nameError==null&&state.loadError==null,onClick=onRequestConfirm)
    }
}

@Composable private fun SurfaceSummary(preview:PaymentPreview) {
    androidx.compose.material3.Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1.15f).fillMaxHeight().background(Navy).padding(28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("이번 여행에 함께 쓴 비용",color=Color(0xFFC5D9E8))
                AmountLabel(Formatters.won(preview.totalWon).removeSuffix("원"),"원",44,Color.White)
                Text("포함 ${preview.includedCount}건 / 등록 ${preview.registeredCount}건",color=Color(0xFFC5D9E8))
            }
            Column(Modifier.weight(1f).padding(28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("${preview.splitPeople}명이 함께 나눠요",fontSize=26.sp)
                Note("주유 ${Formatters.won(preview.fuelTotalWon)}")
                Note("주차·톨비·기타 ${Formatters.won(preview.otherTotalWon)}")
                Note("정산에 포함한 실제 결제 금액을 나눠요.")
            }
        }
    }
}
