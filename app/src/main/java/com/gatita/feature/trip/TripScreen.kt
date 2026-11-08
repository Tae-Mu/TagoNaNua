package com.gatita.feature.trip

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TripEvent.OpenSettlement -> onOpenSettlement(event.tripId)
                TripEvent.Discarded -> onDiscarded()
            }
        }
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
) {
    GatitaScaffold(title = "여행 기록", onBack = onBack) {
        val trip = state.trip
        val ledger = state.ledger
        when {
            state.isLoading -> LoadingBlock("여행 기록을 불러오는 중")
            state.loadError != null -> ErrorBlock(state.loadError, onRetryLoad)
            state.notFound || trip == null || ledger == null ->
                EmptyBlock("여행을 찾을 수 없습니다", "삭제되었거나 저장되지 않은 여행입니다. 홈으로 돌아가세요.")

            else -> {
                InfoCard {
                    Text(trip.name, style = MaterialTheme.typography.titleLarge)
                    Text("${Formatters.tripStatus(trip.status)} · ${trip.tripDate}", style = MaterialTheme.typography.bodyMedium)
                    LabeledValue("시작 시각", Formatters.dateTime(trip.startedAtMillis))
                    SourceLabel(Formatters.source(trip.source))
                }

                CostLedgerSection(
                    ledger = ledger,
                    busy = state.isMutating || state.isCompleting,
                    onIncludedChange = onIncludedChange,
                    onEdit = onEditCost,
                    onDelete = onRequestDelete,
                )
                state.costError?.let { ErrorMessage(it) }
                if (ledger.editable) {
                    ActionButton(
                        text = "비용 추가",
                        onClick = onAddCost,
                        enabled = !state.isMutating && !state.isCompleting,
                    )
                }

                SectionTitle("구간 기록 (선택)")
                if (state.segments.isEmpty()) {
                    EmptyBlock(
                        "저장된 구간이 없습니다",
                        "구간 기록은 선택입니다. 기록하지 않아도 비용만으로 정산할 수 있고, 정산 금액에는 영향이 없습니다.",
                    )
                } else {
                    state.segments.forEach { SegmentCard(it) }
                }

                when (trip.status) {
                    TripStatus.IN_PROGRESS -> {
                        SegmentForm(
                            state,
                            onEndOdometerChange,
                            onEfficiencyChange,
                            onEfficiencyUnitChange,
                            onSaveSegment,
                        )
                        CompleteSection(state, onRequestComplete)
                        // 구간도 비용도 없는 여행은 마칠 수 없으므로 닫는 방법을 함께 보여 준다.
                        if (state.segments.isEmpty() && ledger.costs.isEmpty()) {
                            DiscardSection(state, onRequestDiscard)
                        }
                    }

                    TripStatus.FINISHED -> ActionButton("정산으로 이동", onOpenSettlement)
                    TripStatus.SETTLED -> ActionButton("정산 결과 보기", onOpenResult)
                }
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
                        "저장된 구간이 없으면 주행거리 0km인 여행으로 마칩니다.",
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
