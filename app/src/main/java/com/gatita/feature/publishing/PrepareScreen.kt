@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gatita.feature.publishing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable internal fun PrepareScreen(go:(Page)->Unit,onStart:(String,Int)->Unit){
 var name by rememberSaveable{mutableStateOf("")}
 var people by rememberSaveable{mutableIntStateOf(4)}
 FlowRow(horizontalArrangement=Arrangement.spacedBy(24.dp)){
  listOf("출발 준비","여행 기록","금액 확인","정산 완료").forEachIndexed{i,t->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
   Surface(color=if(i==0)Blue else Tint,shape=RoundedCornerShape(50)){Text("0${i+1}",Modifier.padding(8.dp),color=if(i==0)MaterialTheme.colorScheme.surface else Muted,fontSize=15.sp)}
   Text(t,fontSize=16.sp,color=if(i==0)Blue else Muted)
  }}
 }
 HorizontalDivider(color=Line)
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(24.dp)){
  Panel(Modifier.weight(1.15f)){
   Text("YOUR NEXT JOURNEY",color=Muted,fontSize=16.sp,letterSpacing=1.sp)
   Text("이번 여행을 알려주세요",fontSize=26.sp,fontWeight=FontWeight.Bold)
   Input("여행 이름 · 선택",name,{name=it},"예: 강릉으로 떠나는 주말")
   Note("비워두면 ‘새로운 여행’으로 표시해요.")
   HorizontalDivider(color=Line)
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
    Column(Modifier.weight(1f)){Text("함께 타는 인원",fontSize=19.sp,fontWeight=FontWeight.SemiBold);Note("나 (운전자) 포함")}
    Action("−",false,people>1){people--};Text("${people}명",fontSize=19.sp);Action("+",false,people<8){people++}
   }
   Note("1~8명 · 도착 후 정산 전에도 바꿀 수 있어요.")
   Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
    Action("돌아가기",false){go(Page.Home)}
    Action("여행 기록 시작",modifier=Modifier.weight(1f),arrow=true){onStart(name.trim().ifEmpty{"새로운 여행"},people)}
   }
  }
  Surface(Modifier.weight(1f),color=Tint,shape=RoundedCornerShape(22.dp)){
   Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
    Image(painterResource(R.drawable.vehicle),"아반떼 CN8",Modifier.fillMaxWidth().height(190.dp),contentScale=ContentScale.Fit)
    Note("이번 여행을 함께할");Text("아반떼 CN8",fontSize=22.sp,fontWeight=FontWeight.Medium);Note("차량 정보로 자동 연결 · 샘플")
    HorizontalDivider(color=Line)
    Text("주유 내역은 도착 후 추가해도 돼요",fontWeight=FontWeight.SemiBold,fontSize=17.sp)
    Note("여행별로 등록한 주유 결제액을 모아 나눠요. 실제 결제나 송금은 진행되지 않아요.")
   }
  }
 }
}
