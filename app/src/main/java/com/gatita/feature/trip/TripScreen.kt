package com.gatita.feature.trip

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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatita.core.ui.component.ActionButton
import com.gatita.core.ui.component.ChoiceChips
import com.gatita.core.ui.component.EmptyBlock
import com.gatita.core.ui.component.ErrorBlock
import com.gatita.core.ui.component.ErrorMessage
import com.gatita.core.ui.component.GatitaScaffold
import com.gatita.core.ui.component.InfoCard
import com.gatita.core.ui.component.LabeledValue
import com.gatita.core.ui.component.LoadingBlock
import com.gatita.core.ui.component.NoticeMessage
import com.gatita.core.ui.component.NumberField
import com.gatita.core.ui.component.SecondaryButton
import com.gatita.core.ui.component.SectionTitle
import com.gatita.core.ui.component.SourceLabel
import com.gatita.domain.model.Cost
import com.gatita.domain.model.EfficiencyUnit
import com.gatita.domain.model.TripStatus
import com.gatita.feature.common.Formatters

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.gatita.core.ui.component.*
import com.gatita.core.ui.theme.Navy

@Composable
fun TripRoute(
    viewModel: TripViewModel,
    onBack: () -> Unit,
    onAddCost: (Long) -> Unit,
    onEditCost: (tripId: Long, costId: Long) -> Unit,
    onOpenSettlement: (Long) -> Unit,
    onOpenResult: (Long) -> Unit,
    onDiscarded: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var completed by rememberSaveable{mutableStateOf<Long?>(null)}
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TripEvent.OpenSettlement -> completed=event.tripId
                TripEvent.Discarded -> onDiscarded()
            }
        }
    }
    completed?.let { id->
        GatitaScaffold("여행을 잘 마쳤어요") {
            Panel(Modifier.fillMaxWidth()) {
                Brand(false)
                Text(state.trip?.name.orEmpty(),fontSize=32.sp,fontWeight=FontWeight.Bold)
                Note("여행 기록과 함께 쓴 비용을 저장했어요.")
                Text(Formatters.won(state.ledger?.includedTotalWon?:0),fontSize=40.sp)
                Note("${state.ledger?.includedCount?:0}건의 비용 · ${state.segments.size}개 구간")
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Action("정산하러 가기",arrow=true){onOpenSettlement(id)}
                    Action("나중에 정산",primary=false){onBack()}
                }
            }
        }
        return
    }
    TripContent(
        state = state,
        onBack = onBack,
        onRetryLoad = viewModel::observe,
        onAddCost = { state.trip?.let { onAddCost(it.id) } },
        onEditCost = { cost -> state.trip?.let { onEditCost(it.id, cost.id) } },
        onIncludedChange = viewModel::setIncluded,
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = viewModel::dismissDelete,
        onEndOdometerChange = viewModel::onEndOdometerChange,
        onEfficiencyChange = viewModel::onEfficiencyChange,
        onEfficiencyUnitChange = viewModel::onEfficiencyUnitChange,
        onSaveSegment = viewModel::saveSegment,
        onRequestComplete = viewModel::requestComplete,
        onConfirmComplete = viewModel::confirmComplete,
        onDismissComplete = viewModel::dismissComplete,
        onRequestDiscard = viewModel::requestDiscard,
        onConfirmDiscard = viewModel::confirmDiscard,
        onDismissDiscard = viewModel::dismissDiscard,
        onOpenSettlement = { state.trip?.let { onOpenSettlement(it.id) } },
        onOpenResult = { state.trip?.let { onOpenResult(it.id) } },
        onPauseChange = viewModel::changePaused,
    )
}

@Composable
fun TripContent(
    state: TripUiState,
    onBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onAddCost: () -> Unit,
    onEditCost: (Cost) -> Unit,
    onIncludedChange: (Cost, Boolean) -> Unit,
    onRequestDelete: (Cost) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onEndOdometerChange: (String) -> Unit,
    onEfficiencyChange: (String) -> Unit,
    onEfficiencyUnitChange: (EfficiencyUnit) -> Unit,
    onSaveSegment: () -> Unit,
    onRequestComplete: () -> Unit,
    onConfirmComplete: () -> Unit,
    onDismissComplete: () -> Unit,
    onRequestDiscard: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissDiscard: () -> Unit,
    onOpenSettlement: () -> Unit,
    onOpenResult: () -> Unit,
    onPauseChange: (Boolean) -> Unit = {},
) {
    var showCosts by rememberSaveable { mutableStateOf(false) }
    var showSegmentForm by rememberSaveable { mutableStateOf(false) }
    GatitaScaffold(title = if(showCosts)"이번 여행 비용" else state.trip?.name?:"여행 기록",
        onBack = if(showCosts)({showCosts=false}) else onBack) {
        val trip = state.trip
        val ledger = state.ledger
        when {
            state.isLoading -> LoadingBlock("여행 기록을 불러오는 중")
            state.loadError != null -> ErrorBlock(state.loadError, onRetryLoad)
            state.notFound || trip == null || ledger == null ->
                EmptyBlock("여행을 찾을 수 없습니다", "삭제되었거나 저장되지 않은 여행입니다. 홈으로 돌아가세요.")

            else -> {
                val busy=state.isChangingPause||state.isMutating||state.isCompleting||state.isDiscarding||state.isSavingSegment
                Note("${trip.tripDate} · 함께 타는 ${trip.people}명 · ${Formatters.source(trip.source)}")
                if(!showCosts && trip.status==TripStatus.IN_PROGRESS && state.pausedAt!=null) {
                    Panel(Modifier.fillMaxWidth()) {
                        Text("잠시 멈춘 여행",fontSize=28.sp,fontWeight=FontWeight.Bold)
                        Note("여기까지의 구간과 비용을 보관했어요. 같은 여행을 이어갈 수 있어요.")
                        Action("여행 이어가기",enabled=!busy) { onPauseChange(false) }
                    }
                }
                state.pauseError?.let { ErrorMessage(it) }
                if(showCosts) {
                    if(ledger.editable) Action("비용 추가",enabled=!busy,onClick=onAddCost)
                    CostLedgerSection(ledger,busy,onIncludedChange,onEditCost,onRequestDelete)
                } else {
                    androidx.compose.material3.Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1.15f).fillMaxHeight().background(Navy).padding(26.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                                Text(if(trip.status==TripStatus.IN_PROGRESS && state.pausedAt!=null) "잠시 쉬는 중" else Formatters.tripStatus(trip.status),color=Color(0xFFC5D9E8))
                                Text("함께 이동한 거리",color=Color(0xFFC5D9E8))
                                Text(state.recordedDistance?:"기록 없음",fontSize=44.sp,color=Color.White)
                                HorizontalDivider(color=Color.White.copy(alpha=.15f))
                                Text("구간 경과 시간   ${state.recordedMinutes?.let{"$it 분"}?:"기록 없음"}",color=Color.White)
                            }
                            Column(Modifier.weight(1f).padding(26.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                                Note("TRIP STATUS")
                                Text(if(trip.status==TripStatus.IN_PROGRESS && state.pausedAt!=null)"기록을 안전하게\n보관하고 있어요" else if(trip.status==TripStatus.IN_PROGRESS)"여행은 계속\n이어지고 있어요" else "함께한 여행을\n기록했어요",fontSize=28.sp,fontWeight=FontWeight.Bold)
                                Note("차량 자동 기록은 연결 전이에요.\n필요할 때 구간을 직접 저장해 주세요.")
                                HorizontalDivider()
                                Text("저장된 구간  ${state.segments.size}개",fontSize=20.sp)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("이번 여행의 기록 · ${state.segments.size}개 구간",Modifier.weight(1f),fontWeight=FontWeight.Bold)
                        if(trip.status==TripStatus.IN_PROGRESS && state.pausedAt==null) TextButton({showSegmentForm=!showSegmentForm},enabled=!busy){Text(if(showSegmentForm)"입력 닫기" else "구간 기록 +")}
                    }
                    if(state.segments.isEmpty()) Note("아직 저장된 구간이 없어요. 비용만 기록해도 정산할 수 있어요.")
                    else state.segments.forEach{ row->Panel(Modifier.fillMaxWidth()){
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("구간 ${row.seq}");Text(row.distance,fontSize=22.sp)}
                        Note(row.range+" · "+row.efficiency)
                    }}
                    if(showSegmentForm&&trip.status==TripStatus.IN_PROGRESS&&state.pausedAt==null) Panel(Modifier.fillMaxWidth()) {
                        SegmentForm(state,onEndOdometerChange,onEfficiencyChange,onEfficiencyUnitChange,onSaveSegment)
                    }
                    Panel(Modifier.fillMaxWidth().clickable{showCosts=true}) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)){Text("이번 여행 비용",fontWeight=FontWeight.Bold);Note("주유·주차·톨비를 한곳에서 기록해요.")}
                            Text("${ledger.count}건 · ${Formatters.won(ledger.registeredTotalWon)}  ›")
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                        if(trip.status==TripStatus.IN_PROGRESS && state.pausedAt==null) {
                            Action("잠시 멈추기",primary=false,enabled=!busy) { onPauseChange(true) }
                            Spacer(Modifier.width(12.dp))
                        }
                        if(ledger.editable)Action("비용 추가",primary=false,enabled=!busy,onClick=onAddCost)
                        Spacer(Modifier.width(12.dp))
                        when(trip.status) {
                            TripStatus.IN_PROGRESS->Action(if(state.isCompleting)"저장 중…" else "여행 마치기",enabled=!busy,onClick=onRequestComplete)
                            TripStatus.FINISHED->Action("정산하기",onClick=onOpenSettlement)
                            TripStatus.SETTLED->Action("정산 결과 보기",onClick=onOpenResult)
                        }
                    }
                    if(trip.status==TripStatus.IN_PROGRESS&&state.segments.isEmpty()&&ledger.count==0) DiscardSection(state,onRequestDiscard)
                }
                state.costError?.let{ErrorMessage(it)}
                state.completeError?.let{ErrorMessage(it)}
            }
        }
    }

    state.deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("이 비용을 삭제할까요?") },
            text = {
                Text(
                    "${Formatters.costKind(target.kind)} ${Formatters.won(target.amountWon)}을(를) 삭제합니다. " +
                        "정산에서 빼기만 하려면 삭제하지 말고 '정산에서 제외'를 선택하세요.",
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete, modifier = Modifier.heightIn(min = 48.dp)) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete, modifier = Modifier.heightIn(min = 48.dp)) { Text("취소") }
            },
        )
    }

    if (state.showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = onDismissDiscard,
            title = { Text("빈 여행을 닫을까요?") },
            text = {
                Text(
                    "구간, 주행거리, 비용이 모두 없는 이 여행 기록을 삭제하고 홈으로 돌아갑니다. " +
                        "삭제한 기록은 되돌릴 수 없습니다. 그 사이 구간이나 비용이 생겼다면 삭제하지 않습니다.",
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDiscard, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
            },
            dismissButton = {
                TextButton(onClick = onDismissDiscard, modifier = Modifier.heightIn(min = 48.dp)) { Text("취소") }
            },
        )
    }

    if (state.showCompleteConfirm) {
        AlertDialog(
            onDismissRequest = onDismissComplete,
            title = { Text("여행을 마칠까요?") },
            text = {
                Text(
                    "마친 뒤에는 구간을 더 기록할 수 없고 정산 단계로 넘어갑니다. 비용은 정산 확정 전까지 수정할 수 있습니다. " +
                        "저장된 구간이 없으면 거리 미기록으로 표시합니다.",
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmComplete, modifier = Modifier.heightIn(min = 48.dp)) { Text("마치기") }
            },
            dismissButton = {
                TextButton(onClick = onDismissComplete, modifier = Modifier.heightIn(min = 48.dp)) { Text("취소") }
            },
        )
    }
}

@Composable
private fun SegmentCard(row: SegmentRow) {
    InfoCard {
        Text("구간 ${row.seq} · ${row.distance}", style = MaterialTheme.typography.titleMedium)
        Text(row.range, style = MaterialTheme.typography.bodyMedium)
        Text("연비(기록용): ${row.efficiency}", style = MaterialTheme.typography.bodyMedium)
        SourceLabel(row.source)
    }
}

@Composable
private fun SegmentForm(
    state: TripUiState,
    onEndOdometerChange: (String) -> Unit,
    onEfficiencyChange: (String) -> Unit,
    onEfficiencyUnitChange: (EfficiencyUnit) -> Unit,
    onSaveSegment: () -> Unit,
) {
    val locked = state.isSavingSegment
    SectionTitle("구간 추가 · 수동 입력")
    Text(
        "구간 시작: ${state.lastRecordedKm ?: "여행 시작 주행거리"} (이전 기록에서 이어짐). " +
            "이 값은 기록용이며 정산 금액 계산에 쓰이지 않습니다.",
        style = MaterialTheme.typography.bodyMedium,
    )
    NumberField(
        label = "구간 종료 주행거리 (km) · 수동 입력",
        value = state.endOdometerInput,
        onValueChange = onEndOdometerChange,
        errorText = state.endOdometerError,
        enabled = !locked,
    )
    Text("구간 연비 단위 (선택)", style = MaterialTheme.typography.labelLarge)
    ChoiceChips(
        options = EfficiencyUnit.entries.toList(),
        selected = state.efficiencyUnit,
        label = { Formatters.unitLabel(it) },
        onSelect = onEfficiencyUnitChange,
        enabled = !locked,
    )
    NumberField(
        label = "구간 평균 연비 (${Formatters.unitLabel(state.efficiencyUnit)}) · 선택",
        value = state.efficiencyInput,
        onValueChange = onEfficiencyChange,
        errorText = state.efficiencyError,
        supporting = "모르면 비워 두세요. 비우면 '연비 미기록'으로 저장하며 0으로 바꾸지 않습니다.",
        enabled = !locked,
        imeAction = ImeAction.Done,
    )
    state.segmentError?.let { ErrorMessage(it) }
    state.segmentNotice?.let { NoticeMessage(it) }
    SecondaryButton(
        text = if (state.isSavingSegment) "저장하는 중…" else "구간 저장",
        onClick = onSaveSegment,
        enabled = !locked && !state.isCompleting,
    )
}

@Composable
private fun DiscardSection(state: TripUiState, onRequestDiscard: () -> Unit) {
    SectionTitle("잘못 시작한 여행")
    Text(
        "구간과 비용을 기록하지 않았다면 이 빈 여행을 닫을 수 있습니다. 기록이 있는 여행은 닫을 수 없습니다.",
        style = MaterialTheme.typography.bodyMedium,
    )
    state.discardError?.let { ErrorMessage(it) }
    SecondaryButton(
        text = if (state.isDiscarding) "닫는 중…" else "빈 여행 닫기",
        onClick = onRequestDiscard,
        enabled = !state.isDiscarding && !state.isCompleting && !state.isSavingSegment && !state.isMutating,
    )
}

@Composable
private fun CompleteSection(state: TripUiState, onRequestComplete: () -> Unit) {
    SectionTitle("여행 마치기")
    Text(
        "결제한 비용을 모두 입력했다면 여행을 마치고 정산으로 넘어가세요. 거리를 기록하지 않은 0km 여행도 비용이 있으면 마칠 수 있습니다.",
        style = MaterialTheme.typography.bodyMedium,
    )
    state.completeError?.let { ErrorMessage(it) }
    ActionButton(
        text = "여행 마치고 정산하기",
        onClick = onRequestComplete,
        busy = state.isCompleting,
        busyText = "마치는 중…",
        enabled = !state.isSavingSegment && !state.isMutating,
    )
}
