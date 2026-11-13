package com.gatita.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.gatita.core.ui.component.*

@Composable fun SettingsRoute(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    GatitaScaffold("설정",subtitle="다음 여행도 나에게 맞게 준비해요.") {
        if(state.loading) CircularProgressIndicator() else if(state.loadFailed) ErrorBlock(state.message.orEmpty(),viewModel::load) else {
            // [목적] 설정 목록 한 장(이름 · 운전자 포함 · 화면 모드). 스위치·화면 모드는 누르는 즉시 저장
            // [이유] 같은 모양 카드 4개(2×2)는 내용보다 틀이 앞섰고, '연결 차량'은 조작할 것이 없는 카드였음. 넓은 화면에서 줄이 너무 길어지지 않게 폭을 720dp로 제한
            // [사이드 임팩트] 없음 (표시만 변경, draft/저장 로직 그대로). '연결 차량' 안내는 아래 보조 영역으로 이동
            // [수정 시 고려사항] '이름 저장'은 이름이 바뀌었을 때만 보임. 검증 실패 시 message로 안내되므로 버튼이 남아 있어야 재시도 가능
            Panel(Modifier.widthIn(max=720.dp).fillMaxWidth(),padding=28,gap=18) {
                TextInput("정산에 표시할 이름",state.draft.driverName,{viewModel.edit(state.draft.copy(driverName=it))},enabled=!state.saving,placeholder="나 (운전자)")
                Note("비워두면 ‘나 (운전자)’로 표시해요. 확정된 정산의 이름은 바뀌지 않아요.")
                state.message?.let { Text(it,color=MaterialTheme.colorScheme.primary) }
                // save()는 draft 전체를 저장하므로 입력 중인 이름도 스위치를 누를 때 함께 저장됨
                androidx.compose.animation.AnimatedVisibility(state.draft.driverName!=state.saved.driverName) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) { Action(if(state.saving) "저장 중…" else "이름 저장",enabled=!state.saving){viewModel.save()} } }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                SwitchRow("나 (운전자) 포함",state.draft.includeDriver,{viewModel.edit(state.draft.copy(includeDriver=it));viewModel.save()},enabled=!state.saving,supporting="새 여행에 적용돼요. 혼자 타는 여행은 본인이 부담해요.")
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Text("화면 모드",style=MaterialTheme.typography.bodyLarge)
                ChoiceChips(listOf("시스템","주간","야간"),state.draft.appearance,{it},{viewModel.edit(state.draft.copy(appearance=it));viewModel.save()},enabled=!state.saving)
                Note("고르는 즉시 저장돼요.")
            }
            // [목적] 저장 위치·QR 안내와 '설정 초기화'를 카드 없이 화면 맨 아래 보조 영역으로 표시
            // [이유] 조작할 것이 없는 안내문까지 카드에 담아 박스가 5개로 늘고, 설정 카드와 같은 무게로 보였음
            // [사이드 임팩트] 없음 (표시만 변경, 초기화 동작 그대로)
            // [수정 시 고려사항] 안내 문구는 정산 규칙상 필요한 고지이므로 지우지 말 것
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Note("이동 거리는 차량에서 받고, 비용은 직접 입력해요. 차량 연결은 여행을 시작할 때 확인해요.")
                    Note("여행과 비용은 이 기기에 저장돼요. 앱 삭제 시 기록도 삭제돼요.")
                    Note("QR은 정산 요약 공유용이에요. 실제 송금과 입금 확인은 별도로 진행해 주세요.")
                }
                Action("설정 초기화",primary=false,textSize=16,enabled=!state.saving){confirmReset=true}
            }
        }
    }
    if(confirmReset) com.gatita.core.ui.component.VehicleAlertDialog(onDismissRequest={confirmReset=false},title={Text("설정을 초기화할까요?")},text={Text("이름·화면 모드·운전자 포함 기본값만 초기화해요. 여행과 정산 기록은 삭제하지 않아요.")},confirmButton={Action("초기화",modifier=Modifier.weight(1f)){confirmReset=false;viewModel.save(true)}},dismissButton={Action("취소",primary=false,modifier=Modifier.weight(1f)){confirmReset=false}})
}
