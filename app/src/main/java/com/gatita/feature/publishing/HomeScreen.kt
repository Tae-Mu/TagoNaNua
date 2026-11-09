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
internal fun HomeScreen(go: (Page) -> Unit,onSelect:(String)->Unit) {
    Panel(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 600.dp) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HomeIntroduction(go)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(painterResource(R.drawable.vehicle), "이번 여행을 함께할 차량 시안",
                            Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Fit)
                        Note("이번 여행을 함께할")
                        Text("아반떼 CN8", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        Note("차량 정보로 자동 연결 · 샘플")
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    HomeIntroduction(go)
                    Image(painterResource(R.drawable.vehicle), "이번 여행을 함께할 차량 시안",
                        Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Fit)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("이어서 할 일  ·  1", Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Action("전체 정산",false,arrow=true,leadingIcon=R.drawable.ic_receipt){go(Page.Settlement)}
    }
    TripRow("주말 나들이", "50,000", go,onSelect=onSelect)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        listOf("출발 전에 시작" to "운전자를 포함한 인원을 확인해요.", "이동은 자동 기록" to "잠시 쉬어도 같은 여행으로 이어가요.", "도착하면 정산" to "금액을 확정하고 요약을 복사해요.").forEachIndexed { i, (title, note) ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("0${i + 1}", color = Blue, fontSize = 13.sp)
                Column { Text(title, fontWeight = FontWeight.Medium); Note(note) }
            }
        }
    }
}

@Composable
private fun HomeIntroduction(go: (Page) -> Unit) {
    Text("TAGO · 타고, 나눠", color = Blue, fontSize = 12.sp)
    Text("여행은 함께.\n정산은 가볍게.", fontSize = 38.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold)
    Text("출발부터 도착까지 기록하고,\n함께 탄 만큼 편하게 나눠요.",color=Muted,fontSize=16.sp,lineHeight=25.sp)
    Action("새 여행 준비",modifier=Modifier.widthIn(min=240.dp),arrow=true) { go(Page.Prepare) }
    Note("인원만 확인하면 출발 준비 끝")
}
