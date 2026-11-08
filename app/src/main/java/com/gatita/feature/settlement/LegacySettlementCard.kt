package com.gatita.feature.settlement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gatita.core.ui.component.InfoCard
import com.gatita.core.ui.component.LabeledValue
import com.gatita.core.ui.component.SectionTitle
import com.gatita.core.ui.component.SourceLabel

/**
 * 이전(v1) 방식으로 확정된 추정 정산을 읽기 전용으로 보여준다. 거리·연비·유가로 계산한 값이며 실제 결제 금액이 아니다.
 * 새 정산은 이 방식으로 만들 수 없다.
 */
@Composable
fun LegacySettlementCard(view: SettlementView) {
    InfoCard(container = MaterialTheme.colorScheme.tertiaryContainer) {
        Text("이전 방식(추정) 정산 · LEGACY_ESTIMATE", style = MaterialTheme.typography.titleMedium)
        Text(
            "거리·연비·유가로 계산한 추정 기록입니다. 실제 결제 금액의 합계가 아니며 읽기 전용입니다.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(view.totalCost, style = MaterialTheme.typography.displaySmall)
        Text("추정 총 유류비 · ${view.headcount}명", style = MaterialTheme.typography.bodyMedium)
        SourceLabel(view.sourceLabel)
    }

    SectionTitle("1인당 분담액(추정)")
    view.shareLines.forEach { share ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(share.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(share.amount, style = MaterialTheme.typography.titleMedium)
        }
    }

    SectionTitle("이전 계산 근거")
    Text(view.basisExplanation, style = MaterialTheme.typography.bodyMedium)
    view.fallbackReasons.forEach { Text("• 추정 사유: $it", style = MaterialTheme.typography.bodyMedium) }
    LabeledValue("총 거리", view.totalDistance)
    LabeledValue("총 연료량", view.totalLiters)
    LabeledValue("유가", view.unitPrice)
    view.basisLines.forEach { line ->
        InfoCard {
            Text(line.title, style = MaterialTheme.typography.titleSmall)
            Text(line.detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Text(view.roundingNote, style = MaterialTheme.typography.bodySmall)
}
