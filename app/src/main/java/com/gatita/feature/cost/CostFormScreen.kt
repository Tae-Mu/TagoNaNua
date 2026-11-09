package com.gatita.feature.cost

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatita.core.ui.component.*
import com.gatita.domain.model.CostKind
import com.gatita.feature.common.Formatters
import com.gatita.feature.publishing.CostIcon
import com.gatita.feature.publishing.DayPicker
import com.gatita.feature.trip.iconKind
import java.time.LocalDate

@Composable
fun CostFormRoute(viewModel:CostFormViewModel,onBack:()->Unit,onSaved:()->Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel){viewModel.events.collect{if(it==CostFormEvent.Saved)onSaved()}}
    CostFormContent(state,onBack,viewModel::load,viewModel::onKindChange,viewModel::onAmountChange,
        viewModel::onDescriptionChange,viewModel::onUnitPriceChange,viewModel::onPaidOnChange,viewModel::save)
}
@Composable
fun CostFormContent(state:CostFormUiState,onBack:()->Unit,onRetryLoad:()->Unit,onKindChange:(CostKind)->Unit,
    onAmountChange:(String)->Unit,onDescriptionChange:(String)->Unit,onUnitPriceChange:(String)->Unit,
    onPaidOnChange:(String)->Unit,onSave:()->Unit) {
    var selected by rememberSaveable{mutableStateOf(state.isEdit)}
    var details by rememberSaveable{mutableStateOf(state.isEdit)}
    var calendar by rememberSaveable{mutableStateOf(false)}
    val fuel=state.kind==CostKind.FUEL
    GatitaScaffold(if(!selected)"비용 추가" else "${Formatters.costKind(state.kind)} ${if(state.isEdit)"수정" else "추가"}",
        if(selected&&!state.isEdit)({selected=false}) else onBack, maxWidthDp=960) {
        when {
            state.isLoading->LoadingBlock("비용을 불러오는 중")
            state.loadError!=null->ErrorBlock(state.loadError,onRetryLoad)
            !state.editable->EmptyState("확정된 여행이에요","정산이 확정된 여행의 비용은 변경할 수 없어요.")
            state.notFound->EmptyState("비용을 찾을 수 없어요","목록으로 돌아가 다시 확인해 주세요.")
            else->Panel(Modifier.fillMaxWidth().animateContentSize()) {
                Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                    Text(state.tripName.ifBlank{"이번 여행"},Modifier.fillMaxWidth().padding(16.dp),color=MaterialTheme.colorScheme.primary)
                }
                if(!selected) {
                    Text("어떤 비용을 기록할까요?",fontSize=24.sp)
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        CostKind.entries.forEach{kind->Panel(Modifier.weight(1f).clickable{onKindChange(kind);selected=true}){
                            CostIcon(iconKind(kind));Text(Formatters.costKind(kind));Note("직접 입력  ›")
                        }}
                    }
                } else {
                    ChoiceChips(CostKind.entries.toList(),state.kind,{Formatters.costKind(it)},onKindChange,!state.isSaving)
                    Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                        NumberField("결제 금액 (원)",state.amountInput,onAmountChange,modifier=Modifier.weight(1f),
                            errorText=state.amountError,enabled=!state.isSaving,keyboardType=KeyboardType.Number,
                            supporting="영수증의 총 결제 금액을 입력해 주세요.")
                        if(fuel)NumberField("1L당 가격 (원/L)",state.unitPriceInput,onUnitPriceChange,modifier=Modifier.weight(1f),
                            errorText=state.unitPriceError,enabled=!state.isSaving,supporting="영수증에 표시된 리터당 가격이에요.")
                    }
                    if(fuel) {
                        val liters=state.fuelLiters
                        NoticeMessage("계산한 주유량   ${liters?.let{"약 $it L"}?:"금액과 단가를 입력해 주세요"}")
                        TextButton({details=!details},enabled=!state.isSaving){Text("주유 상세 정보 · 선택  ${if(details)"⌃" else "⌄"}")}
                    }
                    if(!fuel||details) {
                        TextInput(when(state.kind){CostKind.FUEL->"주유소 · 선택";CostKind.PARKING->"주차 장소 · 선택";CostKind.TOLL->"톨게이트 / 이용 구간 · 선택";else->"비용 내용 · 선택"},
                            state.descriptionInput,onDescriptionChange,errorText=state.descriptionError,enabled=!state.isSaving)
                        if(fuel){
                            Action("주유일  ${state.paidOnInput.ifBlank{LocalDate.now().toString()}}",primary=false,enabled=!state.isSaving){calendar=true}
                            state.paidOnError?.let{ErrorMessage(it)}
                        }
                    }
                    state.saveError?.let{ErrorMessage(it)}
                    if(state.conflict) SecondaryButton("최신 내용 다시 불러오기",onRetryLoad)
                    ActionButton(if(state.isEdit)"수정 완료" else "추가하기",onSave,busy=state.isSaving,
                        enabled=state.amountInput.isNotBlank()&&(!fuel||state.unitPriceInput.isNotBlank()))
                    Note("저장한 비용은 이 여행에 연결돼요. 정산 포함 여부는 비용 내역에서 바꿀 수 있어요.")
                }
            }
        }
    }
    if(calendar)DayPicker(state.paidOnInput.ifBlank{LocalDate.now().toString()},{onPaidOnChange(it);calendar=false},{calendar=false})
}
