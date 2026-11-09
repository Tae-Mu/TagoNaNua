package com.gatita.feature.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import com.gatita.core.ui.component.*
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
    val clipboard=LocalClipboardManager.current
    var copied by remember{mutableStateOf(false)}
    var showSummary by rememberSaveable{mutableStateOf(false)}
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Action("정산 요약 복사"){clipboard.setText(AnnotatedString(content.summaryText));copied=true}
        Action(if(showSummary)"요약 접기" else "요약 · QR 보기",primary=false){showSummary=!showSummary}
    }
    if(copied)NoticeMessage("정산 요약을 복사했어요.")
    Panel(Modifier.fillMaxWidth()) {
        Note("확정된 결제액 정산 · ${view.tripDate}")
        Text(view.tripName,fontSize=28.sp)
        Text(view.total,fontSize=42.sp,color=MaterialTheme.colorScheme.primary)
        Note(view.peopleLine)
        Note("주유 ${view.fuelTotal} · 기타 ${view.otherTotal}")
        Note("확정 당시 금액을 보관했어요. 실제 송금과 입금 확인은 별도로 진행해 주세요.")
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
        Panel(Modifier.weight(1f)) {
            SectionTitle("각자 나눌 금액")
            view.shares.forEach {share->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(share.label,Modifier.weight(1f));Text(share.amount)
                }
            }
            Note("기록 거리 ${view.distance} · ${view.source}")
        }
        Panel(Modifier.weight(1f)) {
            SectionTitle("확정 당시 비용")
            view.costs.forEachIndexed {index,cost->
                if(index>0) androidx.compose.material3.HorizontalDivider()
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(cost.title,Modifier.weight(1f));Text(cost.amount)}
                cost.details.forEach{Note(it)}
                Note(if(cost.included)"정산 포함" else "정산 제외")
            }
        }
    }
    if(showSummary) {
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
}
