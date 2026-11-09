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

enum class Page(val title:String){ Home("여행"),Prepare("출발 준비"),Departure("기록 시작"),Trip("여행 기록"),Resume("잠시 멈춘 여행"),Complete("여행 완료"),Costs("이번 여행 비용"),CostKind("비용 추가"),Fuel("주유 추가"),Expense("기타 비용 추가"),Settlement("정산"),Detail("정산 상세"),History("여행 내역"),Settings("설정") }
enum class SampleState(val label:String){ Default("기본"),Empty("빈 상태"),Error("오류"),Loading("로딩"),Driving("주행") }
data class CostUi(val kind:String,val title:String,val detail:String,val amount:String)
val sampleCosts=listOf(CostUi("주유","강릉 주유소","2026. 11. 8 · 1,890원/L · 37.037 L","70,000"),CostUi("주차","중앙시장 공영주차장","주차비","19,800"),CostUi("톨비","대관령 톨게이트","통행료","29,800"),CostUi("기타","세차 비용","기타 비용","30,000"))
// These screens display fixtures and emit UI events. They do not calculate or persist trips.
@Composable fun PublishingScreen(page:Page,state:SampleState,go:(Page)->Unit,confirm:(String,()->Unit)->Unit,dark:Boolean,onDark:(Boolean)->Unit,tripName:String="새로운 여행",people:Int=4,onStart:(String,Int)->Unit={_,_->go(Page.Departure)},revealTrip:Boolean=false,expenseKind:String="주차비",onExpenseKind:(String)->Unit={},onSelect:(String)->Unit={},detailTitle:String=tripName,editor:CostUi?=null,onEdit:(CostUi)->Unit={},costReturn:Page=Page.Trip,onRetry:()->Unit={}){
 if(state==SampleState.Loading){
 Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
  Text("화면을 준비하고 있어요.",color=Muted)
  LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
  repeat(2){Panel(Modifier.fillMaxWidth()){
   Surface(color=Tint,shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),modifier=Modifier.fillMaxWidth(.35f).height(18.dp)){}
   Surface(color=Tint,shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),modifier=Modifier.fillMaxWidth().height(if(page in listOf(Page.Fuel,Page.Expense))56.dp else 92.dp)){}
  }}
 }
 return}
 if(state==SampleState.Error && page !in listOf(Page.Fuel,Page.Expense)){Panel(Modifier.fillMaxWidth()){Text("기록을 불러오지 못했어요",fontWeight=FontWeight.Bold)
Note("마지막 기록은 유지돼요. 잠시 후 다시 확인해 주세요.")
Action("다시 확인",onClick=onRetry)}}
 when(page){
 Page.Home->HomeScreen(go,onSelect)
 Page.Prepare->PrepareScreen(go,onStart)
 Page.Departure->DepartureScreen(tripName){go(Page.Trip)}
 Page.Trip->TripScreen(state,go,confirm,revealTrip)
 Page.Resume->ResumeScreen(go,confirm,tripName,people)
 Page.Complete->CompleteScreen(tripName,people,go)
 Page.CostKind->CostKindScreen(go,onExpenseKind,if(costReturn==Page.Detail)detailTitle else tripName)
 Page.Fuel->FuelScreen(state,go,if(costReturn==Page.Detail)detailTitle else tripName,editor,costReturn)
 Page.Expense->ExpenseScreen(state,go,expenseKind,onExpenseKind,if(costReturn==Page.Detail)detailTitle else tripName,editor,costReturn)
 Page.Costs->CostsScreen(state,go,confirm,onEdit)
 Page.Settlement->SettlementScreen(state,go,confirm,onSelect)
 Page.Detail->SettlementDetail(go,confirm,previewTrip(detailTitle))
 Page.History->HistoryScreen(state,go,onSelect)
 Page.Settings->SettingsScreen(dark,onDark)
 }
}
