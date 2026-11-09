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

@Composable internal fun HistoryScreen(state:SampleState,go:(Page)->Unit,onSelect:(String)->Unit){
 var date by remember{mutableStateOf(false)}
 var month by rememberSaveable{mutableStateOf("2026년 11월")}
 var filter by rememberSaveable{mutableStateOf("전체")}
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(32.dp)){
  Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){Note("여행한 달");Action("$month   ⌄",false){date=true}}
  Metric("여행",if(state==SampleState.Empty)"0 회" else "2 회")
  Metric("함께 간 거리",if(state==SampleState.Empty)"0 km" else "112 km")
  Metric("정산 확정",if(state==SampleState.Empty)"0 건" else "1 건")
 }
 HorizontalDivider(color=Line)
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
  listOf("전체","정산 전","정산 확정").forEach{label->if(filter==label)Action(label){filter=label}else TextButton({filter=label}){Text(label,color=Muted)}}
  Spacer(Modifier.weight(1f));Note(if(state==SampleState.Empty)"0건의 여행" else if(filter=="전체")"2건의 여행" else "1건의 여행")
 }
 if(state==SampleState.Empty)EmptyState("이번 달 여행이 없어요","다른 달을 선택하거나 새 여행을 시작해 보세요.")else{
 Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Line)){Column{
  if(filter!="정산 확정")TripRow("주말 나들이","50,000",go,onSelect=onSelect,joined=true)
  if(filter=="전체")HorizontalDivider(color=Line,modifier=Modifier.padding(horizontal=24.dp))
  if(filter!="정산 전")TripRow("함께한 주말 여행","60,000",go,true,"2","82km · 95분 · 3명",onSelect=onSelect,joined=true)
 }
 }}
 Note("차량 연결 없이 준비한 샘플 여행 내역입니다.")
 if(date)MonthPicker(month,{month=it;date=false},{date=false})
}
