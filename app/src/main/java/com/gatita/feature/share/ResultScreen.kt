package com.gatita.feature.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatita.core.ui.component.ActionButton
import com.gatita.core.ui.component.EmptyBlock
import com.gatita.core.ui.component.ErrorBlock
import com.gatita.core.ui.component.GatitaScaffold
import com.gatita.core.ui.component.InfoCard
import com.gatita.core.ui.component.LabeledValue
import com.gatita.core.ui.component.LoadingBlock
import com.gatita.core.ui.component.SectionTitle
import com.gatita.core.ui.component.SourceLabel
import com.gatita.feature.settlement.LegacySettlementCard

@Composable
fun ResultRoute(viewModel: ResultViewModel, onBack: () -> Unit, onHome: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResultContent(state, onBack, onHome, onRetry = viewModel::reload)
}

@Composable
fun ResultContent(state: ResultUiState, onBack: () -> Unit, onHome: () -> Unit, onRetry: () -> Unit) {
    GatitaScaffold(title = "정산 결과", onBack = onBack) {
        val content = state.content
        when {
            state.isLoading -> LoadingBlock("정산 결과를 불러오는 중")
            state.loadError != null -> ErrorBlock(state.loadError, onRetry)
            state.notFound || content == null ->
                EmptyBlock("확정된 정산이 없습니다", "정산을 확정하면 이곳에서 결과와 공유용 QR을 볼 수 있습니다.")

            else -> {
                LabeledValue("확정 시각", state.confirmedAt)
                when (content) {
                    is ResultContentState.Payments -> PaymentsResult(content)
                    is ResultContentState.Legacy -> LegacySettlementCard(content.view)
                }
                ActionButton("홈으로", onHome)
            }
        }
    }
}

@Composable
private fun PaymentsResult(content: ResultContentState.Payments) {
    val view = content.view
    InfoCard {
        Text("확정된 결제액 정산", style = MaterialTheme.typography.titleMedium)
        Text(view.tripName, style = MaterialTheme.typography.titleLarge)
        Text(view.tripDate, style = MaterialTheme.typography.bodyMedium)
        Text(view.total, style = MaterialTheme.typography.displaySmall)
        Text("정산에 포함한 실제 결제 금액의 합계 · ${view.peopleLine}", style = MaterialTheme.typography.bodyMedium)
        Text("주유 ${view.fuelTotal} · 기타 ${view.otherTotal}", style = MaterialTheme.typography.bodyMedium)
        Text(
            "확정된 정산은 수정할 수 없습니다. 계산 결과일 뿐 송금이나 입금 확인이 아닙니다.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    SectionTitle("분담액")
    view.shares.forEach { share ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(share.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(share.amount, style = MaterialTheme.typography.titleMedium)
        }
    }

    SectionTitle("결제 내역 (확정 당시)")
    view.costs.forEach { cost ->
        InfoCard {
            Text(cost.title, style = MaterialTheme.typography.titleSmall)
            Text(cost.amount, style = MaterialTheme.typography.titleMedium)
            cost.details.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(if (cost.included) "정산에 포함" else "정산에서 제외", style = MaterialTheme.typography.labelLarge)
        }
    }

    SectionTitle("기록 정보 (참고용)")
    InfoCard {
        LabeledValue("주행거리", view.distance)
        Text(view.quality, style = MaterialTheme.typography.bodyMedium)
        SourceLabel(view.source)
        Text("계산 방식 ${view.calculationVersion} · 정산 금액에는 영향을 주지 않는 기록입니다.", style = MaterialTheme.typography.bodySmall)
    }

    SectionTitle("공유용 QR")
    InfoCard {
        Text(
            "QR에는 아래 정산 요약 텍스트만 담깁니다. 송금이나 입금 확인이 아니며, 암호화나 접근 제한이 없으므로 보여줄 사람에게만 보여주세요.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val qr = content.qr) {
            is QrState.Ready -> QrCodeView(
                matrix = qr.matrix,
                contentDescription = "정산 요약 QR 코드. 같은 내용이 아래 텍스트로도 표시됩니다.",
            )

            QrState.TooLong -> InfoCard { Text("요약이 길어 QR을 만들지 못했습니다. 아래 텍스트를 직접 전달하세요.") }
            QrState.None -> Unit
        }
    }
    SelectionContainer {
        Text(content.summaryText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
    }
}
