package com.gatita.feature.publishing

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatita.core.ui.component.*
import com.gatita.core.ui.theme.*

@Composable internal fun FuelScreen(state:SampleState,go:(Page)->Unit,tripName:String,editor:CostUi?=null,returnTo:Page=Page.Trip){
 var amount by rememberSaveable{mutableStateOf(editor?.amount?.replace(",","")?:"")};var price by rememberSaveable{mutableStateOf(if(editor!=null)"1890" else "")}
 var station by rememberSaveable{mutableStateOf(editor?.title?:"")};var details by rememberSaveable{mutableStateOf(false)}
 var date by rememberSaveable{mutableStateOf("2026-11-08")}
 var dateOpen by remember{mutableStateOf(false)}
 Panel(Modifier.fillMaxWidth().animateContentSize()){
  Surface(color=Tint,shape=RoundedCornerShape(10.dp),modifier=Modifier.fillMaxWidth()){Text(tripName,Modifier.padding(14.dp),color=Blue,fontSize=13.sp)}
  Row(horizontalArrangement=Arrangement.spacedBy(20.dp)){
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){MoneyInput("결제 금액",amount,{amount=it},"50,000","원");Note("주유 영수증의 총 결제 금액이에요.")}
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){MoneyInput("1L당 가격",price,{price=it},"1,890","원 / L");Note("영수증에 표시된 리터당 가격이에요.")}
  }
  Surface(color=Tint,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp)){Text("계산한 주유량",Modifier.weight(1f),color=Muted,fontSize=13.sp);Text(if(amount.isBlank()||price.isBlank())"금액과 단가를 입력해 주세요" else "주유량 표시 · 계산 연동 전",color=Blue,fontSize=14.sp)}}
  Surface(onClick={details=!details},shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Line)){
   Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("주유 상세 정보   선택",fontWeight=FontWeight.SemiBold);Note("주유소와 날짜를 함께 남겨요")};Text(if(details)"⌃" else "⌄",color=Blue,fontSize=24.sp)}
  }
  if(details){Input("주유소 · 선택",station,{station=it},"예: 강릉 주유소");Action(date.replace("-",". "),false){dateOpen=true}}
  if(state==SampleState.Error)Text("결제 금액과 단가를 확인해 주세요.",color=MaterialTheme.colorScheme.error)
  Action(if(editor==null)"추가하기" else "수정 완료",enabled=amount.toLongOrNull()?.let{it in 1..10000000}==true && price.toLongOrNull()?.let{it>0}==true,modifier=Modifier.fillMaxWidth()){go(returnTo)}
  Note("정산할 때 함께 나눌 주유 내역을 선택할 수 있어요.")
 }
 if(dateOpen)DayPicker(date,{date=it;dateOpen=false},{dateOpen=false})
}
@Composable internal fun MoneyInput(label:String,value:String,onValue:(String)->Unit,placeholder:String,unit:String){
 val invalid=value.isNotBlank() && value.toLongOrNull()?.let{it in 1..10000000}!=true
 Column(Modifier.animateContentSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text(label,color=Muted,fontSize=14.sp)
  OutlinedTextField(value,onValue,isError=invalid,supportingText=if(invalid){{Text("1~10,000,000 사이의 정수를 입력해 주세요.")}}else null,singleLine=true,placeholder={Text(placeholder,fontSize=32.sp,color=Muted.copy(alpha=.55f))},suffix={Text(unit,color=Muted,fontSize=14.sp)},textStyle=TextStyle(fontFamily=Suit,fontSize=32.sp,color=Ink),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),shape=RoundedCornerShape(12.dp),colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=MaterialTheme.colorScheme.background,unfocusedBorderColor=Line,focusedBorderColor=Blue),modifier=Modifier.fillMaxWidth())
 }
}
