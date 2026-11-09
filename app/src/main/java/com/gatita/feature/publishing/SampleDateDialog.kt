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

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SampleDateDialog(monthOnly:Boolean,close:()->Unit){val state=rememberDatePickerState(initialSelectedDateMillis=1794096000000L)
DatePickerDialog(onDismissRequest=close,confirmButton={TextButton(close){Text("선택")}},dismissButton={TextButton(close){Text("취소")}}){if(monthOnly){Column(Modifier.padding(24.dp)){Text("여행한 달",fontSize=24.sp)
FlowRow{(1..12).forEach{TextButton(close){Text("${it}월")}}}}}else DatePicker(state)}}

@Composable internal fun MonthPicker(value:String,onApply:(String)->Unit,close:()->Unit){
 var year by remember{mutableIntStateOf(value.substringBefore("년").toInt())}
 var month by remember{mutableIntStateOf(value.substringAfter("년 ").substringBefore("월").toInt())}
 androidx.compose.ui.window.Dialog(onDismissRequest=close){
  Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface){
   Column(Modifier.width(420.dp).padding(30.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("●  여행 내역",color=Blue,fontSize=11.sp);Text("여행한 달 선택",fontSize=24.sp,fontWeight=FontWeight.Bold)};TextButton(close){Text("×",fontSize=24.sp)}}
    Surface(color=MaterialTheme.colorScheme.background,shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp)){
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton({year--}){Text("‹",fontSize=24.sp)};Box(Modifier.weight(1f),contentAlignment=Alignment.Center){Text("${year}년",fontSize=21.sp,fontWeight=FontWeight.SemiBold)};IconButton({year++}){Text("›",fontSize=24.sp)}}
    }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)){(1..12).chunked(3).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){row.forEach{m->Action("${m}월",primary=m==month,modifier=Modifier.weight(1f)){month=m}}}}}
    Surface(color=Tint,shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp)){Row(Modifier.fillMaxWidth().padding(16.dp)){Text("선택한 달",Modifier.weight(1f),fontSize=12.sp,color=Muted);Text("${year}년 ${month}월",fontSize=14.sp,color=Blue)}}
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Action("취소",false,modifier=Modifier.weight(1f),onClick=close);Action("적용",modifier=Modifier.weight(1.7f)){onApply("${year}년 ${month}월")}}
   }
  }
 }
}

@Composable internal fun DayPicker(value:String,onApply:(String)->Unit,close:()->Unit){
 var selected by remember{mutableStateOf(java.time.LocalDate.parse(value))}
 var displayed by remember{mutableStateOf(java.time.YearMonth.from(selected))}
 androidx.compose.ui.window.Dialog(onDismissRequest=close){
  Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp)){
   Column(Modifier.widthIn(max=420.dp).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Text("주유한 날짜",fontSize=24.sp,fontWeight=FontWeight.Bold)
    Row(verticalAlignment=Alignment.CenterVertically){IconButton({displayed=displayed.minusMonths(1)}){Text("‹ 이전 달")};Text("${displayed.year}년 ${displayed.monthValue}월",Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center);IconButton({displayed=displayed.plusMonths(1)}){Text("다음 달 ›")}}
    Row{listOf("월","화","수","목","금","토","일").forEach{Text(it,Modifier.weight(1f),color=Muted,textAlign=androidx.compose.ui.text.style.TextAlign.Center)}}
    val offset=displayed.atDay(1).dayOfWeek.value-1
    (0 until ((offset+displayed.lengthOfMonth()+6)/7)).forEach{week->Row{(0..6).forEach{col->val day=week*7+col-offset+1
      if(day in 1..displayed.lengthOfMonth()){
       val date=displayed.atDay(day)
       TextButton({selected=date},modifier=Modifier.weight(1f).height(44.dp),colors=ButtonDefaults.textButtonColors(containerColor=if(date==selected)Blue else androidx.compose.ui.graphics.Color.Transparent,contentColor=if(date==selected)MaterialTheme.colorScheme.onPrimary else Ink)){Text("$day")}
      }else Spacer(Modifier.weight(1f))
    }}}
    Surface(color=Tint,shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp)){Text("선택한 날짜   ${selected.year}. ${selected.monthValue}. ${selected.dayOfMonth}",Modifier.fillMaxWidth().padding(14.dp),color=Blue)}
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Action("취소",false,modifier=Modifier.weight(1f),onClick=close);Action("적용",modifier=Modifier.weight(1f)){onApply(selected.toString())}}
   }
  }
 }
}
