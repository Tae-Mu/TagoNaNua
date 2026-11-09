package com.gatita.feature.publishing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatita.core.ui.component.*
import com.gatita.core.ui.theme.*

@Composable internal fun SettingsScreen(dark:Boolean,onDark:(Boolean)->Unit){
 val systemDark=androidx.compose.foundation.isSystemInDarkTheme()
 var name by rememberSaveable{mutableStateOf("")}
 var driver by rememberSaveable{mutableStateOf(true)}
 var theme by rememberSaveable{mutableStateOf(if(dark)"야간" else "시스템")}
 var notice by remember{mutableStateOf("")}
 var info by remember{mutableStateOf(false)}
 Surface(color=Tint,shape=RoundedCornerShape(24.dp)){
  Row(Modifier.fillMaxWidth().padding(28.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)){
   Surface(shape=RoundedCornerShape(18.dp)){Text(name.take(1).ifEmpty{"나"},Modifier.padding(18.dp),color=Blue,fontSize=24.sp)}
   Column(Modifier.weight(1f)){Text(name.ifEmpty{"타고와 함께하는 운전자"},fontSize=22.sp,fontWeight=FontWeight.Bold);Note("편한 여행을 위한 나의 기본 설정")}
   Note("설정 화면 시안")
  }
 }
 if(notice.isNotEmpty())Text(notice,color=Blue)
 Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(20.dp)){
  Panel(Modifier.weight(1f).fillMaxHeight()){Category("PROFILE","내 정보");Input("정산에 표시할 이름",name,{name=it.take(20)},"예: 민수");Note("비워두면 ‘나 (운전자)’로 표시해요.")}
  Panel(Modifier.weight(1f).fillMaxHeight()){Category("SETTLEMENT","정산 기본값")
   Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("나 (운전자) 포함",fontWeight=FontWeight.SemiBold);Note("새로 완료하는 여행에 적용해요.\n각 여행의 정산 화면에서 바꿀 수 있어요.")};Switch(driver,{driver=it})}
   Note("혼자 탄 여행은 본인이 부담해요.")
  }
 }
 Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(20.dp)){
  Panel(Modifier.weight(1f).fillMaxHeight()){Category("APPEARANCE","화면 모드")
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("시스템","주간","야간").forEach{FilterChip(theme==it,{theme=it},label={Text(it)})}}
   Note("저장하면 선택한 모드로 바뀌어요.")
  }
  Panel(Modifier.weight(1f).fillMaxHeight()){Category("VEHICLE","연결 차량");Text("아반떼 CN8    시연 차량",fontSize=18.sp);Note("실제 차량에 연결된 상태가 아니에요.")}
 }
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("퍼블리싱 세션에서만 적용돼요. 실제 저장은 연결 전이에요.",Modifier.weight(1f),color=Muted,fontSize=13.sp);Action("설정 저장"){onDark(theme=="야간" || (theme=="시스템" && systemDark));notice="현재 화면에 설정을 적용했어요."}}
 Panel(Modifier.fillMaxWidth()){
  Text("데이터와 앱 정보",fontSize=20.sp,fontWeight=FontWeight.Bold)
  TextButton({info=true}){Text("저장 데이터 안내 · 설정과 여행 기록의 보관 방식  ›")}
  HorizontalDivider(color=Line)
  TextButton({name="";driver=true;theme="시스템";onDark(systemDark);notice="기본 설정으로 되돌렸어요."}){Text("설정 초기화 · 이름·화면 모드·정산 기본값 되돌리기  ›")}
  Note("TAGO · 타고                         디자인 시안 · 계정 및 송금 기능 없음")
 }
 if(info)AlertDialog(onDismissRequest={info=false},title={Text("저장 데이터 안내")},text={Text("이 앱은 UI 검토용 시안입니다. 차량 연결과 여행·비용 기록의 영속 저장은 연결하지 않았어요.")},confirmButton={TextButton({info=false}){Text("확인")}})
}
@Composable private fun Category(label:String,title:String){Text(label,fontSize=11.sp,color=Muted,letterSpacing=1.sp);Text(title,fontSize=20.sp,fontWeight=FontWeight.Bold)}
