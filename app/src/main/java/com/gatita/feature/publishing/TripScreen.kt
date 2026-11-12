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

@Composable
internal fun TripScreen(state: SampleState, go: (Page) -> Unit, confirm: (String, () -> Unit) -> Unit, reveal: Boolean = false) {
    MotionSection(reveal,350,28f) {
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Line)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 224.dp).height(IntrinsicSize.Min)) {
            Column(Modifier.weight(1.1f).fillMaxHeight().background(Navy).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("●  여행 기록 중", color = androidx.compose.ui.graphics.Color(0xFFC5D9E8), fontSize = 15.sp)
                Text("함께 이동한 거리", color = androidx.compose.ui.graphics.Color(0xFFC5D9E8))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if(state == SampleState.Empty) "0.0" else "30.0", fontSize = 64.sp, lineHeight = 70.sp, color = androidx.compose.ui.graphics.Color.White)
                    Text(" km", color = androidx.compose.ui.graphics.Color(0xFFC5D9E8), fontSize = 24.sp, modifier = Modifier.padding(bottom = 12.dp))
                }
                HorizontalDivider(color = androidx.compose.ui.graphics.Color.White.copy(alpha = .15f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("함께한 시간", color = androidx.compose.ui.graphics.Color(0xFFC5D9E8), modifier = Modifier.weight(1f))
                    Text(if(state==SampleState.Empty)"00:00" else "00:42", fontSize = 30.sp, color = androidx.compose.ui.graphics.Color.White)
                    Text("  시:분", color = androidx.compose.ui.graphics.Color(0xFFC5D9E8), fontSize = 15.sp)
                }
            }
            Column(Modifier.weight(1f).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("TRIP STATUS", color = Muted, fontSize = 15.sp)
                Text(if(state == SampleState.Driving) "함께 가는 중이에요" else if(state==SampleState.Empty) "출발할 준비가\n됐어요" else "여행은 계속\n이어지고 있어요", fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                Note("시동을 끄면 구간이 자동 저장돼요.")
                HorizontalDivider(color = Line)
                Row(Modifier.fillMaxWidth()) { Text("저장된 구간", Modifier.weight(1f), color = Muted); Text(if(state==SampleState.Empty)"0 개" else "3 개", fontSize = 26.sp) }
            }
        }
    }
    }
    MotionSection(reveal,620,22f) {
    Row(Modifier.fillMaxWidth()){Text("이번 여행의 기록   ${if(state==SampleState.Empty)0 else 3}개 구간",Modifier.weight(1f),fontSize=16.sp,fontWeight=FontWeight.Bold);Note("시동 OFF 시 자동 저장")}
    if(state == SampleState.Empty) EmptyState("첫 구간을 기다리고 있어요", "출발하면 이곳에 여행 기록이 쌓여요.")
    else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("출발 → 경유지" to "10", "경유지 → 주유 지점" to "4", "주유 지점 → 도착" to "16").forEachIndexed { index, (path, km) ->
            Panel(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth()) { Note("구간 0${index + 1}"); Spacer(Modifier.weight(1f)); Text("✓ 저장됨", color = Blue, fontSize = 15.sp) }
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)){Note(path.substringBefore(" → ")+" ›");Text(path.substringAfter(" → "),fontWeight=FontWeight.SemiBold)}; AmountLabel(km,"km",26) }
            }
        }
    }
    }
    MotionSection(reveal,730,16f) {
    Surface(onClick={go(Page.Costs)},shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Line)){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("이번 여행 비용",fontWeight=FontWeight.SemiBold);Note("주유·주차·톨비를 한곳에서 기록해요.")};Text(if(state==SampleState.Empty)"0건 · 0원  ›" else "4건 · 149,600원  ›",color=Blue)}}
    HorizontalDivider(color = Line)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("✓  잠깐 쉬어도 괜찮아요. 도착했을 때 여행을 마치세요.", Modifier.weight(1f), color = Muted, fontSize = 15.sp)
        Action("비용 추가", false, state != SampleState.Driving) { go(Page.CostKind) }
        Action("여행 마치기", enabled = state != SampleState.Driving) { confirm("이번 여행을 마칠까요?") { go(Page.Complete) } }
    }
}
}
