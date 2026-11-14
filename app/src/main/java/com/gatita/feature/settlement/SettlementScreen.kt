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
        onManualPriceChange = viewModel::onManualPriceChange,
        onSaveManualPrice = viewModel::saveManualPrice,
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
    onManualPriceChange: (String) -> Unit = {},
    onSaveManualPrice: () -> Unit = {},
) {
    GatitaScaffold(title = "정산 미리보기", onBack = onBack, subtitle="함께 쓴 비용을 확인하고, 편하게 나눠요.") {
        val preview = state.preview
        when {
            state.isLoading -> LoadingBlock("정산을 계산하는 중")
            state.notFound -> EmptyBlock("여행을 찾을 수 없어요", "삭제되었거나 저장되지 않은 여행이에요. 홈으로 돌아가 주세요.")
            preview == null -> ErrorBlock(state.loadError ?: "정산을 불러오지 못했어요.", onRetry)
            else -> PreviewBody(
                state, preview, onDriverNameChange, onPeopleChange, onIncludeDriverChange,
                onRequestConfirm, onEditCosts, onManualPriceChange, onSaveManualPrice,
            )
        }
    }

    if (state.showConfirm && state.preview != null) {
        com.gatita.core.ui.component.VehicleAlertDialog(
            onDismissRequest = onDismissConfirm,
            title = { Text("정산을 확정할까요?") },
            text = {
                Text(
                    (if (state.preview.fuel != null) "구간별 유류비와 기타 비용을 합친 ${Formatters.won(state.preview.totalWon)}을 "
                    else "정산에 포함한 실제 결제 금액 합계 ${Formatters.won(state.preview.totalWon)}을 ") +
                        "${state.preview.splitPeople}명이 나눈 결과를 저장해요. 확정하면 비용·인원·분담액을 수정할 수 없어요. " +
                        "송금이나 입금은 이 앱에서 처리하지 않아요.",
                )
            },
            confirmButton = {
                Action("확정", modifier = Modifier.weight(1f), onClick = onConfirm)
            },
            dismissButton = {
                Action("취소", primary = false, modifier = Modifier.weight(1f), onClick = onDismissConfirm)
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
    onManualPriceChange: (String) -> Unit,
    onSaveManualPrice: () -> Unit,
) {
    val locked = state.isUpdatingPolicy || state.isConfirming
    var breakdown by rememberSaveable { mutableStateOf(false) }
    androidx.compose.material3.Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // [목적] 정산 미리보기 총액 카드 (남색)
            // [이유] 오른쪽 칸 높이에 맞춰 늘어나며 아래 절반이 비어, 총액은 위·비용 구성은 아래에 붙도록 SpaceBetween 배치
            // [사이드 임팩트] 없음 (배치만 변경)
            // [수정 시 고려사항] 위 묶음(제목·총액·구성)과 아래 묶음(포함 비용 건수) 두 덩어리를 유지할 것
            Column(Modifier.weight(1f).fillMaxHeight().background(Navy).padding(32.dp),verticalArrangement=Arrangement.SpaceBetween) {
              Column(verticalArrangement=Arrangement.spacedBy(24.dp)) {
                Text("함께 나눌 총금액",fontSize=20.sp,color=Color.White)
                AmountLabel(Formatters.won(preview.totalWon).removeSuffix("원"),"원",64,Color.White)
                Text("주유비 ${Formatters.won(preview.fuelTotalWon)} · 기타 비용 ${Formatters.won(preview.otherTotalWon)}",fontSize=16.sp,color=Color(0xFFC5D9E8))
              }
              Column(verticalArrangement=Arrangement.spacedBy(20.dp)) {
                HorizontalDivider(color=Color.White.copy(alpha=.15f))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("정산에 포함한 비용",fontSize=16.sp,color=Color(0xFFC5D9E8));Text("${preview.includedCount}건",color=Color.White)
                }
              }
            }
            Column(Modifier.weight(1f).padding(32.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Text("${preview.splitPeople}명이 함께 나누면",fontSize=20.sp)
                val amounts=preview.shares.filter{preview.includeDriver||!it.isDriver}.map{it.amountWon}
                val min=amounts.minOrNull()?:0;val max=amounts.maxOrNull()?:0
                AmountLabel(if(min==max)Formatters.won(min).removeSuffix("원") else "${Formatters.won(min).removeSuffix("원")}–${Formatters.won(max).removeSuffix("원")}","원 / 인",42,MaterialTheme.colorScheme.primary)
                Note(if(preview.includeDriver)"운전자를 포함한 균등 분담" else "운전자를 제외한 균등 분담")
                // [목적] 개인별 명세 펼치기 버튼
                // [이유] 동승자별 금액 확인은 확정 전 핵심 점검인데 작은 글자 링크라 잘 안 보여 보조 버튼으로 키움
                // [사이드 임팩트] 없음
                // [수정 시 고려사항] 펼친 목록은 바로 아래에 렌더링됨
                Action(if(breakdown)"개인별 명세 접기" else "개인별 명세 ${preview.splitPeople}명 보기",primary=false,textSize=16){breakdown=!breakdown}
                // ponytail: 명세가 뚝 튀어나오지 않게 펼침 애니메이션. 안쪽 Column 간격은 카드 간격과 맞춤
                androidx.compose.animation.AnimatedVisibility(breakdown) { Column(verticalArrangement=Arrangement.spacedBy(12.dp)) { state.shares.forEach { share ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(share.label+if(share.isDriver&&!preview.includeDriver)" (제외)" else "");Text(Formatters.won(share.amountWon))}
                } } }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                StepperRow("함께 탄 인원",preview.people,1..8,onPeopleChange,enabled=!locked)
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                SwitchRow("나 (운전자) 포함",preview.includeDriver,onIncludeDriverChange,enabled=!locked&&preview.people>1,
                    supporting=if(preview.people==1)"혼자 탄 여행은 본인이 부담해요." else "나도 여행 비용을 함께 나눠요.")
                state.policyError?.let{ErrorMessage(it)}
            }
        }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
        Panel(Modifier.weight(1f)) {
            SectionTitle("나눌 주유 내역")
            if(preview.fuel?.fuelNotIncluded==true) {
                Text("유류비 미포함",fontSize=24.sp)
                Note("측정값이나 단가가 없어 유류비를 계산하지 못했어요. 나머지 비용만 나눠요.")
            } else {
                AmountLabel(Formatters.won(preview.fuelTotalWon).removeSuffix("원"),"원",30)
                Note(if(preview.fuel!=null)"구간별 사용량 × 주유 단가로 계산했어요." else "정산에 포함한 주유 결제 금액이에요.")
            }
        }
        Panel(Modifier.weight(1f)) {
            SectionTitle("주차·톨비·기타 비용")
            AmountLabel(Formatters.won(preview.otherTotalWon).removeSuffix("원"),"원",30)
            Note("이번 여행에서 함께 쓴 그 밖의 비용이에요.")
        }
    }
    // [목적] 비용 내역으로 가는 버튼 (하나로 통합)
    // [이유] 두 카드에 각각 있던 '비용 내역 확인'·'비용 추가·수정'이 같은 화면으로 가는 14sp·44dp 작은 버튼이라, 표준 크기 보조 버튼 하나로 합침
    // [사이드 임팩트] 없음 (onEditCosts 콜백 그대로)
    // [수정 시 고려사항] 확정 버튼(주요)보다 눈에 띄지 않게 보조 버튼으로 유지
    Action("비용 내역 확인·수정",primary=false,textSize=16,onClick=onEditCosts)
    preview.fuel?.let { fuel ->
        FuelIntervalPanel(fuel)
        if(fuel.intervals.size==1) {
            TextInput("주유 단가 직접 입력 (원/L) · 선택",state.manualPriceInput,onManualPriceChange,errorText=state.manualPriceError,enabled=!locked&&!state.isSavingPrice)
            Action(if(state.isSavingPrice)"저장 중…" else "단가 적용",primary=false,textSize=16,enabled=!locked&&!state.isSavingPrice,onClick=onSaveManualPrice)
            Note("이번 여행에서 주유를 감지하지 못했어요. 단가를 입력하지 않아도 정산할 수 있고, 그때는 유류비가 포함되지 않아요.")
        }
    }
    TextInput("정산에 표시할 이름 · 선택",state.driverNameInput,onDriverNameChange,errorText=state.nameError,enabled=!locked,placeholder="나 (운전자)")
    Note(preview.recording.distanceKm?.let{"주행거리 ${Formatters.km(it)} · ${Formatters.source(preview.recording.source)}"}?:"주행거리 기록 없음")
    state.loadError?.let{ErrorMessage(it)}
    state.confirmError?.let{ErrorMessage(it)}
    if(!preview.canConfirm)preview.blockingReason?.let{ErrorMessage(it.userMessage())}
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    // [목적] 정산 확정 버튼 줄
    // [이유] 다른 화면처럼 주요 버튼을 운전석 쪽(왼쪽)에 두고, '✓' 글자 아이콘을 제거
    // [사이드 임팩트] 없음 (배치만 변경)
    // [수정 시 고려사항] 확정은 대화상자에서 한 번 더 확인함
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)) {
        Action(if(state.isConfirming) "저장 중…" else "이 금액으로 확정",arrow=true,
            enabled=preview.canConfirm&&!locked&&state.nameError==null&&state.loadError==null,onClick=onRequestConfirm)
        Box(Modifier.weight(1f)) { Note("확정 전에 비용과 인원을 한 번 더 확인해 주세요.") }
    }
}

/** 주유 구간별 계산 근거. 측정이 없거나 단가가 없는 구간은 0원이 아니라 제외로 표시한다. */
@Composable
private fun FuelIntervalPanel(fuel: com.gatita.domain.model.FuelSettlementResult) {
    Panel(Modifier.fillMaxWidth()) {
        SectionTitle("구간별 유류비")
        fuel.intervals.forEach { interval ->
            val detail = if(interval.exclusion == null) {
                "${Formatters.km(interval.distanceKm)} · 사용량 ${interval.usage?.let{Formatters.decimal(it,2)}} × ${interval.unitPriceWonPerLiter?.let{Formatters.decimal(it,2)}}원"
            } else when(interval.exclusion) {
                com.gatita.domain.model.FuelExclusion.MEASUREMENT_MISSING -> "${Formatters.km(interval.distanceKm)} · 차량 측정이 없어 유류비에서 제외"
                else -> "${Formatters.km(interval.distanceKm)} · 적용할 단가가 없어 유류비에서 제외"
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("구간 ${interval.ordinal+1}",fontSize=15.sp)
                Text(interval.costWonExact?.let{Formatters.wonDecimal(it)}?:"제외",fontSize=15.sp)
            }
            Note(detail)
        }
        if(fuel.excludedIntervalCount>0&&!fuel.fuelNotIncluded)Note("제외된 구간은 평균이나 0으로 채우지 않았어요.")
        Note("각 구간 금액은 반올림하지 않고 합친 뒤 원 단위로 한 번만 반올림해요.")
    }
}
