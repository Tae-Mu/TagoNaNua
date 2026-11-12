package com.gatita.feature.trip

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    completed?.let { id ->
        TripCompletionContent(state,onBack){onOpenSettlement(id)}
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
    androidx.activity.compose.BackHandler(showCosts) { showCosts=false }
    GatitaScaffold(title = if(showCosts)"이번 여행 비용" else if(state.pausedAt!=null) "잠시 멈춘 여행" else state.trip?.name?:"여행 기록",
        onBack = if(showCosts)({showCosts=false}) else if(state.trip?.status==TripStatus.IN_PROGRESS)null else onBack,
        subtitle=if(showCosts) state.trip?.name.orEmpty() else if(state.pausedAt!=null) "쉬어가는 동안에도 여행 기록은 그대로예요." else "${state.trip?.tripDate?.let(Formatters::shortDate).orEmpty()} · 함께 타는 ${state.trip?.people?:0}명") {
        val trip = state.trip
        val ledger = state.ledger
        when {
            state.isLoading -> LoadingBlock("여행 기록을 불러오는 중")
            state.loadError != null -> ErrorBlock(state.loadError, onRetryLoad)
            state.notFound || trip == null || ledger == null ->
                EmptyBlock("여행을 찾을 수 없어요", "삭제되었거나 저장되지 않은 여행이에요. 홈으로 돌아가 주세요.")

            else -> {
                val busy=state.isChangingPause||state.isMutating||state.isCompleting||state.isDiscarding||state.isSavingSegment
                state.pauseError?.let { ErrorMessage(it) }
                if(showCosts) {
                    CostLedgerSection(ledger,busy,onIncludedChange,onEditCost,onRequestDelete,onAddCost)
                } else if(trip.status==TripStatus.IN_PROGRESS && state.pausedAt!=null) {
                    PausedTripCard(state,busy,{onPauseChange(false)},onAddCost,{showCosts=true},onRequestComplete)
                } else {
                    androidx.compose.material3.Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1.15f).fillMaxHeight().background(Navy).padding(32.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                                // [목적] 여행 상태 배지 (점 + 상태 글자)
                                // [이유] '기록 중'이 작은 회색 글자라 가장 중요한 상태가 아래 라벨과 구분되지 않아, 점이 붙은 배지로 강조
                                // [사이드 임팩트] 없음 (표시만 변경)
                                // [수정 시 고려사항] 기록 중일 때만 초록 점, 그 외 상태는 연한 점
                                val recording=trip.status==TripStatus.IN_PROGRESS && state.pausedAt==null
                                androidx.compose.material3.Surface(color=Color.White.copy(alpha=.12f),shape=androidx.compose.foundation.shape.RoundedCornerShape(999.dp)) {
                                    Row(Modifier.padding(horizontal=14.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        Box(Modifier.size(9.dp).background(if(recording) Color(0xFF7ED3A2) else Color(0xFFC5D9E8),androidx.compose.foundation.shape.CircleShape))
                                        Text(if(trip.status==TripStatus.IN_PROGRESS && state.pausedAt!=null) "잠시 쉬는 중" else Formatters.tripStatus(trip.status),fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=Color.White)
                                    }
                                }
                                Text("함께 이동한 거리",color=Color(0xFFC5D9E8))
                                if(state.recordedDistance!=null) AmountLabel(state.recordedDistance.removeSuffix(" km"),"km",110,Color.White) else Text("구간 기록 대기",fontSize=32.sp,color=Color.White)
                                // [목적] 기록된 주행 시간 표시 (값이 있을 때만)
                                // [이유] 값이 없을 때 바로 위 '구간 기록 대기'와 같은 말을 반복해, 시간 줄 자체를 숨김
                                // [사이드 임팩트] 없음 (표시만 변경)
                                // [수정 시 고려사항] recordedMinutes가 null이면 측정 실패/미수신이므로 0으로 대체하지 말 것 (AGENTS.md)
                                state.recordedMinutes?.let { minutes ->
                                    HorizontalDivider(color=Color.White.copy(alpha=.15f))
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                                        Text("함께한 시간",fontSize=17.sp,color=Color(0xFFC5D9E8))
                                        AmountLabel("%02d:%02d".format(minutes/60,minutes%60),"시:분",28,Color.White)
                                    }
                                }
                            }
                            Column(Modifier.weight(1f).padding(32.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                                Text(if(trip.status==TripStatus.IN_PROGRESS && state.pausedAt!=null)"기록을 안전하게\n보관하고 있어요" else if(trip.status==TripStatus.IN_PROGRESS)"여행은 계속\n이어지고 있어요" else "함께한 여행을\n기록했어요",fontSize=28.sp,fontWeight=FontWeight.Bold)
                                Note("이동 거리와 구간은 차량에서 받아요.")
                                HorizontalDivider()
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Note("저장된 구간");AmountLabel("${state.segments.size}","개",30) }
                            }
                        }
                    }
                    // [목적] 저장된 구간 목록 (구간이 있을 때만)
                    // [이유] 0개일 때 제목·안내가 위 카드의 '구간 기록 대기'·'저장된 구간 0개'를 반복해 숨김
                    // [사이드 임팩트] 없음 (표시만 변경)
                    // [수정 시 고려사항] 빈 상태 안내는 위 기록 카드가 맡으므로 여기에 다시 넣지 말 것
                    if(state.segments.isNotEmpty()) Text("이번 여행의 기록 · ${state.segments.size}개 구간",fontWeight=FontWeight.Bold)
                    if(state.segments.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(
                        Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.segments.size) { index ->
                            val row=state.segments[index]
                            Panel(Modifier.width(320.dp)) {
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                    Note("구간 ${row.seq.toString().padStart(2,'0')}")
                                    Text("저장됨",fontSize=15.sp,color=MaterialTheme.colorScheme.primary)
                                }
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                        Note(row.range.substringBefore(" → "))
                                        val destination=row.range.substringAfter(" → ","")
                                        if(destination.isNotBlank()) Text(destination,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                                    }
                                    AmountLabel(row.distance.removeSuffix(" km"),"km",30)
                                }
                                if(row.efficiency.isNotBlank()) Note(row.efficiency)
                            }
                        }
                    }
                    Panel(Modifier.fillMaxWidth().clickable{showCosts=true}) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            // [목적] 비용 목록으로 가는 요약 줄
                            // [이유] '비용 추가' 버튼과 역할이 겹쳐 보여, 안내를 '목록 보기'로 바꾸고 텍스트 '›'를 공통 아이콘으로 교체
                            // [사이드 임팩트] 없음 (표시만 변경)
                            // [수정 시 고려사항] 화살표는 ChevronRight()로 통일 (유니코드 글리프 금지)
                            Column(Modifier.weight(1f)){Text("이번 여행 비용",fontWeight=FontWeight.Bold);Note("기록한 비용을 보고 정산 포함 여부를 정해요.")}
                            Text("${ledger.count}건 · ${Formatters.won(ledger.registeredTotalWon)}",fontSize=18.sp)
                            Spacer(Modifier.width(8.dp)); ChevronRight()
                        }
                    }
                    // [목적] 여행 화면 주요 버튼 줄
                    // [이유] 운전석(왼쪽)에서 가장 먼 오른쪽 끝에 있어, 출발 준비 화면처럼 왼쪽 정렬하고 주요 버튼을 맨 앞에 둠
                    // [사이드 임팩트] 없음 (배치만 변경, 콜백 그대로)
                    // [수정 시 고려사항] 우핸들 차량을 지원하면 정렬 방향을 운전석 위치에 맞춰야 함
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        when(trip.status) {
                            TripStatus.IN_PROGRESS->Action(if(state.isCompleting)"저장 중…" else "여행 마치기",enabled=!busy,onClick=onRequestComplete)
                            TripStatus.FINISHED->Action("정산하기",onClick=onOpenSettlement)
                            TripStatus.SETTLED->Action("정산 결과 보기",onClick=onOpenResult)
                        }
                        if(ledger.editable)Action("비용 추가",primary=false,enabled=!busy,onClick=onAddCost)
                        if(trip.status==TripStatus.IN_PROGRESS && state.pausedAt==null) Action("잠시 멈추기",primary=false,enabled=!busy) { onPauseChange(true) }
                    }
                    if(trip.status==TripStatus.IN_PROGRESS&&state.segments.isEmpty()&&ledger.count==0) DiscardSection(state,onRequestDiscard)
                }
                state.costError?.let{ErrorMessage(it)}
                state.completeError?.let{ErrorMessage(it)}
            }
        }
    }

    state.deleteTarget?.let { target ->
        com.gatita.core.ui.component.VehicleAlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("이 비용을 삭제할까요?") },
            text = {
                Text(
                    "${Formatters.costKind(target.kind)} ${Formatters.won(target.amountWon)}을 삭제해요. " +
                        "정산에서 빼기만 하려면 삭제하지 말고 '정산 포함'을 꺼 주세요.",
                )
            },
            confirmButton = {
                Action("삭제", modifier = Modifier.weight(1f), onClick = onConfirmDelete)
            },
            dismissButton = {
                Action("취소", primary = false, modifier = Modifier.weight(1f), onClick = onDismissDelete)
            },
        )
    }

    if (state.showDiscardConfirm) {
        VehicleDialog(onDismissRequest=onDismissDiscard) {
            Panel(Modifier.padding(24.dp).widthIn(max=480.dp).fillMaxWidth().verticalScroll(rememberScrollState()),padding=28,gap=20) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    IconBadge(com.gatita.R.drawable.ic_close)
                    IconButton(onDismissDiscard,enabled=!state.isDiscarding) {
                        Icon(androidx.compose.ui.res.painterResource(com.gatita.R.drawable.ic_close),"빈 여행 삭제 창 닫기")
                    }
                }
                Text("이 여행을 삭제할까요?",fontSize=26.sp,fontWeight=FontWeight.Bold)
                Note("아직 기록하지 않은 빈 여행이에요.\n삭제하면 홈으로 돌아가요.")
                Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(state.trip?.name.orEmpty().ifBlank { "새로운 여행" },fontSize=18.sp,fontWeight=FontWeight.SemiBold,
                            maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Note("삭제한 여행은 되돌릴 수 없어요.")
                    }
                }
                state.discardError?.let { ErrorMessage(it) }
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Action("계속 기록",primary=false,modifier=Modifier.weight(1f),enabled=!state.isDiscarding,onClick=onDismissDiscard)
                    Action(if(state.isDiscarding) "삭제 중…" else "빈 여행 삭제",modifier=Modifier.weight(1f),
                        enabled=!state.isDiscarding,onClick=onConfirmDiscard)
                }
            }
        }
    }

    if (state.showCompleteConfirm) {
        VehicleDialog(onDismissRequest=onDismissComplete) {
            Panel(Modifier.padding(24.dp).widthIn(max=500.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    IconBadge(com.gatita.R.drawable.ic_flag)
                    IconButton(onDismissComplete) { Icon(androidx.compose.ui.res.painterResource(com.gatita.R.drawable.ic_close),"여행 완료 창 닫기") }
                }
                Text("이번 여행을 마칠까요?",fontSize=26.sp,fontWeight=FontWeight.Bold)
                Note("여행 기록을 저장하고, 함께 쓴 비용을 확인해요.")
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

@Composable private fun IconBadge(icon:Int,boxSize:Int=52,iconSize:Int=24) {
    Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
        androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(icon),null,Modifier.padding(((boxSize-iconSize)/2).dp).size(iconSize.dp),tint=MaterialTheme.colorScheme.primary)
    }
}

// [목적] 잠시 멈춘 여행 카드 (이어가기·비용·마치기)
// [이유] 제목 위 'Ⅱ' 이름표, '✓' 글자 아이콘, 값이 없을 때 '— 분', 13sp 작은 '＋ 비용 추가' 버튼을 정리하고 '여행 이어가기'를 운전석 쪽(왼쪽)으로 옮김
// [사이드 임팩트] 없음 (표시·배치만 변경, 콜백 그대로)
// [수정 시 고려사항] recordedMinutes가 null이면 0으로 대체하지 말고 숨길 것 (AGENTS.md)
@Composable private fun PausedTripCard(state:TripUiState,busy:Boolean,onResume:()->Unit,onAdd:()->Unit,onCosts:()->Unit,onFinish:()->Unit) {
    Surface(Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
      Column(Modifier.padding(horizontal=34.dp,vertical=28.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(state.trip?.name.orEmpty(),fontSize=30.sp,fontWeight=FontWeight.Bold)
                Note("함께 타는 ${state.trip?.people}명")
            }
            // [목적] 잠시 멈춘 여행 카드의 그림: 눈을 감고 쉬는 마스코트
            // [이유] 경로 아이콘 배지는 '쉬는 중'이라는 상태를 전하지 못해, 스르르 잠드는 모션으로 바꿈
            // [사이드 임팩트] 멈춤 화면 진입 시 모션 1회 재생 후 눈 감은 장면에서 멈춤
            // [수정 시 고려사항] 카드 제목 줄 높이를 넘지 않게 120dp 안에서 유지
            com.gatita.feature.launch.JourneyMascot(Modifier.size(120.dp),com.gatita.R.drawable.tago_pause_rest)
        }
        Surface(color=Navy,shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal=28.dp,vertical=24.dp),horizontalArrangement=Arrangement.spacedBy(30.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("함께 이동한 거리",fontSize=15.sp,color=Color(0xFFC5D9E8))
                    val distance=state.recordedDistance
                    if(distance==null) Text("미기록",fontSize=48.sp,color=Color.White)
                    else AmountLabel(distance.removeSuffix("km").trim(),"km",48,Color.White)
                }
                androidx.compose.material3.VerticalDivider(Modifier.height(68.dp),color=Color.White.copy(alpha=.15f))
                Row(horizontalArrangement=Arrangement.spacedBy(42.dp)) {
                    state.recordedMinutes?.let { Metric("기록 시간","$it 분",true) }
                    Metric("기록한 구간","${state.segments.size} 개",true)
                }
            }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(com.gatita.R.drawable.ic_receipt),null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f).clickable(onClick=onCosts),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text("이번 여행 비용  ${state.ledger?.count?:0}건",fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                Text(if(state.ledger?.count==0) "주유·주차·톨비를 기록해 보세요." else "등록한 비용도 함께 보관했어요.",fontSize=15.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AmountLabel(Formatters.won(state.ledger?.registeredTotalWon?:0).removeSuffix("원"),"원",23)
            Action("비용 추가",primary=false,textSize=16,enabled=!busy,onClick=onAdd)
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            Action("여행 이어가기",modifier=Modifier.widthIn(min=230.dp),minHeight=56,textSize=18,
                trailingIcon=com.gatita.R.drawable.ic_route,enabled=!busy,onClick=onResume)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text("여기까지 기록했어요",fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                Note("이 기기에 저장한 여행을 그대로 이어가요.")
            }
        }
      }
    }
    // [목적] '여행 마치기' 안내를 카드 없이 한 줄로 표시
    // [이유] 위의 여행 카드와 같은 테두리 박스라 보조 동작이 본 카드만큼 무겁게 보였음. 기록 중 화면의 '빈 여행 닫기' 줄과 같은 모양으로 맞춤
    // [사이드 임팩트] 없음 (표시만 변경)
    // [수정 시 고려사항] 버튼 높이 56dp(터치 영역)는 유지할 것
    Row(Modifier.padding(horizontal=8.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text("목적지에 도착하셨나요?",fontSize=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("여행을 마치고 함께 쓴 비용을 확인해요.",fontSize=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Action("여행 마치기",primary=false,textSize=16,enabled=!busy,onClick=onFinish)
    }
}

@Composable
private fun DiscardSection(state: TripUiState, onRequestDiscard: () -> Unit) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(vertical=10.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("실수로 시작하셨나요?",fontSize=16.sp,fontWeight=FontWeight.Medium,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Note("구간과 비용이 없는 여행만 닫을 수 있어요.")
            }
            Action(
                label=if(state.isDiscarding) "닫는 중…" else "빈 여행 닫기",
                primary=false,textSize=16,horizontalPadding=18,
                leadingIcon=com.gatita.R.drawable.ic_close,iconGap=8,
                enabled=!state.isDiscarding && !state.isCompleting && !state.isSavingSegment && !state.isMutating,
                onClick=onRequestDiscard,
            )
        }
        state.discardError?.let { ErrorMessage(it) }
    }
}
@Composable
internal fun TripCompletionContent(state:TripUiState,onBack:()->Unit,onOpenSettlement:()->Unit) {
        GatitaScaffold("",showJourneyBanner=false) {
            Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.TopCenter) {
                Column(Modifier.widthIn(max=700.dp).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=androidx.compose.foundation.shape.RoundedCornerShape(30.dp)) {
                        Box(Modifier.size(100.dp),contentAlignment=Alignment.Center) { androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(com.gatita.R.drawable.ic_tago),null,Modifier.size(72.dp),tint=MaterialTheme.colorScheme.primary) }
                    }
                    Note("함께한 여정, 기록 완료")
                    Text("안전하게 도착했어요",fontSize=36.sp,fontWeight=FontWeight.Bold)
                    Note("수고했어요. 함께한 여행을 돌아볼까요?")
                    com.gatita.feature.publishing.MotionSection(true,180,14f) {
                        Panel(Modifier.fillMaxWidth(),padding=30,gap=16) {
                            Note("${state.trip?.tripDate}")
                            Text(state.trip?.name.orEmpty(),fontSize=24.sp,fontWeight=FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(28.dp)) {
                                Column(Modifier.weight(1f)) { Note("함께 이동한 거리");Text(state.recordedDistance?:"미기록",fontSize=36.sp,color=MaterialTheme.colorScheme.primary) }
                                Column(Modifier.weight(1f)) { Note("함께한 시간");Text(state.recordedMinutes?.let { "$it 분" }?:"미기록",fontSize=32.sp) }
                            }
                            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                            Note("${state.segments.size}개 구간 기록 · 함께 탄 ${state.trip?.people}명")
                        }
                    }
                    Note("비용 ${state.ledger?.includedCount?:0}건 · ${Formatters.won(state.ledger?.includedTotalWon?:0)}")
                    Text("이제 함께 쓴 비용을 나눠보세요.")
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Action("나중에 정산",primary=false,modifier=Modifier.weight(1f)){onBack()}
                        Action("정산하기",modifier=Modifier.weight(1.35f),arrow=true){onOpenSettlement()}
                    }
                    Note("나중에 정산해도 ‘정산’ 메뉴에서 이어갈 수 있어요.")
                }
            }
        }

}
