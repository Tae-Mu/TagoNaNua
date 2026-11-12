@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gatita.feature.publishing

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
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
DatePickerDialog(onDismissRequest=close,confirmButton={TextButton(close){Text("선택")}},dismissButton={Action("취소",primary=false,minHeight=52,textSize=16,horizontalPadding=20,onClick=close)}){if(monthOnly){Column(Modifier.padding(24.dp)){Text("여행한 달",fontSize=24.sp)
FlowRow{(1..12).forEach{TextButton(close){Text("${it}월")}}}}}else DatePicker(state)}}

@Composable internal fun MonthSelector(value:String,expanded:Boolean,onClick:()->Unit) {
 val angle by androidx.compose.animation.core.animateFloatAsState(if(expanded)180f else 0f,
  androidx.compose.animation.core.tween(160),label="monthSelectorChevron")
 Surface(onClick=onClick,shape=androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
  color=androidx.compose.ui.graphics.Color.Transparent,modifier=Modifier.padding(top=4.dp)) {
  Row(Modifier.heightIn(min=52.dp).padding(end=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
   Text(androidx.compose.ui.text.buildAnnotatedString {
    pushStyle(androidx.compose.ui.text.SpanStyle(fontSize=18.sp,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.onSurfaceVariant))
    append(value.substringBefore("년")+"년 ");pop()
    append(value.substringAfter("년 "))
   },fontSize=30.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface)
   Surface(shape=androidx.compose.foundation.shape.CircleShape,color=MaterialTheme.colorScheme.secondaryContainer) {
    Box(Modifier.size(32.dp),contentAlignment=Alignment.Center) {
     Icon(painterResource(R.drawable.ic_chevron_down),if(expanded) "월 선택 열림" else "월 선택 열기",
      Modifier.size(18.dp).graphicsLayer { rotationZ=angle },tint=MaterialTheme.colorScheme.primary)
    }
   }
  }
 }
}

@Composable internal fun MonthPicker(value:String,onApply:(String)->Unit,close:()->Unit){
 val initialYear=value.substringBefore("년").toInt()
 var year by rememberSaveable{mutableIntStateOf(initialYear)}
 var selectedYear by rememberSaveable{mutableIntStateOf(initialYear)}
 var month by rememberSaveable{mutableIntStateOf(value.substringAfter("년 ").substringBefore("월").toInt())}
 VehicleDialog(onDismissRequest=close,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)){
  Surface(modifier=Modifier.padding(24.dp).widthIn(max=520.dp).fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface){
   Column(Modifier.verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(24.dp)){
    Row(verticalAlignment=Alignment.CenterVertically) {
     Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
      Text("여행한 달 선택",fontSize=24.sp,fontWeight=FontWeight.Bold)
      Text("확인할 여행의 연도와 월을 골라주세요.",fontSize=16.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
     }
     IconButton(close){Icon(painterResource(R.drawable.ic_close),"월 선택 닫기",Modifier.size(22.dp))}
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
     FilledTonalIconButton({year--},modifier=Modifier.size(48.dp).semantics { contentDescription="이전 연도" },enabled=year>1){Box(Modifier.then(Modifier.graphicsLayer { rotationZ=180f })){ChevronRight()}}
     Text("${year}년",fontSize=24.sp,fontWeight=FontWeight.SemiBold)
     FilledTonalIconButton({year++},modifier=Modifier.size(48.dp).semantics { contentDescription="다음 연도" },enabled=year<9999){ChevronRight()}
    }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
     (1..12).chunked(4).forEach { months ->
      Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
       months.forEach { m ->
        val chosen=selectedYear==year && month==m
        Surface(onClick={selectedYear=year;month=m},modifier=Modifier.weight(1f).height(58.dp).semantics { selected=chosen },
         shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
         color=if(chosen)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
         contentColor=if(chosen)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) {
          Box(contentAlignment=Alignment.Center){Text("${m}월",fontSize=17.sp,fontWeight=if(chosen)FontWeight.Bold else FontWeight.Medium)}
        }
       }
      }
     }
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
     Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
      Text("선택한 달",fontSize=16.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
      Text("${selectedYear}년 ${month}월",fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)
     }
     Action("취소",primary=false,minHeight=52,textSize=16,horizontalPadding=20,onClick=close)
     Action("적용",minHeight=52,textSize=16,horizontalPadding=24){onApply("${selectedYear}년 ${month}월")}
    }
   }
  }
 }
}

@Composable internal fun DayPicker(value:String,onApply:(String)->Unit,close:()->Unit){
 var selected by remember{mutableStateOf(java.time.LocalDate.parse(value))}
 var displayed by remember{mutableStateOf(java.time.YearMonth.from(selected))}
 VehicleDialog(onDismissRequest=close,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)){
  Surface(modifier=Modifier.padding(24.dp).widthIn(max=520.dp).fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp)){
   Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Text("주유한 날짜",fontSize=24.sp,fontWeight=FontWeight.Bold)
    Row(verticalAlignment=Alignment.CenterVertically){IconButton({displayed=displayed.minusMonths(1)},Modifier.size(56.dp)){Box(Modifier.graphicsLayer{rotationZ=180f}.semantics{contentDescription="이전 달"}){ChevronRight()}};Text("${displayed.year}년 ${displayed.monthValue}월",Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center);IconButton({displayed=displayed.plusMonths(1)},Modifier.size(56.dp)){Box(Modifier.semantics{contentDescription="다음 달"}){ChevronRight()}}}
    Row{listOf("월","화","수","목","금","토","일").forEach{Text(it,Modifier.weight(1f),color=Muted,textAlign=androidx.compose.ui.text.style.TextAlign.Center)}}
    val offset=displayed.atDay(1).dayOfWeek.value-1
    (0 until ((offset+displayed.lengthOfMonth()+6)/7)).forEach{week->Row{(0..6).forEach{col->val day=week*7+col-offset+1
      if(day in 1..displayed.lengthOfMonth()){
       val date=displayed.atDay(day)
       TextButton({selected=date},modifier=Modifier.weight(1f).height(52.dp),colors=ButtonDefaults.textButtonColors(containerColor=if(date==selected)Blue else androidx.compose.ui.graphics.Color.Transparent,contentColor=if(date==selected)MaterialTheme.colorScheme.onPrimary else Ink)){Text("$day")}
      }else Spacer(Modifier.weight(1f))
    }}}
    Surface(color=Tint,shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp)){Text("선택한 날짜   ${selected.year}. ${selected.monthValue}. ${selected.dayOfMonth}",Modifier.fillMaxWidth().padding(14.dp),color=Blue)}
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Action("취소",false,modifier=Modifier.weight(1f),onClick=close);Action("적용",modifier=Modifier.weight(1f)){onApply(selected.toString())}}
   }
  }
 }
}
