package com.gatita.feature.trip

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import com.gatita.core.ui.theme.Navy
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
fun CostLedgerSection(ledger:CostLedger,busy:Boolean,onIncludedChange:(Cost,Boolean)->Unit,onEdit:(Cost)->Unit,onDelete:(Cost)->Unit,onAdd:(()->Unit)?=null) {
    Surface(Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(22.dp),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Row(Modifier.weight(1.65f).fillMaxHeight().background(Navy).padding(28.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("등록한 총비용",fontSize=13.sp,color=Color(0xFFC1D8E9))
                    AmountLabel(Formatters.won(ledger.registeredTotalWon).removeSuffix("원"),"원",42,Color.White)
                    Text("비용 ${ledger.count}건",fontSize=12.sp,color=Color(0xFFC1D8E9))
                }
                if(ledger.editable && onAdd!=null)Action("＋ 비용 추가",primary=false,minHeight=46,textSize=14,enabled=!busy,onClick=onAdd)
            }
            Column(Modifier.weight(1f).padding(28.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Note("함께 나눌 금액")
                AmountLabel(Formatters.won(ledger.includedTotalWon).removeSuffix("원"),"원",29,MaterialTheme.colorScheme.primary)
                Note("${ledger.includedCount}건 포함 · ${Formatters.won(ledger.excludedTotalWon)} 제외")
            }
        }
    }
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        CostKind.entries.forEach { kind->Panel(Modifier.weight(1f)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                CostIcon(iconKind(kind))
                Column { Note(Formatters.costKind(kind));AmountLabel(Formatters.won(ledger.totalsByKind[kind]?:0).removeSuffix("원"),"원",24) }
            }
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
