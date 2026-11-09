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

@Composable internal fun SettlementScreen(state:SampleState,go:(Page)->Unit,confirm:(String,()->Unit)->Unit,onSelect:(String)->Unit){Panel(Modifier.fillMaxWidth()){Text("함께 탄 만큼, 가볍게 나눠요.",fontSize=26.sp,fontWeight=FontWeight.Bold)
Metric("미확정 정산 합계",if(state==SampleState.Empty)"0 원" else "50,000 원")}
if(state==SampleState.Empty)EmptyState("미뤄둔 정산이 없어요","새 여행을 마치면 이곳에서 정산할 수 있어요.")else{Text("확인이 필요한 여행 · 1",fontSize=18.sp)
TripRow("주말 나들이","50,000",go,onSelect=onSelect,onDelete={confirm("주말 나들이 여행을 삭제할까요?\n50,000원 · 여행 기록과 비용 내역") {}})
Text("확정한 정산 · 1",fontSize=18.sp)
TripRow("함께한 주말 여행","60,000",go,true,"2","82km · 95분 · 3명",onSelect=onSelect,onDelete={confirm("함께한 주말 여행을 삭제할까요?\n60,000원 · 확정한 정산 포함") {}})}}
