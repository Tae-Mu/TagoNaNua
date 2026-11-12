package com.gatita.feature.publishing

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatita.R
import com.gatita.core.ui.component.*
import com.gatita.core.ui.theme.*

@Composable internal fun CostsScreen(state:SampleState,go:(Page)->Unit,confirm:(String,()->Unit)->Unit,onEdit:(CostUi)->Unit){
Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),border=BorderStroke(1.dp,Line)) {
Row(Modifier.fillMaxWidth()) {
Column(Modifier.weight(1.3f).background(Navy).padding(28.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
Metric("등록한 총비용",if(state==SampleState.Empty)"0 원" else "149,600 원",true)
Text("등록한 비용 ${if(state==SampleState.Empty)0 else 4}건",color=androidx.compose.ui.graphics.Color(0xFFC5D9E8))
Action("＋ 비용 추가") {go(Page.CostKind)}
}
Column(Modifier.weight(1f).padding(28.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
Metric("함께 나눌 금액",if(state==SampleState.Empty)"0 원" else "149,600 원")
Note("모든 비용을 정산에 포함했어요.")
}
}}
Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
sampleCosts.forEach { cost -> Panel(Modifier.weight(1f)) {
Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
CostIcon(cost.kind);Column {Note(cost.kind);Text(if(state==SampleState.Empty)"0원" else "${cost.amount}원",fontSize=20.sp)}
}}}
}
Text("비용 내역",fontSize=20.sp,fontWeight=FontWeight.Bold)
if(state==SampleState.Empty)EmptyState("아직 등록한 비용이 없어요","주유·주차·톨비 등 함께 쓴 비용을 추가해 보세요.")
else Panel(Modifier.fillMaxWidth()) {
sampleCosts.forEachIndexed { index,cost ->
var included by rememberSaveable(cost.title){mutableStateOf(true)}
if(index>0)HorizontalDivider(color=Line)
Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
CostIcon(cost.kind)
Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){Note(cost.kind);Text(cost.title,fontSize=18.sp,fontWeight=FontWeight.Medium);Note(cost.detail)}
AmountLabel(cost.amount,"원",24,if(included)Ink else Muted)
Column(horizontalAlignment=Alignment.End){Row(verticalAlignment=Alignment.CenterVertically){Checkbox(included,{included=it});Text("정산 포함",fontSize=15.sp)}
Row {TextButton({onEdit(cost)}){Text("수정")};TextButton({confirm("${cost.title} 비용을 삭제할까요?\n${cost.amount}원") {}}){Text("삭제")}}}
}
}}
}
