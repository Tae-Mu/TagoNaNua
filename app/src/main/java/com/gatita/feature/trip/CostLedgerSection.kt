package com.gatita.feature.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gatita.core.ui.component.CheckRow
import com.gatita.core.ui.component.EmptyBlock
import com.gatita.core.ui.component.InfoCard
import com.gatita.core.ui.component.LabeledValue
import com.gatita.core.ui.component.SectionTitle
import com.gatita.domain.model.Cost
import com.gatita.domain.model.CostKind
import com.gatita.domain.model.CostLedger
import com.gatita.feature.common.Formatters

/** 등록한 결제 비용 목록과 합계. 합계는 도메인이 계산한 [CostLedger] 값을 그대로 표시한다. */
@Composable
fun CostLedgerSection(
    ledger: CostLedger,
    busy: Boolean,
    onIncludedChange: (Cost, Boolean) -> Unit,
    onEdit: (Cost) -> Unit,
    onDelete: (Cost) -> Unit,
) {
    SectionTitle("결제 비용")
    InfoCard {
        Text("정산에 포함한 금액 합계", style = MaterialTheme.typography.labelLarge)
        Text(Formatters.won(ledger.includedTotalWon), style = MaterialTheme.typography.displaySmall)
        Text(
            "등록 ${ledger.count}건 중 ${ledger.includedCount}건 포함 · 등록 합계 ${Formatters.won(ledger.registeredTotalWon)}" +
                " · 제외 ${Formatters.won(ledger.excludedTotalWon)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        val byKind = CostKind.entries
            .filter { (ledger.totalsByKind[it] ?: 0L) > 0L }
            .joinToString(" · ") { "${Formatters.costKind(it)} ${Formatters.won(ledger.totalsByKind.getValue(it))}" }
        if (byKind.isNotEmpty()) {
            Text("종류별 등록액(포함 여부 무관): $byKind", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (ledger.costs.isEmpty()) {
        EmptyBlock(
            "등록한 결제 비용이 없습니다",
            "실제로 결제한 주유·주차·톨비·기타 금액을 입력하세요. 비용이 없으면 정산할 수 없습니다.",
        )
        return
    }
    ledger.costs.forEach { cost ->
        CostCard(
            cost = cost,
            editable = ledger.editable,
            busy = busy,
            onIncludedChange = { onIncludedChange(cost, it) },
            onEdit = { onEdit(cost) },
            onDelete = { onDelete(cost) },
        )
    }
}

@Composable
private fun CostCard(
    cost: Cost,
    editable: Boolean,
    busy: Boolean,
    onIncludedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    InfoCard(
        container = if (cost.included) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        CheckRow(
            label = if (cost.included) "정산에 포함" else "정산에서 제외",
            checked = cost.included,
            onCheckedChange = onIncludedChange,
            enabled = editable && !busy,
        )
        Text(
            "${Formatters.costKind(cost.kind)} · ${Formatters.won(cost.amountWon)}",
            style = MaterialTheme.typography.titleMedium,
        )
        cost.description?.let {
            val label = if (cost.kind == CostKind.FUEL) "주유소" else "내용"
            LabeledValue(label, it)
        }
        if (cost.kind == CostKind.FUEL) {
            cost.unitPriceWonPerLiter?.let { LabeledValue("리터당 가격", Formatters.wonDecimal(it) + "/L") }
            cost.paidOn?.let { LabeledValue("주유일", it.toString()) }
            cost.fuelLiters?.let {
                LabeledValue("주유량(결제액 ÷ 단가, 표시용)", Formatters.decimal(it, 3) + " L")
            }
        }
        if (editable) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("수정") }
                TextButton(onClick = onDelete, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("삭제") }
            }
        }
    }
}
