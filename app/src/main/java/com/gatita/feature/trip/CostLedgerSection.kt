package com.gatita.feature.trip

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatita.core.ui.component.*
import com.gatita.domain.model.*
import com.gatita.feature.common.Formatters
import com.gatita.feature.publishing.CostIcon

@Composable
fun CostLedgerSection(ledger:CostLedger,busy:Boolean,onIncludedChange:(Cost,Boolean)->Unit,onEdit:(Cost)->Unit,onDelete:(Cost)->Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(30.dp)) {
            Column(Modifier.weight(1f)){Note("등록한 총비용");Text(Formatters.won(ledger.registeredTotalWon),fontSize=36.sp);Note("비용 ${ledger.count}건")}
            Column(Modifier.weight(1f)){Note("함께 나눌 금액");Text(Formatters.won(ledger.includedTotalWon),fontSize=32.sp);Note("${ledger.includedCount}건 포함 · ${Formatters.won(ledger.excludedTotalWon)} 제외")}
        }
    }
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        CostKind.entries.forEach { kind->Panel(Modifier.weight(1f)){
            CostIcon(iconKind(kind));Note(Formatters.costKind(kind));Text(Formatters.won(ledger.totalsByKind[kind]?:0),fontSize=18.sp)
        }}
    }
    if(ledger.costs.isEmpty())EmptyState("아직 등록한 비용이 없어요","함께 쓴 주유·주차·톨비·기타 비용을 추가해 주세요.")
    else Panel(Modifier.fillMaxWidth()) {
        ledger.costs.forEachIndexed{index,cost->
            if(index>0)HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(18.dp),verticalAlignment=Alignment.CenterVertically) {
                CostIcon(iconKind(cost.kind))
                Column(Modifier.weight(1f)){
                    Text(cost.description?.takeIf{it.isNotBlank()}?:Formatters.costKind(cost.kind),fontSize=19.sp)
                    Note(Formatters.costKind(cost.kind))
                    if(cost.kind==CostKind.FUEL)Note("${cost.paidOn} · ${cost.unitPriceWonPerLiter?.toPlainString()}원/L · 약 ${cost.fuelLiters} L")
                }
                Column(horizontalAlignment=Alignment.End) {
                    AmountLabel(Formatters.won(cost.amountWon).removeSuffix("원"),"원",24)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(cost.included,{onIncludedChange(cost,it)},enabled=ledger.editable&&!busy);Text("정산 포함")
                        if(ledger.editable){TextButton({onEdit(cost)},enabled=!busy){Text("수정")};TextButton({onDelete(cost)},enabled=!busy){Text("삭제")}}
                    }
                }
            }
        }
    }
}
internal fun iconKind(kind:CostKind)=when(kind){CostKind.FUEL->"주유";CostKind.PARKING->"주차";CostKind.TOLL->"톨비";CostKind.OTHER->"기타"}
