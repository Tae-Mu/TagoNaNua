package com.gatita.feature.share

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import com.gatita.core.ui.theme.Navy
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
import com.gatita.feature.common.Formatters
import com.gatita.feature.settlement.LegacySettlementCard

@Composable
fun ResultRoute(viewModel: ResultViewModel, onBack: () -> Unit, onHome: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResultContent(state, onBack, onHome, onRetry = viewModel::reload)
}

@Composable
fun ResultContent(state: ResultUiState, onBack: () -> Unit, onHome: () -> Unit, onRetry: () -> Unit) {
    GatitaScaffold(title = (state.content as? ResultContentState.Payments)?.view?.tripName ?: "정산 결과", onBack = onBack,subtitle="함께 탄 사람들에게 정산 요약을 전달해 주세요.") {
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
    androidx.compose.material3.Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
      Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.weight(1f).fillMaxHeight().background(Navy).padding(32.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Text("CONFIRMED TOTAL",fontSize=11.sp,letterSpacing=2.sp,color=Color(0xFFC5D9E8))
            Text("함께 나눌 총금액",fontSize=20.sp,color=Color.White)
            AmountLabel(view.total.removeSuffix("원"),"원",64,Color.White)
            Text("주유비 ${view.fuelTotal} · 기타 비용 ${view.otherTotal}",fontSize=13.sp,color=Color(0xFFC5D9E8))
            androidx.compose.material3.HorizontalDivider(color=Color.White.copy(alpha=.15f))
            Text("정산에 포함한 비용  ${view.costs.count{it.included}}건",fontSize=13.sp,color=Color(0xFFC5D9E8))
        }
        Column(Modifier.weight(1f).padding(32.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Text("SPLIT TOGETHER",fontSize=11.sp,letterSpacing=2.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${view.splitPeople}명이 함께 나누면",fontSize=20.sp)
            AmountLabel(view.perPerson.removeSuffix("원"),"원 / 인",42,MaterialTheme.colorScheme.primary)
            Note(if(view.includeDriver)"운전자를 포함한 균등 분담" else "운전자를 제외한 균등 분담")
            androidx.compose.material3.HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Text("함께 탄 인원  ${view.peopleLine}")
            Note("확정한 분담 기준이에요.")
        }
      }
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
                Note(if(view.fuelSegments&&cost.title.startsWith(Formatters.costKind(com.gatita.domain.model.CostKind.FUEL)))"단가 산정용 · 금액은 합계에 직접 더하지 않아요" else if(cost.included)"정산 포함" else "정산 제외")
            }
            if(view.fuelIntervalLines.isNotEmpty()) {
                androidx.compose.material3.HorizontalDivider()
                Text("구간별 유류비 계산",fontSize=15.sp)
                view.fuelIntervalLines.forEach{Note(it)}
            }
        }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
        Action(if(showSummary)"요약 접기" else "요약 · QR 보기",primary=false){showSummary=!showSummary}
        Spacer(Modifier.width(12.dp))
        Action("정산 요약 복사"){clipboard.setText(AnnotatedString(content.summaryText));copied=true}
    }
    if(copied)NoticeMessage("정산 요약을 복사했어요.")
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
