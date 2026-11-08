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
import androidx.compose.runtime.getValue
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
        onOpen = { row ->
            when (row.status) {
                TripStatus.IN_PROGRESS -> onOpenTrip(row.tripId)
                TripStatus.FINISHED -> onOpenSettlement(row.tripId)
                TripStatus.SETTLED -> onOpenResult(row.tripId)
            }
        },
    )
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
) {
    GatitaScaffold(title = "지난 여행·정산", onBack = onBack) {
        MonthBar(state.month, onPreviousMonth, onNextMonth)
        ChoiceChips(
            options = HistoryFilter.entries.toList(),
            selected = state.filter,
            label = {
                when (it) {
                    HistoryFilter.ALL -> "전체"
                    HistoryFilter.PENDING -> "정산 전"
                    HistoryFilter.SETTLED -> "확정"
                }
            },
            onSelect = onFilterChange,
        )
        when {
            state.error != null -> ErrorBlock(state.error, onRetry)
            state.isLoading -> LoadingBlock("내역을 불러오는 중")
            else -> {
                InfoCard {
                    Text(
                        "이번 달 여행 ${state.monthlyTripCount}건 · 확정 ${state.monthlySettledCount}건 · 주행거리 ${state.monthlyDistance}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text("월 통계는 위 필터와 관계없이 그 달 전체 기준입니다.", style = MaterialTheme.typography.bodySmall)
                }
                if (state.rows.isEmpty()) {
                    EmptyBlock(
                        "이 달에 표시할 여행이 없습니다",
                        "여행을 기록하고 정산을 확정하면 이곳에 쌓입니다. 다른 달이나 필터도 확인해 보세요.",
                    )
                } else {
                    HistorySection.entries.forEach { section ->
                        val rows = state.rows.filter { it.section == section }
                        if (rows.isNotEmpty()) {
                            SectionTitle(section.title)
                            rows.forEach { HistoryCard(it, onOpen) }
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
private fun HistoryCard(row: HistoryRow, onOpen: (HistoryRow) -> Unit) {
    InfoCard(
        container = if (row.section == HistorySection.LEGACY) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        modifier = Modifier
            .heightIn(min = 72.dp)
            .clickable(role = Role.Button, onClickLabel = "${row.title} 열기") { onOpen(row) },
    ) {
        Text("${row.title} · ${row.statusLabel}", style = MaterialTheme.typography.titleMedium)
        Text(row.date, style = MaterialTheme.typography.bodyMedium)
        row.amountCaption?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
        row.amount?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
        row.costSummary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        row.distance?.let { Text("주행거리 $it", style = MaterialTheme.typography.bodyMedium) }
        SourceLabel(row.source)
    }
}
