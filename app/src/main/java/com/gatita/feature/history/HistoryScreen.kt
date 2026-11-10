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
    state.deleteTarget?.let { target -> androidx.compose.material3.AlertDialog(
        onDismissRequest=viewModel::dismissDelete,
        title={Text("목록에서 삭제할까요?")},
        text={Column {Text("‘${target.title}’을 여행 내역과 정산 목록에서 제외해요. 확정된 정산 원본은 이 기기에 보관돼요.");state.deleteError?.let { ErrorMessage(it) }}},
        confirmButton={androidx.compose.material3.TextButton(viewModel::confirmDelete,enabled=!state.deleting){Text(if(state.deleting)"처리 중…" else "목록에서 삭제")}},
        dismissButton={androidx.compose.material3.TextButton(viewModel::dismissDelete,enabled=!state.deleting){Text("취소")}}
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
                    EmptyBlock(
                        "이 달에 표시할 여행이 없습니다",
                        "여행을 기록하고 정산을 확정하면 이곳에 쌓입니다. 다른 달이나 필터도 확인해 보세요.",
                    )
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
            Column {Text(row.date.takeLast(2).toIntOrNull()?.toString()?:row.date.takeLast(2),fontSize=28.sp);Note("${row.date.substringAfter("-").substringBefore("-").toIntOrNull()?:""}월")}
            androidx.compose.material3.VerticalDivider(Modifier.height(56.dp),color=MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(row.title,fontSize=20.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold);Note(listOfNotNull(row.distance,row.costSummary).joinToString(" · "))}
            Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                row.amount?.let { AmountLabel(it.removeSuffix("원"),"원",26) }
                androidx.compose.material3.Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=androidx.compose.foundation.shape.RoundedCornerShape(6.dp)) { Text(row.statusLabel,Modifier.padding(horizontal=8.dp,vertical=4.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.primary) }
                if(row.section==HistorySection.LEGACY) row.amountCaption?.let{Note(it)}
            }
            ChevronRight()
            if(onDelete!=null && row.status!=TripStatus.IN_PROGRESS) androidx.compose.material3.TextButton({onDelete(row)}) {Text("목록 삭제")}
        }
    }
}
