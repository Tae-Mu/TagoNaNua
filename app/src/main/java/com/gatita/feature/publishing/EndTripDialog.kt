package com.gatita.feature.publishing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gatita.R
import com.gatita.core.ui.component.*
import com.gatita.core.ui.theme.*

@Composable fun EndTripDialog(name:String,onCancel:()->Unit,onComplete:()->Unit){
 Dialog(onDismissRequest=onCancel){Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface){
  Column(Modifier.width(520.dp).padding(30.dp),verticalArrangement=Arrangement.spacedBy(22.dp)){
   Surface(color=Tint,shape=RoundedCornerShape(16.dp)){Icon(painterResource(R.drawable.ic_flag),null,Modifier.padding(16.dp).size(26.dp),tint=Blue)}
   Text("이번 여행을 마칠까요?",fontSize=28.sp,fontWeight=FontWeight.Bold)
   Note("함께 달린 여정을 마무리할 시간이에요.")
   Surface(color=Tint,shape=RoundedCornerShape(18.dp)){
    Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
     Note("이번 여행");Text(name,fontSize=22.sp,fontWeight=FontWeight.Bold)
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Metric("이동 거리","30 km");Metric("함께한 시간","42 분");Metric("기록 구간","3 개")}
    }
   }
   Note("완료 후 여행 요약을 보고, 정산은 나중에 해도 돼요.")
   Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
    Action("계속 기록",false,modifier=Modifier.weight(1f),onClick=onCancel)
    Action("여행 완료",modifier=Modifier.weight(1f),onClick=onComplete)
   }
  }
 }}
}
