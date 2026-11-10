package com.gatita.feature.trip

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
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
        GatitaScaffold("여행 완료") {
            Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.TopCenter) {
                Column(Modifier.widthIn(max=700.dp).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Brand(false)
                    Note("함께한 여정, 기록 완료")
                    Text("안전하게 도착했어요",fontSize=36.sp,fontWeight=FontWeight.Bold)
                    Note("수고했어요. 함께한 여행을 돌아볼까요?")
                    com.gatita.feature.publishing.MotionSection(true,180,14f) {
                        Panel(Modifier.fillMaxWidth()) {
                            Note("${state.trip?.tripDate}")
                            Text(state.trip?.name.orEmpty(),fontSize=24.sp,fontWeight=FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(28.dp)) {
                                Column(Modifier.weight(1f)) { Note("함께 이동한 거리");Text(state.recordedDistance?:"미기록",fontSize=36.sp,color=MaterialTheme.colorScheme.primary) }
                                Column(Modifier.weight(1f)) { Note("구간 경과 시간");Text(state.recordedMinutes?.let { "$it 분" }?:"미기록",fontSize=32.sp) }
                            }
                            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                            Note("${state.segments.size}개 구간 기록 · 함께 탄 ${state.trip?.people}명")
                        }
                    }
                    Note("비용 ${state.ledger?.includedCount?:0}건 · ${Formatters.won(state.ledger?.includedTotalWon?:0)}")
                    Text("이제 함께 쓴 비용을 나눠보세요.")
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Action("나중에 정산",primary=false,modifier=Modifier.weight(1f)){onBack()}
                        Action("정산하러 가기",modifier=Modifier.weight(1.35f),arrow=true){onOpenSettlement(id)}
                    }
                    Note("나중에 정산해도 ‘정산’ 메뉴에서 이어갈 수 있어요.")
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
    GatitaScaffold(title = if(showCosts)"이번 여행 비용" else if(state.pausedAt!=null) "잠시 멈춘 여행" else state.trip?.name?:"여행 기록",
        onBack = if(showCosts)({showCosts=false}) else onBack,
        subtitle=if(showCosts) state.trip?.name.orEmpty() else if(state.pausedAt!=null) "잠시 쉬어도, 함께한 여행은 이어져요." else "${state.trip?.tripDate} · 함께 타는 ${state.trip?.people?:0}명 · 수동 기록") {
        val trip = state.trip
        val ledger = state.ledger
        when {
            state.isLoading -> LoadingBlock("여행 기록을 불러오는 중")
            state.loadError != null -> ErrorBlock(state.loadError, onRetryLoad)
            state.notFound || trip == null || ledger == null ->
                EmptyBlock("여행을 찾을 수 없습니다", "삭제되었거나 저장되지 않은 여행입니다. 홈으로 돌아가세요.")

            else -> {
                val busy=state.isChangingPause||state.isMutating||state.isCompleting||state.isDiscarding||state.isSavingSegment
                state.pauseError?.let { ErrorMessage(it) }
                if(showCosts) {
                    if(ledger.editable) Action("비용 추가",enabled=!busy,onClick=onAddCost)
                    CostLedgerSection(ledger,busy,onIncludedChange,onEditCost,onRequestDelete)
                } else if(trip.status==TripStatus.IN_PROGRESS && state.pausedAt!=null) {
                    PausedTripCard(state,busy,{onPauseChange(false)},onAddCost,{showCosts=true},onRequestComplete)
                } else {
                    androidx.compose.material3.Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1.15f).fillMaxHeight().background(Navy).padding(32.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                                Text(if(trip.status==TripStatus.IN_PROGRESS && state.pausedAt!=null) "잠시 쉬는 중" else Formatters.tripStatus(trip.status),color=Color(0xFFC5D9E8))
                                Text("함께 이동한 거리",color=Color(0xFFC5D9E8))
                                if(state.recordedDistance!=null) AmountLabel(state.recordedDistance.removeSuffix(" km"),"km",72,Color.White) else Text("기록 없음",fontSize=40.sp,color=Color.White)
                                HorizontalDivider(color=Color.White.copy(alpha=.15f))
                                Text("구간 경과 시간   ${state.recordedMinutes?.let{"$it 분"}?:"기록 없음"}",color=Color.White)
                            }
                            Column(Modifier.weight(1f).padding(32.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
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
                    else androidx.compose.foundation.lazy.LazyRow(
                        Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.segments.size) { index ->
                            val row=state.segments[index]
                            Panel(Modifier.width(320.dp)) {
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                    Note("구간 ${row.seq.toString().padStart(2,'0')}")
                                    Text("✓ 저장됨",fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
                                }
                                Text(row.distance,fontSize=30.sp)
                                Note(row.range)
                                Note(row.efficiency)
                            }
                        }
                    }
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
        androidx.compose.ui.window.Dialog(onDismissRequest=onDismissComplete) {
            Panel(Modifier.widthIn(max=480.dp).fillMaxWidth()) {
                IconBadge(com.gatita.R.drawable.ic_flag)
                Text("이번 여행을 마칠까요?",fontSize=26.sp,fontWeight=FontWeight.Bold)
                Note("기록한 구간을 모아\n함께 나눌 비용을 확인해요.")
                Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(22.dp),horizontalArrangement=Arrangement.spacedBy(32.dp)) {
                        Column(Modifier.weight(1f)) { Note("총 이동 거리");Text(state.recordedDistance?:"미기록",fontSize=28.sp) }
                        Column(Modifier.weight(1f)) { Note("기록된 구간");Text("${state.segments.size} 개",fontSize=28.sp) }
                    }
                }
                Note("여행을 마친 뒤 기록을 확인하고 정산할 수 있어요.")
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Action("계속 기록",primary=false,modifier=Modifier.weight(1f),onClick=onDismissComplete)
                    Action("여행 완료",modifier=Modifier.weight(1f),enabled=!state.isCompleting,onClick=onConfirmComplete)
                }
            }
        }
    }
}

@Composable private fun IconBadge(icon:Int) {
    Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
        androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(icon),null,Modifier.padding(14.dp).size(24.dp),tint=MaterialTheme.colorScheme.primary)
    }
}

@Composable private fun PausedTripCard(state:TripUiState,busy:Boolean,onResume:()->Unit,onAdd:()->Unit,onCosts:()->Unit,onFinish:()->Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Note("Ⅱ  이어갈 수 있는 여행")
                Text(state.trip?.name.orEmpty(),fontSize=28.sp,fontWeight=FontWeight.Bold)
                Note("함께 타는 ${state.trip?.people}명")
            }
            IconBadge(com.gatita.R.drawable.ic_route)
        }
        Surface(color=Navy,shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(24.dp),horizontalArrangement=Arrangement.spacedBy(32.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("함께 이동한 거리",color=Color(0xFFC5D9E8));Text(state.recordedDistance?:"미기록",fontSize=40.sp,color=Color.White) }
                Metric("구간 경과 시간",state.recordedMinutes?.let { "$it 분" }?:"— 분",true)
                Metric("기록한 구간","${state.segments.size} 개",true)
            }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f).clickable(onClick=onCosts)) { Text("이번 여행 비용  ${state.ledger?.count?:0}건",fontWeight=FontWeight.SemiBold);Note("등록한 비용도 함께 보관했어요.  ›") }
            Text(Formatters.won(state.ledger?.registeredTotalWon?:0),fontSize=24.sp)
            Action("비용 추가",primary=false,enabled=!busy,onClick=onAdd)
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {Text("✓  여기까지 기록했어요");Note("이 기기에 저장한 여행을 그대로 이어가요.")}
            Action("여행 이어가기",modifier=Modifier.widthIn(min=230.dp),arrow=true,enabled=!busy,onClick=onResume)
        }
    }
    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            IconBadge(com.gatita.R.drawable.ic_flag)
            Column(Modifier.weight(1f)) {Text("목적지에 도착하셨나요?",fontWeight=FontWeight.SemiBold);Note("여행을 마치고 함께 쓴 비용을 확인하세요.")}
            Action("여행 마치기",primary=false,enabled=!busy,onClick=onFinish)
        }
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
