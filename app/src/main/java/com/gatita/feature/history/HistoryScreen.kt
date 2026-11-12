package com.gatita.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import com.gatita.core.ui.component.*
import com.gatita.feature.publishing.MonthPicker
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatita.core.ui.component.ChoiceChips
import com.gatita.core.ui.component.EmptyBlock
import com.gatita.core.ui.component.ErrorBlock
import com.gatita.core.ui.component.GatitaScaffold
import com.gatita.core.ui.component.InfoCard
import com.gatita.core.ui.component.LoadingBlock
import com.gatita.core.ui.component.SectionTitle
import com.gatita.core.ui.component.SourceLabel
import com.gatita.domain.model.HistoryFilter
import com.gatita.domain.model.TripStatus
import java.time.YearMonth

@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onOpenTrip: (Long) -> Unit,
    onOpenSettlement: (Long) -> Unit,
    onOpenResult: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::observe,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onFilterChange = viewModel::setFilter,
        onMonthChange = viewModel::setMonth,
        onDelete = viewModel::requestDelete,
        onOpen = { row ->
            when (row.status) {
                TripStatus.IN_PROGRESS -> onOpenTrip(row.tripId)
                TripStatus.FINISHED -> onOpenSettlement(row.tripId)
                TripStatus.SETTLED -> onOpenResult(row.tripId)
            }
        },
    )
    state.deleteTarget?.let { target -> com.gatita.core.ui.component.VehicleAlertDialog(
        onDismissRequest=viewModel::dismissDelete,
        title={Text("목록에서 삭제할까요?")},
        text={Column {Text("‘${target.title}’을 여행 내역과 정산 목록에서 제외해요. 확정된 정산 원본은 이 기기에 보관돼요.");state.deleteError?.let { ErrorMessage(it) }}},
        confirmButton={com.gatita.core.ui.component.Action(if(state.deleting)"처리 중…" else "목록에서 삭제",enabled=!state.deleting,modifier=Modifier.weight(1f),onClick=viewModel::confirmDelete)},
        dismissButton={com.gatita.core.ui.component.Action("취소",primary=false,enabled=!state.deleting,modifier=Modifier.weight(1f),onClick=viewModel::dismissDelete)}
    ) }
}

@Composable
fun HistoryContent(
    state: HistoryUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onFilterChange: (HistoryFilter) -> Unit,
    onOpen: (HistoryRow) -> Unit,
    onMonthChange: ((YearMonth)->Unit)? = null,
    onDelete: ((HistoryRow)->Unit)? = null,
) {
    var monthPicker by rememberSaveable{mutableStateOf(false)}
    if(monthPicker)MonthPicker("${state.month.year}년 ${state.month.monthValue}월",{value->
        onMonthChange?.invoke(YearMonth.of(value.substringBefore("년").toInt(),value.substringAfter("년 ").substringBefore("월").toInt()));monthPicker=false
    },{monthPicker=false})
    GatitaScaffold(title = "여행 내역", subtitle="함께 이동한 거리와 나눈 비용을 모았어요.") {
        Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1f)) {
                Note("여행한 달")
                if(onMonthChange!=null)com.gatita.feature.publishing.MonthSelector("${state.month.year}년 ${state.month.monthValue}월",monthPicker){monthPicker=true}
                else MonthBar(state.month,onPreviousMonth,onNextMonth)
            }
            Column { Note("여행");AmountLabel(state.monthlyTripCount.toString(),"회",34) }
            Column { Note("함께 간 거리");AmountLabel(state.monthlyDistance.removeSuffix("km").trim(),"km",34) }
            Column { Note("정산 확정");AmountLabel(state.monthlySettledCount.toString(),"건",34) }
        }
        androidx.compose.material3.HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        ChoiceChips(
            options = HistoryFilter.entries.toList(),
            selected = state.filter,
            label = {
                when (it) {
                    HistoryFilter.ALL -> "전체"
                    HistoryFilter.PENDING -> "정산 전"
                    HistoryFilter.SETTLED -> "정산 확정"
                }
            },
            onSelect = onFilterChange,
            outlined = false,
        )
        when {
            state.error != null -> ErrorBlock(state.error, onRetry)
            state.isLoading -> LoadingBlock("내역을 불러오는 중")
            else -> {
                if (state.rows.isEmpty()) {
                    // [목적] 여행 내역 빈 상태 (필터별 문구)
                    // [이유] '정산 전' 필터에서 결과가 없을 때도 '이 달에 여행이 없습니다'라고 나와 실제 상황과 달랐음
                    // [사이드 임팩트] 없음 (표시만 변경)
                    // [수정 시 고려사항] HistoryFilter 값이 늘면 문구도 추가할 것
                    val (emptyTitle, emptyBody) = when (state.filter) {
                        com.gatita.domain.model.HistoryFilter.PENDING -> "이 달에 정산 전인 여행이 없어요" to "마친 여행은 모두 정산을 확정했어요."
                        com.gatita.domain.model.HistoryFilter.SETTLED -> "이 달에 확정한 정산이 없어요" to "여행을 마치고 정산을 확정하면 여기에 쌓여요."
                        else -> "이 달에 기록한 여행이 없어요" to "여행을 기록하면 여기에 쌓여요. 다른 달도 확인해 보세요."
                    }
                    // ponytail: 빈 화면에는 두리번거리며 기다리는 마스코트를 곁들임 (필터를 바꿔도 다시 재생하지 않음)
                    EmptyBlock(emptyTitle, emptyBody) { com.gatita.feature.launch.JourneyMascot(Modifier.size(210.dp),com.gatita.R.drawable.tago_empty_wait) }
                } else {
                    listOf(state.rows.filter { it.section!=HistorySection.LEGACY }, state.rows.filter { it.section==HistorySection.LEGACY }).filter { it.isNotEmpty() }.forEach { group ->
                    if(group.first().section==HistorySection.LEGACY) SectionTitle(HistorySection.LEGACY.title)
                    androidx.compose.material3.Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                        Column {
                            group.forEachIndexed { index,row ->
                                if(index>0) androidx.compose.material3.HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                                HistoryCard(row,onOpen,onDelete)
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthBar(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(onClick = onPrevious, modifier = Modifier.heightIn(min = 56.dp)) { Text("이전 달") }
        Text("${month.year}년 ${month.monthValue}월", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = onNext, modifier = Modifier.heightIn(min = 56.dp)) { Text("다음 달") }
    }
}

@Composable
private fun HistoryCard(row: HistoryRow, onOpen: (HistoryRow) -> Unit, onDelete: ((HistoryRow)->Unit)?) {
    Column(Modifier.fillMaxWidth().clickable(role=Role.Button,onClickLabel="${row.title} 열기"){onOpen(row)}.padding(horizontal=24.dp,vertical=20.dp)) {
        Row(Modifier.heightIn(min=76.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(22.dp)) {
            // [목적] 여행 날짜 블록 (월 위, 일 크게)
            // [이유] '11 / 11월'처럼 일과 월이 같으면 어느 쪽이 날짜인지 모호해 '일' 단위를 붙임
            // [사이드 임팩트] 없음 (표시만 변경)
            // [수정 시 고려사항] row.date는 yyyy-MM-dd 문자열 전제
            Column {Note("${row.date.substringAfter("-").substringBefore("-").toIntOrNull()?:""}월");AmountLabel(row.date.takeLast(2).toIntOrNull()?.toString()?:row.date.takeLast(2),"일",28)}
            androidx.compose.material3.VerticalDivider(Modifier.height(56.dp),color=MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(row.title,fontSize=20.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold);Note(listOfNotNull(row.distance,row.costSummary).joinToString(" · "))}
            Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                row.amount?.let { AmountLabel(it.removeSuffix("원"),"원",26) }
                androidx.compose.material3.Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=androidx.compose.foundation.shape.RoundedCornerShape(6.dp)) { Text(row.statusLabel,Modifier.padding(horizontal=8.dp,vertical=4.dp),fontSize=15.sp,color=MaterialTheme.colorScheme.primary) }
                if(row.section==HistorySection.LEGACY) row.amountCaption?.let{Note(it)}
            }
            ChevronRight()
            // [목적] 여행 내역 행의 '목록 삭제' 버튼
            // [이유] 화살표 바로 옆 작은 글자 링크라 행을 열려다 잘못 누르기 쉬워, 구분선으로 떼고 보조 버튼 크기로 키움
            // [사이드 임팩트] 행 오른쪽 폭이 늘어남
            // [수정 시 고려사항] 삭제는 확인 대화상자를 거침
            if(onDelete!=null && row.status!=TripStatus.IN_PROGRESS) {
                androidx.compose.material3.VerticalDivider(Modifier.height(56.dp),color=MaterialTheme.colorScheme.outlineVariant)
                com.gatita.core.ui.component.Action("목록 삭제",primary=false,textSize=16,horizontalPadding=18){onDelete(row)}
            }
        }
    }
}
