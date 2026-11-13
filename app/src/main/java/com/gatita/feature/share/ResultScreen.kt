package com.gatita.feature.share

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
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
    GatitaScaffold(title = (state.content as? ResultContentState.Payments)?.view?.tripName ?: "정산 결과", onBack = onBack,
        // ponytail: 카드 위에 따로 떠 있던 '확정 시각' 라벨·값을 부제 한 줄로 합침
        subtitle=if(state.confirmedAt.isNotBlank())"${state.confirmedAt}에 확정했어요. 함께 탄 사람들에게 요약을 전달해 주세요." else "함께 탄 사람들에게 정산 요약을 전달해 주세요.") {
        val content = state.content
        when {
            state.isLoading -> LoadingBlock("정산 결과를 불러오는 중")
            state.loadError != null -> ErrorBlock(state.loadError, onRetry)
            state.notFound || content == null ->
                EmptyBlock("확정된 정산이 없어요", "정산을 확정하면 이곳에서 결과와 공유용 QR을 볼 수 있어요.")

            else -> {
                when (content) {
                    is ResultContentState.Payments -> PaymentsResult(content)
                    is ResultContentState.Legacy -> LegacySettlementCard(content.view)
                }
                // ponytail: 이 화면의 주요 버튼은 '정산 요약 복사' 하나만 두고 홈 이동은 보조 버튼으로
                Action("홈으로", primary = false, onClick = onHome)
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
            Text("함께 나눌 총금액",fontSize=20.sp,color=Color.White)
            AmountLabel(view.total.removeSuffix("원"),"원",64,Color.White)
            Text("주유비 ${view.fuelTotal} · 기타 비용 ${view.otherTotal}",fontSize=15.sp,color=Color(0xFFC5D9E8))
            androidx.compose.material3.HorizontalDivider(color=Color.White.copy(alpha=.15f))
            Text("정산에 포함한 비용  ${view.costs.count{it.included}}건",fontSize=15.sp,color=Color(0xFFC5D9E8))
        }
        Column(Modifier.weight(1f).padding(32.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            // [목적] 1인 금액 옆에 정산 완료를 축하하는 마스코트
            // [이유] 정산을 끝낸 순간인데 숫자만 있어 밋밋해, 한 바퀴 돌며 뛰는 축하 모션을 곁들임
            // [사이드 임팩트] 결과 화면 진입 시 모션 1회 재생. 금액 글자 폭은 weight로 먼저 확보
            // [수정 시 고려사항] 금액이 길어지면 마스코트가 아니라 글이 우선이므로 마스코트 크기를 키우지 말 것
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(24.dp)) {
                    Text("${view.splitPeople}명이 함께 나누면",fontSize=20.sp)
                    AmountLabel(view.perPerson.removeSuffix("원"),"원 / 인",42,MaterialTheme.colorScheme.primary)
                    Note(if(view.includeDriver)"운전자를 포함한 균등 분담" else "운전자를 제외한 균등 분담")
                }
                com.gatita.feature.launch.JourneyMascot(Modifier.size(150.dp),com.gatita.R.drawable.tago_result_cheer)
            }
            androidx.compose.material3.HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Text("함께 탄 인원  ${view.peopleLine}")
            Note("확정한 분담 기준이에요.")
        }
      }
    }
    // [목적] 정산 요약 공유 버튼 (복사·QR 보기)
    // [이유] 화면 안내가 '요약을 전달해 주세요'인데 버튼이 명세 아래 첫 화면 밖에 있어, 총액 카드 바로 아래·운전석 쪽으로 올림
    // [사이드 임팩트] 'QR 보기'는 아래 모달창(showSummary)을 엶
    // [수정 시 고려사항] QR은 화면 안에 펼치지 않고 모달로만 보여줌 (아래 VehicleDialog)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Action("정산 요약 복사"){clipboard.setText(AnnotatedString(content.summaryText));copied=true}
        Action("QR 보기",primary=false){showSummary=true}
    }
    androidx.compose.animation.AnimatedVisibility(copied){NoticeMessage("정산 요약을 복사했어요.")}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
        Panel(Modifier.weight(1f)) {
            SectionTitle("각자 나눌 금액")
            view.shares.forEach {share->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(share.label,Modifier.weight(1f));Text(share.amount)
                }
            }
            Note(if(view.distance=="기록 없음") "주행거리 기록 없음" else "기록 거리 ${view.distance} · ${view.source}")
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
    // [목적] 공유용 QR과 요약 글을 모달창으로 보여줌 (왼쪽 QR, 오른쪽 요약 글·닫기)
    // [이유] 화면 맨 아래에 펼치면 스크롤해야 QR이 보였고, 동승자에게 화면을 내밀 때 다른 내용과 섞여 QR이 눈에 띄지 않았음
    // [사이드 임팩트] 없음 (표시 방식만 변경, QR·요약 내용은 그대로). 바깥을 누르거나 '닫기'로 닫힘
    // [수정 시 고려사항] 요약 글은 QR과 같은 내용을 글로 확인하는 용도라 지우지 말 것. 긴 요약은 글 영역 안에서만 스크롤됨
    if(showSummary) VehicleDialog({showSummary=false}) {
        androidx.compose.material3.Surface(Modifier.padding(24.dp).widthIn(max=860.dp).fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
            Row(Modifier.padding(32.dp),horizontalArrangement=Arrangement.spacedBy(32.dp),verticalAlignment=Alignment.CenterVertically) {
                (content.qr as? QrState.Ready)?.let { QrCodeView(matrix=it.matrix,contentDescription="정산 요약 QR 코드. 같은 내용이 옆에 글로도 표시돼요.",modifier=Modifier.width(320.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))) }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text("정산 요약 QR",style=MaterialTheme.typography.headlineSmall)
                    Note(if(content.qr is QrState.Ready) "휴대폰 카메라로 찍으면 아래 요약 글이 열려요. 송금이나 입금 확인은 아니에요." else "요약이 길어 QR을 만들지 못했어요. 아래 글을 직접 전달해 주세요.")
                    SelectionContainer(Modifier.heightIn(max=220.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        Text(content.summaryText,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.fillMaxWidth())
                    }
                    Action("닫기",primary=false,modifier=Modifier.fillMaxWidth()){showSummary=false}
                }
            }
        }
    }
}
