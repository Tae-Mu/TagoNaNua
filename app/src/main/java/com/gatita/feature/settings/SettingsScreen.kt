package com.gatita.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.gatita.core.ui.component.*

@Composable fun SettingsRoute(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    GatitaScaffold("설정") {
        if(state.loading) CircularProgressIndicator() else if(state.loadFailed) ErrorBlock(state.message.orEmpty(),viewModel::load) else {
            Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
                Row(Modifier.fillMaxWidth().padding(24.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                    Brand(false)
                    Column { Text(state.draft.driverName.ifBlank { "타고와 함께하는 운전자" },style=MaterialTheme.typography.titleLarge);Note("편한 여행을 위한 나의 기본 설정") }
                }
            }
            Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    Note("PROFILE")
                    Text("내 정보",style=MaterialTheme.typography.titleLarge)
                    TextInput("정산에 표시할 이름",state.draft.driverName,{viewModel.edit(state.draft.copy(driverName=it))},enabled=!state.saving)
                    Note("비워두면 ‘나 (운전자)’로 표시해요. 확정된 정산의 이름은 바뀌지 않아요.")
                }
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    Note("SETTLEMENT")
                    Text("정산 기본값",style=MaterialTheme.typography.titleLarge)
                    SwitchRow("나 (운전자) 포함",state.draft.includeDriver,{viewModel.edit(state.draft.copy(includeDriver=it))},enabled=!state.saving)
                    Note("새 여행에 적용돼요. 혼자 타는 여행은 본인이 부담해요.")
                }
            }
            Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    Note("APPEARANCE")
                    Text("화면 모드",style=MaterialTheme.typography.titleLarge)
                    ChoiceChips(listOf("시스템","주간","야간"),state.draft.appearance,{it},{viewModel.edit(state.draft.copy(appearance=it))},enabled=!state.saving)
                    Note("저장한 설정은 앱을 다시 열어도 유지돼요.")
                }
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    Note("VEHICLE")
                    Text("연결 차량",style=MaterialTheme.typography.titleLarge)
                    Text("차량 연결 전")
                    Note("거리와 비용을 직접 입력해요.")
                }
            }
            state.message?.let { Text(it,color=MaterialTheme.colorScheme.primary) }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) { Action(if(state.saving) "저장 중…" else "설정 저장",enabled=!state.saving){viewModel.save()} }
            Panel(Modifier.fillMaxWidth()) {
                Text("기록과 연결",style=MaterialTheme.typography.titleLarge)
                Note("여행과 비용은 이 기기에 저장돼요. 앱 삭제 시 기록도 삭제돼요.")
                Note("차량 연결: 미연결 · 거리와 비용을 직접 입력해요.")
                Note("QR은 정산 요약 공유용이에요. 실제 송금과 입금 확인은 별도로 진행해 주세요.")
                TextButton({confirmReset=true},enabled=!state.saving) {Text("설정 초기화")}
            }
        }
    }
    if(confirmReset) AlertDialog(onDismissRequest={confirmReset=false},title={Text("설정을 초기화할까요?")},text={Text("이름·화면 모드·운전자 포함 기본값만 초기화해요. 여행과 정산 기록은 삭제하지 않아요.")},confirmButton={TextButton({confirmReset=false;viewModel.save(true)}){Text("초기화")}},dismissButton={TextButton({confirmReset=false}){Text("취소")}})
}
