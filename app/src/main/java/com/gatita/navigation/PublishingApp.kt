@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gatita.navigation
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.painterResource
import com.gatita.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.gatita.core.ui.component.*
import com.gatita.core.ui.theme.*
import com.gatita.feature.publishing.*

@Composable fun PublishingApp(){
 val screenState=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
 var page by rememberSaveable { mutableStateOf(Page.Home) }
 var expenseKind by rememberSaveable { mutableStateOf("주차비") }
 var detailTitle by rememberSaveable { mutableStateOf("주말 나들이") }
 var tripName by rememberSaveable { mutableStateOf("새로운 여행") }
 var people by rememberSaveable { mutableIntStateOf(4) }
 var departing by remember { mutableStateOf(false) }
 var revealTrip by remember { mutableStateOf(false) }
 var dark by rememberSaveable { mutableStateOf(false) }
 var sample by rememberSaveable { mutableStateOf(SampleState.Default) }
 var editor by remember { mutableStateOf<CostUi?>(null) }
 var costReturn by remember { mutableStateOf(Page.Trip) }
 var toolsExpanded by remember { mutableStateOf(false) }
 var picker by remember { mutableStateOf(false) }
 var confirmation by remember { mutableStateOf<String?>(null) }
 var confirmAction by remember { mutableStateOf<()->Unit>({}) }
 val history=remember{mutableStateListOf<Page>()}
 val go:(Page)->Unit={next->
  if(next in listOf(Page.CostKind,Page.Fuel,Page.Expense) && page !in listOf(Page.CostKind,Page.Fuel,Page.Expense)){costReturn=page;editor=null}
  if(next==Page.Expense && expenseKind !in listOf("주차비","톨비","기타"))expenseKind="주차비"
  if(next==Page.Departure){history.add(page);revealTrip=false;page=Page.Trip;departing=true}
  else if(next!=page){history.add(page);revealTrip=false;page=next}
 }
 val back:()->Unit={page=if(history.isNotEmpty())history.removeAt(history.lastIndex) else Page.Home}
 BackHandler(page!=Page.Home){back()}
 TagoTheme(dark){Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){BoxWithConstraints(Modifier.safeDrawingPadding()){
 val wide=maxWidth>=600.dp
 Column{
 Row(Modifier.weight(1f)){
 if(wide)Surface(Modifier.width(116.dp).fillMaxHeight()){Column(Modifier.fillMaxHeight().padding(horizontal=12.dp,vertical=28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)){Brand()
Spacer(Modifier.height(12.dp))
listOf(Page.Home,Page.Settlement,Page.History,Page.Settings).forEach{p->if(p==Page.Settings)Spacer(Modifier.weight(1f));TextButton({go(p)}, modifier=Modifier.fillMaxWidth().height(84.dp), shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp), colors=ButtonDefaults.textButtonColors(containerColor=if(selectedNav(page)==p)Tint else androidx.compose.ui.graphics.Color.Transparent, contentColor=if(selectedNav(page)==p)Blue else Muted)){Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(10.dp)){Icon(painterResource(navIcon(p)),null)
Text(p.title)}}}}}
 key(page,if(page==Page.Trip)revealTrip else false) {
 Box(Modifier.weight(1f).fillMaxHeight(),contentAlignment=Alignment.TopCenter){
 Column(Modifier.widthIn(max=if(page in listOf(Page.Fuel,Page.Expense,Page.CostKind))900.dp else 1400.dp).fillMaxWidth().then(pageFadeModifier(sample!=SampleState.Driving)).verticalScroll(rememberScrollState()).padding(horizontal=if(wide)44.dp else 20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 if(page!=Page.Departure && page!=Page.Complete){
 if(page !in listOf(Page.Home,Page.Prepare,Page.Trip,Page.Resume,Page.History,Page.Settings,Page.Settlement))TextButton({if(page in listOf(Page.CostKind,Page.Fuel,Page.Expense))go(costReturn) else back()}){Text(if(page in listOf(Page.CostKind,Page.Fuel,Page.Expense))"‹ ${if(costReturn==Page.Trip || costReturn==Page.Resume)"여행 기록" else if(costReturn==Page.Detail)"정산" else "비용 내역"}으로 돌아가기" else "‹ 목록으로 돌아가기")}
 Title(if(page==Page.Home)"오늘은 어디로 떠나세요?" else if(page==Page.Trip)tripName else if(page==Page.Detail)detailTitle else if(page==Page.Expense)"${expenseKindLabel(expenseKind)} ${if(editor!=null)"수정" else "추가"}" else if(page==Page.Fuel && editor!=null)"주유 수정" else page.title,if(page==Page.Home)"함께한 여정의 기름값, 타고가 나눠드려요." else if(page==Page.Trip)"아반떼 CN8 · 함께 타는 ${people}명" else if(page==Page.Detail)previewTrip(detailTitle).subtitle else pageSubtitle(page,tripName),sample==SampleState.Driving)
 }
 screenState.SaveableStateProvider(page.name + if(page==Page.Detail)detailTitle else if(page in listOf(Page.Fuel,Page.Expense))editor?.title.orEmpty() else "") {
 PublishingScreen(page,sample,go,{title,action->confirmation=title
confirmAction=action},dark,{dark=it},tripName,people,{name,count->tripName=name;people=count;detailTitle=name;go(Page.Departure)},revealTrip,expenseKind,{expenseKind=it},{detailTitle=it},detailTitle,editor,{cost->
 costReturn=page;history.add(page);editor=cost;expenseKind=when(cost.kind){"주차"->"주차비";"톨비"->"톨비";else->"기타"};page=if(cost.kind=="주유")Page.Fuel else Page.Expense
 },costReturn,{sample=SampleState.Default})
 }
 Spacer(Modifier.height(12.dp))
 }
 }
 }
 }
 if(!wide)NavigationBar{listOf(Page.Home,Page.Settlement,Page.History,Page.Settings).forEach{p->NavigationBarItem(selected=page==p,onClick={go(p)},icon={Icon(painterResource(navIcon(p)),null)},label={Text(p.title)})}}
 Surface(tonalElevation=2.dp){FlowRow(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
 TextButton({picker=true}){Text("퍼블리싱 · 화면 선택",fontSize=12.sp)}
 TextButton({toolsExpanded=!toolsExpanded}){Text(if(toolsExpanded)"검수 옵션 접기" else "검수 옵션 펼치기",fontSize=12.sp)}
 if(toolsExpanded)SampleState.entries.forEach{s->FilterChip(sample==s,{sample=s},label={Text(s.label,fontSize=11.sp)})}
 }}
 }
 if(departing)DepartureScreen(tripName,onReveal={revealTrip=true},onFinished={departing=false})
 if(picker)AlertDialog(onDismissRequest={picker=false},title={Text("화면 미리보기")},text={Column(Modifier.verticalScroll(rememberScrollState())){Page.entries.forEach{p->TextButton({go(p)
picker=false}){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(painterResource(navIcon(p)),null)
Text(p.title)}}}}},confirmButton={TextButton({picker=false}){Text("닫기")}})
 if(confirmation=="이번 여행을 마칠까요?")EndTripDialog(tripName,{confirmation=null},{confirmation=null;confirmAction()})
 else if(confirmation!=null)AlertDialog(onDismissRequest={confirmation=null},title={Text(confirmation!!)},text={Text(if(confirmation=="이번 여행을 마칠까요?") "기록한 구간을 모아 함께 나눌 기름값을 확인해요.\n\n총 이동 거리 30km · 기록된 구간 3개\n\n여행을 마친 뒤 기록을 확인하고 정산할 수 있어요." else if(confirmation=="정산을 확정할까요?") "포함한 비용 내역과 인원을 확인해 주세요. 확정한 금액은 여행 내역에서 다시 볼 수 있어요." else "이 항목과 연결된 내역을 삭제하는 화면 시안입니다. 실제 데이터는 변경하지 않아요.")},dismissButton={TextButton({confirmation=null}){Text(if(confirmation=="이번 여행을 마칠까요?") "계속 기록" else "취소")}},confirmButton={TextButton({confirmation=null
confirmAction()}){Text(if(confirmation=="이번 여행을 마칠까요?") "여행 완료" else if(confirmation!!.contains("삭제"))"삭제하기" else "확인")}})
 }}}
}
@Preview(name="Tablet · 1280 × 800",widthDp=1280,heightDp=800,showBackground=true)
@Composable private fun LandscapePreview(){PublishingApp()}
@Preview(name="Small tablet · 960 × 600",widthDp=960,heightDp=600,showBackground=true)
@Composable private fun SmallTabletPreview(){PublishingApp()}

private fun navIcon(page:Page)=when(page){Page.Home->R.drawable.ic_car
Page.Settlement->R.drawable.ic_receipt
Page.History->R.drawable.ic_history
else->R.drawable.ic_settings}

private fun selectedNav(page:Page)=when(page){Page.Settlement,Page.Detail->Page.Settlement;Page.History->Page.History;Page.Settings->Page.Settings;else->Page.Home}
private fun pageSubtitle(page:Page,name:String)=when(page){
 Page.Prepare->"함께할 사람을 확인하고 여행을 시작하세요."
 Page.Resume->"쉬어가는 동안에도 여행 기록은 그대로예요."
 Page.History->"함께 이동한 거리와 나눈 기름값을 모았어요."
 Page.Settings->"다음 여행도 나에게 맞게 준비해요."
 Page.Detail->"2026. 11. 8 · 30km · 아반떼 CN8"
 Page.CostKind->"어떤 비용을 기록할까요?"
 Page.Fuel->"이 여행에서 결제한 주유 금액을 기록해요."
 Page.Expense->"이번 여행에서 함께 쓴 비용을 기록해요."
 Page.Settlement->"함께 쓴 비용을 확인하고 가볍게 나눠요."
 else->name
}
