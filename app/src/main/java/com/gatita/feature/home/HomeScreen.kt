package com.gatita.feature.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatita.R
import com.gatita.core.ui.component.*
import com.gatita.domain.model.TripSummary
import com.gatita.feature.common.Formatters
import com.gatita.feature.publishing.DepartureScreen

@Composable
fun HomeRoute(viewModel:HomeViewModel,onOpenTrip:(Long)->Unit,onOpenSettlement:(Long)->Unit,onOpenHistory:()->Unit,onOpenSettlements:()->Unit=onOpenHistory, activeContent:(@Composable (Long)->Unit)?=null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var startedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var startedName by rememberSaveable { mutableStateOf("") }
    var awaitingDeparture by rememberSaveable { mutableStateOf(false) }
    var revealingTrip by remember { mutableStateOf(false) }
    // Keep the collector alive when Room emits the new active trip before OpenTrip.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event -> if(event is HomeEvent.OpenTrip) startedId=event.tripId }
    }
    LaunchedEffect(state.startError,state.nameError) {
        if(state.startError!=null || state.nameError!=null) awaitingDeparture=false
    }
    val activeId=state.activeTrip?.id
    Box(Modifier.fillMaxSize()) {
        if(activeId!=null && activeContent!=null && ((!awaitingDeparture && startedId==null) || revealingTrip)) activeContent(activeId)
        else HomeContent(state,viewModel::onNameChange,
            {awaitingDeparture=true;startedName=state.nameInput.ifBlank{"새로운 여행"};viewModel.startTrip()},viewModel::observe,
            onOpenTrip,onOpenSettlement,onOpenHistory,viewModel::onPeopleChange,viewModel::onIncludeDriverChange,onOpenSettlements)
        startedId?.let { id -> DepartureScreen(startedName,onReveal={revealingTrip=true},onFinished={
            startedId=null;awaitingDeparture=false;revealingTrip=false
            if(activeContent==null) onOpenTrip(id)
        }) }
    }
}

@Composable
fun HomeContent(state:HomeUiState,onNameChange:(String)->Unit,
    onStartTrip:()->Unit,onRetryLoad:()->Unit,onOpenTrip:(Long)->Unit,onOpenSettlement:(Long)->Unit,onOpenHistory:()->Unit,
    onPeopleChange:(Int)->Unit={},onIncludeDriverChange:(Boolean)->Unit={},onOpenSettlements:()->Unit=onOpenHistory) {
    var prepare by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(prepare) { prepare=false }
    GatitaScaffold(if(prepare)"출발 준비" else "오늘은 어디로 떠나세요?",if(prepare)({prepare=false}) else null, subtitle=if(prepare) "인원을 확인하고, 함께할 여행을 시작해요." else "함께한 여정의 기름값, 타고가 나눠드려요.", statusLabel="출발 시 연결 확인") {
        when {
            state.isLoading -> LoadingBlock("저장된 여행을 불러오는 중")
            state.loadError!=null -> ErrorBlock(state.loadError,onRetryLoad)
            state.activeTrip!=null -> LoadingBlock("여행 기록을 불러오는 중")
            prepare -> {
                JourneySteps()
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                    Panel(Modifier.weight(1.15f)) {
                        Text("이번 여행을 알려주세요",fontSize=26.sp,fontWeight=FontWeight.Bold)
                        // ponytail: 32자 제한을 입력 단계에서 막아 제출 후 오류를 없앰 (검증은 ViewModel에 그대로 남음)
                        TextInput("여행 이름 · 선택",state.nameInput,{onNameChange(it.take(com.gatita.domain.model.CostLimits.TRIP_NAME_MAX_LENGTH))},errorText=state.nameError,enabled=!state.isStarting,placeholder="예: 강릉 주말 여행")
                        Note("비워두면 ‘새로운 여행’으로 표시해요.")
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                        StepperRow("함께 타는 인원",state.people,1..8,onPeopleChange,enabled=!state.isStarting)
                        // [목적] 운전자 정산 포함 스위치 (혼자 탈 때는 이유와 함께 비활성)
                        // [이유] 인원 1명이면 스위치가 설명 없이 꺼져 고장처럼 보여, 설정 화면과 같은 문구로 이유를 붙임
                        // [사이드 임팩트] 없음 (표시만 추가)
                        // [수정 시 고려사항] 문구는 설정 화면 '정산 기본값' 안내와 같게 유지
                        SwitchRow("본인도 정산에 포함",state.includeDriver,onIncludeDriverChange,enabled=!state.isStarting&&state.people>1,supporting=if(state.people==1) "혼자 타는 여행은 본인이 부담해요." else null)
                        Note("이동 거리와 주행 정보는 차량에서 자동으로 받아요.")
                        state.startError?.let{ErrorMessage(it)}
                        ActionButton("여행 기록 시작",onStartTrip,busy=state.isStarting)
                    }
                    // [목적] 출발 준비 화면 오른쪽: 카드 없이 마스코트와 한 줄 안내만 가운데에 둠
                    // [이유] 조작할 것이 없는 안내를 입력 카드와 같은 테두리 박스에 담아, 박스 두 개가 같은 무게로 보였음
                    // [사이드 임팩트] 안내 문구 두 줄을 한 문단으로 합침 (내용은 그대로)
                    // [수정 시 고려사항] 마스코트 모션(tago_prep_ready)은 진입 시 1회만 재생. 왼쪽 입력 카드보다 눈에 띄지 않게 유지
                    Box(Modifier.weight(1f)) {
                        EmptyBlock("함께 떠날 준비가 됐나요?","비용은 여행 중에도 추가할 수 있어요. 주유·주차·톨비를 한곳에 모으고, 도착 후 함께 나눠요.") {
                            com.gatita.feature.launch.JourneyMascot(Modifier.size(260.dp),com.gatita.R.drawable.tago_prep_ready)
                        }
                    }
                }
            }
            else -> Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                        Text("여행은 함께.\n정산은 가볍게.",fontSize=46.sp,lineHeight=55.sp,fontWeight=FontWeight.Bold)
                        Text("출발부터 도착까지 기록하고,\n함께 탄 만큼 편하게 나눠요.",fontSize=18.sp,lineHeight=28.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Action("새 여행 준비",modifier=Modifier.widthIn(min=230.dp),arrow=true){prepare=true}
                        Note("인원만 확인하면 출발 준비 끝")
                    }
                    Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        // [목적] 홈 대표 이미지
                        // [이유] 사용자 차와 다른 예시 차량 사진이 주인공이 되고 '디자인 예시예요' 해명이 필요해, 브랜드 마스코트로 교체
                        // [사이드 임팩트] 홈 진입 시 마스코트 애니메이션 1회 재생 (JourneyMascot의 손 흔들어 인사하는 모션, 모션 줄이기 설정 존중)
                        // [수정 시 고려사항] 차량 연결 안내는 상단 상태 배지가 맡으므로 여기에 반복하지 말 것. 차량 연동 후 실제 차량 정보가 생기면 그때 차량 이미지를 다시 고려. 미뤄둔 정산이 없으면 아래가 비므로 마스코트를 키워 첫 화면을 채움
                        com.gatita.feature.launch.JourneyMascot(Modifier.fillMaxWidth().height(if(state.pendingTrips.isEmpty()) 460.dp else 240.dp),com.gatita.R.drawable.tago_home_greet)
                    }
                }
            }
        }
        if(!prepare) {
            // [목적] 홈 하단 '이어서 할 일' (미뤄둔 정산이 있을 때만)
            // [이유] 0건일 때 빈 카드가 정산 탭과 같은 말을 반복해 영역째 숨김. 01/02/03 단계 줄은 히어로 문구·출발 준비 4단계와 중복이라 삭제
            // [사이드 임팩트] 정산이 없으면 홈에서 '전체 정산' 진입점이 사라짐 (내비게이션 레일 '정산'으로 대체)
            // [수정 시 고려사항] 진행 단계 안내는 출발 준비의 JourneySteps 하나로 유지할 것
            if(state.pendingTrips.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("이어서 할 일 · ${state.pendingTrips.size}",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold)
                    Action("전체 정산",primary=false,leadingIcon=R.drawable.ic_receipt,arrow=true,minHeight=56,textSize=16,horizontalPadding=16,iconGap=10){onOpenSettlements()}
                }
                state.pendingTrips.forEach{SummaryRow(it){onOpenSettlement(it.tripId)}}
            }
        }
    }
}

@Composable internal fun SummaryRow(trip:TripSummary,onOpen:()->Unit) {
    Panel(Modifier.fillMaxWidth().clickable(onClick=onOpen)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            // [목적] 정산 목록 날짜 블록 (월 위, 일 크게)
            // [이유] 여행 내역과 같은 '11월 / 11일' 형식으로 통일 ('11 / 11월'은 일과 월이 같을 때 모호)
            // [사이드 임팩트] 홈 '이어서 할 일'과 정산 탭 목록 모두 적용
            // [수정 시 고려사항] HistoryScreen 날짜 블록과 형식을 함께 유지할 것
            Column {Note("${trip.tripDate.monthValue}월");AmountLabel(trip.tripDate.dayOfMonth.toString(),"일",28)}
            Column(Modifier.weight(1f)){Text(trip.name,fontSize=20.sp,fontWeight=FontWeight.SemiBold);Note("${Formatters.startTime(trip.startedAtMillis)} · ${trip.people}명 · 비용 ${trip.costCount}건 · ${Formatters.tripStatus(trip.status)}")}
            AmountLabel(Formatters.won(trip.settlement?.totalWon?:trip.includedTotalWon).removeSuffix("원"),"원",24);ChevronRight()
        }
    }
}
@Composable fun SettlementListRoute(viewModel:HomeViewModel,onOpen:(Long)->Unit,onHistory:()->Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GatitaScaffold("정산") {
        when {
            state.isLoading->LoadingBlock("정산을 불러오는 중")
            state.loadError!=null->ErrorBlock(state.loadError!!,viewModel::observe)
            else->{
                // [목적] 미확정 정산 합계 요약과 빈 상태
                // [이유] 0건일 때 '0원' 큰 숫자 카드와 빈 상태 카드가 같은 정보를 중복 표시해, 합계 카드는 1건 이상일 때만 보여줌
                // [사이드 임팩트] 없음 (표시만 변경, pendingTotalWon 계산 그대로)
                // [수정 시 고려사항] 합계는 정산에 포함한 실제 결제액 합계라는 문구를 유지할 것 (AGENTS.md 정산 규칙)
                if(state.pendingTrips.isNotEmpty()) Panel(Modifier.fillMaxWidth()){Note("확인이 필요한 여행 · ${state.pendingTrips.size}건");AmountLabel(Formatters.won(state.pendingTotalWon).removeSuffix("원"),"원",40);Note("정산에 포함한 실제 결제액 합계")}
                if(state.pendingTrips.isEmpty())EmptyState("미뤄둔 정산이 없어요","여행을 마치면 여기에서 정산을 이어가요. 확정한 정산은 여행 내역에서 볼 수 있어요."){com.gatita.feature.launch.JourneyMascot(Modifier.size(210.dp),com.gatita.R.drawable.tago_empty_wait)}
                state.pendingTrips.forEach{SummaryRow(it){onOpen(it.tripId)}}
                if(state.settledTrips.isNotEmpty()) {
                    Text("확정한 정산 · ${state.settledTrips.size}",fontWeight=FontWeight.Bold)
                    state.settledTrips.forEach{SummaryRow(it){onOpen(it.tripId)}}
                }
                // [목적] 정산이 하나도 없으면 '전체 여행 내역' 버튼을 빈 화면 안내 아래 가운데에 둠
                // [이유] 가운데 정렬 빈 화면 아래에 버튼만 왼쪽에 붙어 따로 노는 것처럼 보였음
                // [사이드 임팩트] 없음 (위치만 변경)
                // [수정 시 고려사항] 목록이 있을 때는 목록과 같은 왼쪽 정렬을 유지할 것
                Row(Modifier.fillMaxWidth(),horizontalArrangement=if(state.pendingTrips.isEmpty()&&state.settledTrips.isEmpty())Arrangement.Center else Arrangement.Start){Action("전체 여행 내역",primary=false){onHistory()}}
            }
        }
    }
}

@Composable private fun JourneySteps() {
    Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) {
        listOf("출발 준비","여행 기록","금액 확인","정산 완료").forEachIndexed { index, title ->
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Surface(color=if(index==0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,shape=androidx.compose.foundation.shape.CircleShape) {
                    Text("0${index+1}",Modifier.padding(9.dp),fontSize=15.sp,color=if(index==0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Note(title)
            }
        }
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
}
