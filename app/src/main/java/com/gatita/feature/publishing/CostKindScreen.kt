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

@Composable internal fun CostKindScreen(go:(Page)->Unit,onKind:(String)->Unit,tripName:String){
 Panel(Modifier.fillMaxWidth()){
 Surface(color=Tint,shape=androidx.compose.foundation.shape.RoundedCornerShape(10.dp)){Text(tripName,Modifier.fillMaxWidth().padding(14.dp),color=Blue)}
 val kinds=listOf("주유" to "결제 금액과 1L당 가격","주차비" to "결제 금액과 주차 장소","톨비" to "결제 금액과 이용 구간","기타 비용" to "결제 금액과 비용 내용")
 kinds.chunked(2).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
  row.forEach{(label,description)->Surface(onClick={if(label!="주유")onKind(if(label=="기타 비용")"기타" else label);go(if(label=="주유")Page.Fuel else Page.Expense)},modifier=Modifier.weight(1f),shape=androidx.compose.foundation.shape.RoundedCornerShape(22.dp),border=BorderStroke(1.dp,Line)){
   Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    CostIcon(label)
    Row(Modifier.fillMaxWidth()){Text(label,Modifier.weight(1f),fontSize=22.sp,fontWeight=FontWeight.Bold);Text("→",color=Blue)}
    Note(description)
   }
  }}
 }}
 Note("장소와 비용 내용은 선택 사항이에요.")
 }
}

@Composable
internal fun CostIcon(kind: String) {
    val resource = when (kind) {
        "주유" -> R.drawable.ic_fuel
        "주차비", "주차" -> R.drawable.ic_parking
        "톨비", "통행료" -> R.drawable.ic_toll
        else -> R.drawable.ic_receipt
    }
    Icon(painterResource(resource), contentDescription = null,
        modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
}
