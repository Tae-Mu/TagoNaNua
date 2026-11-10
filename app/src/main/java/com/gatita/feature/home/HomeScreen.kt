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
fun HomeRoute(viewModel:HomeViewModel,onOpenTrip:(Long)->Unit,onOpenSettlement:(Long)->Unit,onOpenHistory:()->Unit,onOpenSettlements:()->Unit=onOpenHistory) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var startedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var startedName by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(viewModel) {viewModel.events.collect {event-> if(event is HomeEvent.OpenTrip) startedId=event.tripId }}
    Box(Modifier.fillMaxSize()) {
        HomeContent(state,viewModel::onNameChange,viewModel::onStartOdometerChange,
            {startedName=state.nameInput.ifBlank{"새로운 여행"};viewModel.startTrip()},viewModel::observe,
            onOpenTrip,onOpenSettlement,onOpenHistory,viewModel::onPeopleChange,viewModel::onIncludeDriverChange,onOpenSettlements)
        startedId?.let { id-> DepartureScreen(startedName,onReveal={},onFinished={startedId=null;onOpenTrip(id)}) }
    }
}

@Composable
fun HomeContent(state:HomeUiState,onNameChange:(String)->Unit,onStartOdometerChange:(String)->Unit,
    onStartTrip:()->Unit,onRetryLoad:()->Unit,onOpenTrip:(Long)->Unit,onOpenSettlement:(Long)->Unit,onOpenHistory:()->Unit,
    onPeopleChange:(Int)->Unit={},onIncludeDriverChange:(Boolean)->Unit={},onOpenSettlements:()->Unit=onOpenHistory) {
    var prepare by rememberSaveable { mutableStateOf(false) }
    GatitaScaffold(if(prepare)"출발 준비" else "오늘은 어디로 떠나세요?",if(prepare)({prepare=false}) else null, subtitle=if(prepare) "인원을 확인하고, 함께할 여행을 시작해요." else "함께한 여정의 기름값, 타고가 나눠드려요.", statusLabel="수동 기록") {
        when {
            state.isLoading -> LoadingBlock("저장된 여행을 불러오는 중")
            state.loadError!=null -> ErrorBlock(state.loadError,onRetryLoad)
            state.activeTrip!=null -> {
                val trip=state.activeTrip
                Panel(Modifier.fillMaxWidth()) {
                    Note("이어갈 여행이 있어요 · ${trip.tripDate}")
                    Text(trip.name,fontSize=32.sp,fontWeight=FontWeight.Bold)
                    Note("저장된 여행과 비용을 그대로 이어서 확인할 수 있어요.")
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Action("여행 이어가기",arrow=true){onOpenTrip(trip.id)}
                        Action("여행 내역",primary=false){onOpenHistory()}
                    }
                }
            }
            prepare -> {
                JourneySteps()
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                    Panel(Modifier.weight(1.15f)) {
                        Note("YOUR NEXT JOURNEY")
                        Text("이번 여행을 알려주세요",fontSize=26.sp,fontWeight=FontWeight.Bold)
                        TextInput("여행 이름 · 선택",state.nameInput,onNameChange,errorText=state.nameError,enabled=!state.isStarting)
                        Note("비워두면 ‘새로운 여행’으로 표시해요.")
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                        StepperRow("함께 타는 인원",state.people,1..8,onPeopleChange,enabled=!state.isStarting)
                        SwitchRow("본인도 정산에 포함",state.includeDriver,onIncludeDriverChange,enabled=!state.isStarting&&state.people>1)
                        NumberField("출발 계기판 주행거리 (km)",state.startOdometerInput,onStartOdometerChange,
                            errorText=state.startOdometerError,supporting="계기판의 누적 주행거리를 입력해 주세요.",enabled=!state.isStarting)
                        state.startError?.let{ErrorMessage(it)}
                        ActionButton("여행 기록 시작",onStartTrip,busy=state.isStarting)
                    }
                    Panel(Modifier.weight(1f)) {
                        Image(painterResource(R.drawable.vehicle),"차량 예시 이미지",Modifier.fillMaxWidth().height(190.dp))
                        Text("함께 떠날 준비가 됐나요?",fontSize=22.sp,fontWeight=FontWeight.SemiBold)
                        Note("차량 연결 전 · 수동 기록")
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                        Text("비용은 여행 중에도 추가할 수 있어요",fontWeight=FontWeight.SemiBold)
                        Note("주유·주차·톨비를 한곳에 모으고, 도착 후 함께 나눠요.")
                    }
                }
            }
            else -> Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                        Note("TAGO · 타고, 나눠")
                        Text("여행은 함께.\n정산은 가볍게.",fontSize=38.sp,lineHeight=46.sp,fontWeight=FontWeight.Bold)
                        Note("출발부터 도착까지 기록하고,\n함께 탄 만큼 편하게 나눠요.")
                        Action("새 여행 준비",modifier=Modifier.widthIn(min=230.dp),arrow=true){prepare=true}
                        Note("인원만 확인하면 출발 준비 끝")
                    }
                    Image(painterResource(R.drawable.vehicle),"차량 이미지 · 실제 연결 차량 아님",Modifier.weight(1f).height(220.dp))
                }
            }
        }
        if(!prepare) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("이어서 할 일 · ${state.pendingTrips.size}",Modifier.weight(1f),fontWeight=FontWeight.Bold)
                Action("전체 정산",primary=false,leadingIcon=R.drawable.ic_receipt,arrow=true){onOpenSettlements()}
            }
            if(state.pendingTrips.isEmpty()&&!state.isLoading&&state.loadError==null) EmptyState("미뤄둔 정산이 없어요","여행을 마치면 이곳에서 정산을 이어갈 수 있어요.")
            state.pendingTrips.forEach{SummaryRow(it){onOpenSettlement(it.tripId)}}
            Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                listOf("출발 전에 시작" to "운전자를 포함한 인원을 확인해요.","여행을 차곡차곡" to "구간과 비용을 같은 여행에 모아요.","도착하면 정산" to "금액을 확정하고 요약을 공유해요.").forEachIndexed { index, step ->
                    Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Note("0${index+1}")
                        Column { Text(step.first,fontSize=14.sp,fontWeight=FontWeight.SemiBold);Note(step.second) }
                    }
                }
            }
        }
    }
}

@Composable internal fun SummaryRow(trip:TripSummary,onOpen:()->Unit) {
    Panel(Modifier.fillMaxWidth().clickable(onClick=onOpen)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            Column {Text(trip.tripDate.dayOfMonth.toString(),fontSize=28.sp);Note("${trip.tripDate.monthValue}월")}
            Column(Modifier.weight(1f)){Text(trip.name,fontSize=20.sp,fontWeight=FontWeight.SemiBold);Note("${trip.people}명 · 비용 ${trip.costCount}건 · ${Formatters.tripStatus(trip.status)}")}
            AmountLabel(Formatters.won(trip.settlement?.totalWon?:trip.includedTotalWon).removeSuffix("원"),"원",24);Text("›")
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
                Panel(Modifier.fillMaxWidth()){Note("확인이 필요한 여행 · ${state.pendingTrips.size}건");Text(Formatters.won(state.pendingTotalWon),fontSize=40.sp);Note("정산에 포함한 실제 결제액 합계")}
                if(state.pendingTrips.isEmpty())EmptyState("미뤄둔 정산이 없어요","확정한 정산은 여행 내역에서 확인하세요.")
                state.pendingTrips.forEach{SummaryRow(it){onOpen(it.tripId)}}
                if(state.settledTrips.isNotEmpty()) {
                    Text("확정한 정산 · ${state.settledTrips.size}",fontWeight=FontWeight.Bold)
                    state.settledTrips.forEach{SummaryRow(it){onOpen(it.tripId)}}
                }
                Action("전체 여행 내역",primary=false){onHistory()}
            }
        }
    }
}

@Composable private fun JourneySteps() {
    Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) {
        listOf("출발 준비","여행 기록","금액 확인","정산 완료").forEachIndexed { index, title ->
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Surface(color=if(index==0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,shape=androidx.compose.foundation.shape.CircleShape) {
                    Text("0${index+1}",Modifier.padding(9.dp),fontSize=12.sp,color=if(index==0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Note(title)
            }
        }
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
}
