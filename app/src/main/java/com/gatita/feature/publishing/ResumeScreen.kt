package com.gatita.feature.publishing

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import com.gatita.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatita.core.ui.component.*
import com.gatita.core.ui.theme.*

@Composable internal fun ResumeScreen(go:(Page)->Unit,confirm:(String,()->Unit)->Unit,name:String,people:Int){
 Panel(Modifier.fillMaxWidth()) {
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
    Text("Ⅱ  이어갈 수 있는 여행",color=Blue,fontSize=15.sp)
    Text(name,fontSize=28.sp,fontWeight=FontWeight.Bold)
    Note("함께 타는 ${people}명")
   }
   Surface(color=Tint,shape=RoundedCornerShape(22.dp)){Icon(painterResource(R.drawable.ic_route),null,Modifier.padding(18.dp).size(28.dp),tint=Blue)}
  }
  Surface(color=Navy,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){
   Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(32.dp)){
    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){
     Text("함께 이동한 거리",color=androidx.compose.ui.graphics.Color(0xFFC5D9E8),fontSize=15.sp)
     Row(verticalAlignment=Alignment.Bottom){Text("30.0",fontSize=40.sp,color=Color.White);Text(" km",color=androidx.compose.ui.graphics.Color(0xFFC5D9E8),modifier=Modifier.padding(bottom=8.dp))}
    }
    VerticalDivider(Modifier.height(68.dp),color=Tint.copy(alpha=.2f))
    Metric("기록 시간","42 분",true);Metric("기록한 구간","3 개",true)
   }
  }
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)){
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("이번 여행 비용  4건",fontWeight=FontWeight.SemiBold);Note("등록한 비용도 함께 보관했어요.")}
   Text("149,600 원",fontSize=24.sp)
   Action("＋ 비용 추가",false){go(Page.CostKind)}
  }
  HorizontalDivider(color=Line)
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text("✓  여기까지 기록했어요",fontSize=16.sp);Note("저장 상태 시안 · 실제 기록 저장 없음")}
   Action("여행 이어가기",modifier=Modifier.widthIn(min=230.dp),arrow=true){go(Page.Trip)}
  }
 }
 Surface(shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Line)){
  Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
   Surface(color=Tint,shape=RoundedCornerShape(12.dp)){Icon(painterResource(R.drawable.ic_flag),null,Modifier.padding(12.dp).size(22.dp),tint=Blue)}
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("목적지에 도착하셨나요?",fontWeight=FontWeight.SemiBold);Note("여행을 마치고 함께 쓴 비용을 확인하세요.")}
   Action("여행 마치기",false){confirm("이번 여행을 마칠까요?"){go(Page.Complete)}}
  }
 }
 Note("시안 안내  정차로 시동 OFF를 재현해요. 거리·시간은 샘플이며 실제 차량과 연결되지 않아요.")
}
