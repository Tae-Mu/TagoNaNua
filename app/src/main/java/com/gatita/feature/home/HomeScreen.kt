package com.gatita.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatita.core.ui.component.ActionButton
import com.gatita.core.ui.component.EmptyBlock
import com.gatita.core.ui.component.ErrorBlock
import com.gatita.core.ui.component.ErrorMessage
import com.gatita.core.ui.component.GatitaScaffold
import com.gatita.core.ui.component.InfoCard
import com.gatita.core.ui.component.LabeledValue
import com.gatita.core.ui.component.LoadingBlock
import com.gatita.core.ui.component.NumberField
import com.gatita.core.ui.component.SecondaryButton
import com.gatita.core.ui.component.SectionTitle
import com.gatita.core.ui.component.SourceLabel
import com.gatita.core.ui.component.TextInput
import com.gatita.domain.model.Trip
import com.gatita.domain.model.TripSummary
import com.gatita.feature.common.Formatters

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onOpenTrip: (Long) -> Unit,
    onOpenSettlement: (Long) -> Unit,
    onOpenHistory: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenTrip -> onOpenTrip(event.tripId)
            }
        }
    }
    HomeContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onStartOdometerChange = viewModel::onStartOdometerChange,
        onStartTrip = viewModel::startTrip,
        onRetryLoad = viewModel::observe,
        onOpenTrip = onOpenTrip,
        onOpenSettlement = onOpenSettlement,
        onOpenHistory = onOpenHistory,
    )
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onNameChange: (String) -> Unit,
    onStartOdometerChange: (String) -> Unit,
    onStartTrip: () -> Unit,
    onRetryLoad: () -> Unit,
    onOpenTrip: (Long) -> Unit,
    onOpenSettlement: (Long) -> Unit,
    onOpenHistory: () -> Unit,
) {
    GatitaScaffold(title = "같이타 정산") {
        InfoCard {
            Text("직접 입력 모드", style = MaterialTheme.typography.titleMedium)
            Text(
                "실제로 결제한 금액을 직접 입력해 나눕니다. 차량(Pleos) 데이터와 카드·하이패스 내역은 연동되어 있지 않으며, " +
                    "이 앱은 계산 결과만 보여줄 뿐 송금이나 입금을 처리하지 않습니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        when {
            state.isLoading -> LoadingBlock("저장된 여행을 불러오는 중")
            state.loadError != null -> ErrorBlock(state.loadError, onRetryLoad)
            else -> {
                val active = state.activeTrip
                if (active != null) {
                    ActiveTripCard(active, onContinue = { onOpenTrip(active.id) })
                } else {
                    StartTripCard(state, onNameChange, onStartOdometerChange, onStartTrip)
                }

                if (state.pendingTrips.isNotEmpty()) {
                    SectionTitle("정산을 마치지 않은 여행")
                    state.pendingTrips.forEach { trip ->
                        PendingTripCard(trip, onOpen = { onOpenSettlement(trip.tripId) })
                    }
                }
            }
        }

        SecondaryButton("지난 여행·정산 내역", onOpenHistory)
    }
}

@Composable
private fun ActiveTripCard(trip: Trip, onContinue: () -> Unit) {
    InfoCard {
        Text("기록 중인 여행", style = MaterialTheme.typography.titleLarge)
        Text(trip.name, style = MaterialTheme.typography.titleMedium)
        Text(
            "앱을 다시 켜도 저장된 여행과 비용을 이어서 볼 수 있습니다. 앱을 다시 켠 것만으로 지금 주행 중이라고 판단하지 않습니다.",
            style = MaterialTheme.typography.bodyMedium,
        )
        LabeledValue("시작 시각", Formatters.dateTime(trip.startedAtMillis))
        SourceLabel(Formatters.source(trip.source))
        ActionButton(text = "이어서 보기", onClick = onContinue)
    }
}

@Composable
private fun PendingTripCard(trip: TripSummary, onOpen: () -> Unit) {
    InfoCard {
        Text(trip.name, style = MaterialTheme.typography.titleMedium)
        Text("정산 대기 · ${trip.tripDate}", style = MaterialTheme.typography.bodyMedium)
        LabeledValue("포함한 결제 비용 합계(미확정)", Formatters.won(trip.includedTotalWon))
        Text(
            "비용 ${trip.costCount}건 중 ${trip.includedCount}건 포함",
            style = MaterialTheme.typography.bodyMedium,
        )
        SourceLabel(Formatters.source(trip.source))
        ActionButton(text = "정산 이어서 하기", onClick = onOpen)
    }
}

@Composable
private fun StartTripCard(
    state: HomeUiState,
    onNameChange: (String) -> Unit,
    onStartOdometerChange: (String) -> Unit,
    onStartTrip: () -> Unit,
) {
    SectionTitle("새 여행 시작")
    TextInput(
        label = "여행 이름 (선택)",
        value = state.nameInput,
        onValueChange = onNameChange,
        errorText = state.nameError,
        supporting = "비우면 '새로운 여행'으로 저장합니다.",
        enabled = !state.isStarting,
    )
    NumberField(
        label = "계기판 주행거리 (km) · 수동 입력",
        value = state.startOdometerInput,
        onValueChange = onStartOdometerChange,
        errorText = state.startOdometerError,
        supporting = "계기판 값을 직접 입력하세요. 차량에서 자동으로 읽은 값이 아니며, 정산 금액에는 쓰이지 않는 기록입니다.",
        enabled = !state.isStarting,
        imeAction = ImeAction.Done,
    )
    state.startError?.let { ErrorMessage(it) }
    ActionButton(
        text = "여행 시작",
        onClick = onStartTrip,
        busy = state.isStarting,
        busyText = "시작하는 중…",
    )
    if (state.pendingTrips.isEmpty()) {
        EmptyBlock(
            title = "기록된 여행이 없습니다",
            description = "여행을 시작하고 주유·주차·톨비·기타 결제 금액을 입력하면, 정산에 포함한 금액의 합계를 인원수로 나눕니다.",
        )
    }
}
