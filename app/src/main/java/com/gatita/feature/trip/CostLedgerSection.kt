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
fun CostLedgerSection(
    ledger:CostLedger,busy:Boolean,onIncludedChange:(Cost,Boolean)->Unit,onEdit:(Cost)->Unit,onDelete:(Cost)->Unit,onAdd:(()->Unit)?=null,
    /** 구간별 유류비 방식 여행이면 true. 주유 이벤트·"감지 안 됨"을 보이고 함께 나눌 금액은 종료 후 계산한다고 안내한다. */
    fuelSegments:Boolean=false,
    refuels:List<RefuelEvent> = emptyList(),
    onRefuelDismissedChange:(RefuelEvent,Boolean)->Unit={_,_->},
    onUndetectedChange:(Cost,Boolean)->Unit={_,_->},
) {
    Surface(Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(22.dp),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Row(Modifier.weight(1.65f).fillMaxHeight().background(Navy).padding(28.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("등록한 총비용",fontSize=15.sp,color=Color(0xFFC1D8E9))
                    AmountLabel(Formatters.won(ledger.registeredTotalWon).removeSuffix("원"),"원",42,Color.White)
                    Text("비용 ${ledger.count}건",fontSize=15.sp,color=Color(0xFFC1D8E9))
                }
                if(ledger.editable && onAdd!=null)Action("비용 추가",primary=false,textSize=16,enabled=!busy,onClick=onAdd)
            }
            Column(Modifier.weight(1f).padding(28.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Note("함께 나눌 금액")
                if(fuelSegments) {
                    Text("여행을 마치면 계산해요",fontSize=20.sp,color=MaterialTheme.colorScheme.primary)
                    Note("유류비는 구간별 사용량 × 주유 단가로 계산해요.")
                } else {
                    AmountLabel(Formatters.won(ledger.includedTotalWon).removeSuffix("원"),"원",29,MaterialTheme.colorScheme.primary)
                    Note("${ledger.includedCount}건 포함 · ${Formatters.won(ledger.excludedTotalWon)} 제외")
                }
            }
        }
    }
    // [목적] 종류별 합계 칸 (금액이 있는 종류만, 2종류 이상일 때)
    // [이유] 0원 칸이 자리를 차지하고, 1종류뿐이면 위 총비용과 같은 숫자를 반복해 숨김
    // [사이드 임팩트] 없음 (표시만 변경)
    // [수정 시 고려사항] 종류별 금액은 비용 목록 각 행에도 있으므로 여기서 빠져도 정보 손실 없음
    val usedKinds=CostKind.entries.filter{(ledger.totalsByKind[it]?:0L)>0L}
    if(usedKinds.size>1) Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        usedKinds.forEach { kind->Panel(Modifier.weight(1f)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                CostIcon(iconKind(kind))
                Column { Note(Formatters.costKind(kind));AmountLabel(Formatters.won(ledger.totalsByKind[kind]?:0).removeSuffix("원"),"원",24) }
            }
        }}
    }
    if(fuelSegments) RefuelSection(ledger,refuels,busy,onRefuelDismissedChange)
    if(ledger.costs.isEmpty())EmptyState("아직 등록한 비용이 없어요","함께 쓴 주유·주차·톨비·기타 비용을 추가해 주세요.")
    else Panel(Modifier.fillMaxWidth()) {
        ledger.costs.forEachIndexed{index,cost->
            if(index>0)HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(18.dp),verticalAlignment=Alignment.CenterVertically) {
                CostIcon(iconKind(cost.kind))
                Column(Modifier.weight(1f)){
                    // [목적] 비용 행 제목·부제 (메모가 있을 때만 종류를 부제로)
                    // [이유] 메모가 없으면 제목과 부제가 '주유 / 주유'로 반복됨
                    // [사이드 임팩트] 없음
                    // [수정 시 고려사항] 메모 없는 행은 제목이 종류명
                    val memo=cost.description?.takeIf{it.isNotBlank()}
                    Text(memo?:Formatters.costKind(cost.kind),fontSize=19.sp)
                    if(memo!=null) Note(Formatters.costKind(cost.kind))
                    if(cost.kind==CostKind.FUEL)Note(listOfNotNull(cost.paidOn?.let(Formatters::shortDate),cost.unitPriceWonPerLiter?.let{Formatters.wonDecimal(it)+"/L"},cost.fuelLiters?.let{"약 $it L"}).joinToString(" · "))
                }
                // [목적] 비용 행 오른쪽: 금액 → 정산 포함 → 수정·삭제를 한 줄에 가운데 맞춤으로 배치
                // [이유] 금액이 버튼 줄 위에 따로 떠 있어 행의 오른쪽이 두 층으로 어긋나 보였음. 수정·삭제는 56dp 보조 버튼(운전석에서 누르기 쉬운 크기) 유지
                // [사이드 임팩트] 행 높이가 줄어듦
                // [수정 시 고려사항] 삭제는 확인 대화상자를 거치므로 여기서 추가 확인 불필요. 금액과 조작 사이 간격(28dp)을 줄이면 다시 붙어 보임
                AmountLabel(Formatters.won(cost.amountWon).removeSuffix("원"),"원",24)
                Spacer(Modifier.width(10.dp))
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    // ponytail: 구간별 유류비 여행의 주유 비용은 '정산 포함' 대신 '감지 안 됨'을 고름 (원격 fuel-segments-v2 로직 그대로)
                    if(fuelSegments&&cost.kind==CostKind.FUEL){Checkbox(cost.undetected,{onUndetectedChange(cost,it)},enabled=ledger.editable&&!busy);Text("감지 안 됨",fontSize=16.sp)}
                    else{Checkbox(cost.included,{onIncludedChange(cost,it)},enabled=ledger.editable&&!busy);Text("정산 포함",fontSize=16.sp)}
                    if(ledger.editable){Action("수정",primary=false,textSize=16,horizontalPadding=18,enabled=!busy){onEdit(cost)};Action("삭제",primary=false,textSize=16,horizontalPadding=18,enabled=!busy){onDelete(cost)}}
                }
            }
        }
    }
}
internal fun iconKind(kind:CostKind)=when(kind){CostKind.FUEL->"주유";CostKind.PARKING->"주차";CostKind.TOLL->"톨비";CostKind.OTHER->"기타"}

/** 감지된 주유 목록과 주유 비용 건수 비교. 두 수가 같아야 여행을 마칠 수 있다. */
@Composable
private fun RefuelSection(ledger:CostLedger,refuels:List<RefuelEvent>,busy:Boolean,onDismissedChange:(RefuelEvent,Boolean)->Unit) {
    val detected=refuels.count{!it.dismissed}
    val paired=ledger.costs.count{it.kind==CostKind.FUEL&&!it.undetected}
    Panel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("감지한 주유 ${detected}회 · 주유 비용 ${paired}건",fontSize=17.sp)
            Note(if(detected==paired)"주유 횟수와 비용 건수가 맞아요." else "두 수가 같아야 여행을 마칠 수 있어요. 비용을 추가하거나, 주유가 아니었다면 \"주유 아님\", 앱이 감지하지 못한 주유 비용이면 \"감지 안 됨\"을 눌러 주세요.")
            if(refuels.isEmpty())Note("아직 감지한 주유가 없어요. 주유 감지는 차량에서 연료량 변화를 읽어요.")
            refuels.forEach{event->
                val at=java.time.Instant.ofEpochMilli(event.detectedAtMillis).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("$at · 연료 ${event.percentBefore}% → ${event.percentAfter}%",fontSize=15.sp)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(event.dismissed,{onDismissedChange(event,it)},enabled=ledger.editable&&!busy);Text("주유 아님")
                    }
                }
            }
        }
    }
}
