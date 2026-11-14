package com.gatita.feature.publishing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatita.core.ui.component.*
import com.gatita.core.ui.theme.*

@Composable internal fun CompleteScreen(name:String,people:Int,go:(Page)->Unit){
 Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.TopCenter){
  Column(Modifier.widthIn(max=700.dp).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){
   Brand(false)
   Text("함께한 여정, 기록 완료",color=Blue,fontSize=15.sp)
   Text("안전하게 도착했어요",fontSize=36.sp,fontWeight=FontWeight.Bold)
   Note("수고했어요. 함께한 여행을 돌아볼까요?")
   MotionSection(true,180,14f){
    Panel(Modifier.fillMaxWidth()){
     Note("2026. 11. 8 · 아반떼 CN8")
     Text(name,fontSize=23.sp,fontWeight=FontWeight.Bold)
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(28.dp)){
      Column(Modifier.weight(1f)){Note("함께 이동한 거리");AmountLabel("30","km",40,Blue)}
      VerticalDivider(Modifier.height(80.dp),color=Line)
      Column(Modifier.weight(1f)){Note("함께한 시간");AmountLabel("42","분",32,Blue)}
     }
     HorizontalDivider(color=Line)
     Note("3개 구간 기록       함께 탄 ${people}명")
    }
   }
   Note("비용 4건 · 149,600원")
   Text("이제 함께 쓴 비용을 나눠보세요.",fontSize=15.sp)
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
    Action("나중에 정산",false,modifier=Modifier.weight(1f)){go(Page.Home)}
    Action("정산하기",modifier=Modifier.weight(1.35f),arrow=true){go(Page.Detail)}
   }
   Note("나중에 정산해도 ‘정산’ 메뉴에서 이어갈 수 있어요.")
  }
 }
}
