package com.gatita.feature.publishing

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatita.core.ui.component.*
import com.gatita.core.ui.theme.*

@Composable internal fun SettlementDetail(go:(Page)->Unit,confirm:(String,()->Unit)->Unit,trip:TripPreview){
 var driver by rememberSaveable(trip.title){mutableStateOf(true)}
 var people by rememberSaveable(trip.title){mutableIntStateOf(trip.people)}
 var fixed by rememberSaveable(trip.title){mutableStateOf(trip.fixed)}
 var breakdown by remember{mutableStateOf(false)}
 val count=if(driver)people else (people-1).coerceAtLeast(1)
 val share=trip.total/count
 val remainder=trip.total%count
 val fuel=if(trip.total==149600L)70000L else trip.total
 var summary by remember{mutableStateOf(false)}
 val clipboard=androidx.compose.ui.platform.LocalClipboardManager.current
 val summaryText="${trip.title} · 총 ${won(trip.total)}원 · ${count}명 분담 · 실제 입금은 별도 확인"
 Text(if(fixed)"정산 확정 · 확정한 금액을 보관했어요." else "금액 확인 · 함께 나눌 비용 내역과 인원을 확인해 주세요.",color=Muted,fontSize=14.sp)
 Surface(shape=RoundedCornerShape(24.dp),border=BorderStroke(1.dp,Line)){
  Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)){
   Column(Modifier.weight(1f).fillMaxHeight().background(Navy).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
    Text("TRIP TOTAL",color=androidx.compose.ui.graphics.Color(0xFFC5D9E8),fontSize=11.sp,letterSpacing=1.6.sp)
    Text("함께 나눌 총금액",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.SemiBold)
    AmountLabel(won(trip.total),"원",48,Color.White)
    Text("주유비 ${won(fuel)}원 · 기타 비용 ${won(trip.total-fuel)}원",color=androidx.compose.ui.graphics.Color(0xFFC5D9E8),fontSize=13.sp)
    HorizontalDivider(color=androidx.compose.ui.graphics.Color(0xFFC5D9E8).copy(alpha=.2f))
    Row(Modifier.fillMaxWidth()){Text("정산에 포함한 비용",Modifier.weight(1f),color=androidx.compose.ui.graphics.Color(0xFFC5D9E8),fontSize=13.sp);Text(if(trip.total==149600L)"4 건" else "1 건",color=Color.White,fontSize=22.sp)}
   }
   Column(Modifier.weight(1f).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
    Text("SPLIT TOGETHER",color=Muted,fontSize=11.sp,letterSpacing=1.6.sp)
    Text("${if(driver)people else (people-1).coerceAtLeast(1)}명이 함께 나누면",fontSize=22.sp,fontWeight=FontWeight.SemiBold)
    AmountLabel(if(remainder==0L)won(share) else "${won(share)}–${won(share+1)}","원 / 인",36,Blue)
    Note(if(driver)"운전자를 포함한 균등 분담" else "운전자를 제외한 균등 분담")
    TextButton({breakdown=true}){Text("개인별 전체 명세                       ${count}명 보기 ›")}
    HorizontalDivider(color=Line)
    Row(verticalAlignment=Alignment.CenterVertically){Text("함께 탄 인원",Modifier.weight(1f),color=Muted,fontSize=14.sp);IconButton({people--;if(people==1)driver=true},enabled=!fixed&&people>1){Text("−",fontSize=24.sp)};Text("${people}명");IconButton({people++},enabled=!fixed&&people<8){Text("+",fontSize=24.sp)}}
    HorizontalDivider(color=Line)
    Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("나 (운전자) 포함",fontWeight=FontWeight.SemiBold);Note(if(people==1)"혼자 탄 여행은 본인이 부담해요." else if(driver)"나도 여행 비용을 함께 나눠요." else "동승자끼리 여행 비용을 나눠요.")};Switch(driver,{driver=it},enabled=!fixed&&people>1)}
   }
  }
 }
 Panel(Modifier.fillMaxWidth()){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("나눌 주유 내역   1건",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold);if(!fixed)Action("＋ 주유 추가",false){go(Page.Fuel)}}
  Note("주유한 금액을 나누는 방식이에요. 여행에서 실제 소비한 연료비와 다를 수 있어요.")
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(trip.fixed)"샘플 주유소" else "강릉 주유소",fontWeight=FontWeight.SemiBold);Note("2026. 11. ${trip.day} · 1,890원/L")};AmountLabel(won(fuel),"원",26)}
 }
 Panel(Modifier.fillMaxWidth()){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("기타 비용   ${if(trip.total==149600L)3 else 0}건",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold);if(!fixed)Action("＋ 기타 비용 추가",false){go(Page.CostKind)}}
  Note("주차비·통행료 등을 주유비와 함께 나눠요. 직접 입력한 결제 금액이에요.")
  if(trip.total!=149600L)Note("등록한 기타 비용이 없어요.")
  (if(trip.total==149600L)sampleCosts.drop(1) else emptyList()).forEach{cost->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){CostIcon(cost.kind);Text(cost.title,Modifier.weight(1f).padding(start=12.dp));Text("${cost.amount} 원",fontSize=22.sp)}}
 }
 HorizontalDivider(color=Line)
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
  Text("✓  확정 전에 포함한 비용 내역과 인원을 확인해 주세요.",Modifier.weight(1f),color=Muted,fontSize=13.sp)
  Action("나중에 정산",false){go(Page.Settlement)}
  if(!fixed)Action("이 금액으로 확정",arrow=true){confirm("정산을 확정할까요?"){fixed=true}}else {Action("요약 보기",false){summary=true};Action("정산 요약 복사"){clipboard.setText(androidx.compose.ui.text.AnnotatedString(summaryText));summary=true}}
 }
 Note("퍼블리싱 샘플 · 실제 정산 계산 및 저장은 연결 전이에요.")
 if(summary)AlertDialog(onDismissRequest={summary=false},title={Text("정산 요약")},text={Text(summaryText)},confirmButton={TextButton({summary=false}){Text("닫기")}})
 if(breakdown)AlertDialog(onDismissRequest={breakdown=false},title={Text("개인별 전체 명세")},text={Column{(1..count).forEach{Text("${if(driver&&it==1)"나 (운전자)" else "참여자 ${if(driver)it else it+1}"}    ${won(share+if(it<=remainder)1 else 0)}원 · 샘플",Modifier.padding(8.dp))}}},confirmButton={TextButton({breakdown=false}){Text("닫기")}})
}
