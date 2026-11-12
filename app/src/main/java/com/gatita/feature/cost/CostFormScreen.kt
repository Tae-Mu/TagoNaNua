package com.gatita.feature.cost

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import com.gatita.feature.publishing.DayPicker
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
    val detailRotation by animateFloatAsState(if(details)180f else 0f,tween(160),label="detailChevron")
    GatitaScaffold(if(!selected)"비용 추가" else "${costLabel(state.kind)} ${if(state.isEdit)"수정" else "추가"}",
        if(selected&&!state.isEdit)({selected=false}) else onBack, maxWidthDp=960,
        subtitle=if(!selected) "어떤 비용을 기록할까요?" else if(fuel) "이 여행에서 결제한 주유 금액을 기록해요." else "이번 여행에서 함께 쓴 비용을 기록해요.",
        showJourneyBanner=selected,
        backLabel=if(selected&&!state.isEdit) "비용 종류로 돌아가기" else "여행 기록으로 돌아가기") {
        when {
            state.isLoading->LoadingBlock("비용을 불러오는 중")
            state.loadError!=null->ErrorBlock(state.loadError,onRetryLoad)
            !state.editable->EmptyState("확정된 여행이에요","정산이 확정된 여행의 비용은 변경할 수 없어요.")
            state.notFound->EmptyState("비용을 찾을 수 없어요","목록으로 돌아가 다시 확인해 주세요.")
            else->Panel(Modifier.fillMaxWidth()) {
                Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                    Text(state.tripName.ifBlank{"이번 여행"},Modifier.fillMaxWidth().padding(16.dp),color=MaterialTheme.colorScheme.primary)
                }
                if(!selected) {
                    CostKind.entries.chunked(2).forEach { kinds ->
                        Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            kinds.forEach { kind ->
                                val interaction=remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                Surface(onClick={onKindChange(kind);selected=true},
                                    interactionSource=interaction,
                                    modifier=Modifier.weight(1f).then(pressMotion(interaction,true)), shape=RoundedCornerShape(16.dp),
                                    border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                                    Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                            Text(costLabel(kind),Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                                            Text("›",fontSize=28.sp)
                                        }
                                        Note(when(kind){CostKind.FUEL->"결제 금액과 1L당 가격";CostKind.PARKING->"결제 금액과 주차 장소";CostKind.TOLL->"결제 금액과 이용 구간";CostKind.OTHER->"결제 금액과 비용 내용"})
                                    }
                                }
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) { Note("장소와 비용 내용은 선택 사항이에요.") }
                } else {
                    if(!fuel) ChoiceChips(CostKind.entries.filter{it!=CostKind.FUEL},state.kind,{Formatters.costKind(it)},onKindChange,!state.isSaving)
                    Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                        CostAmountField("결제 금액", "원", if(fuel) "50,000" else "3,000",state.amountInput,onAmountChange,modifier=Modifier.weight(1f),
                            errorText=state.amountError,enabled=!state.isSaving,
                            supporting="영수증의 총 결제 금액을 입력해 주세요.")
                        if(fuel)CostAmountField("1L당 가격", "원 / L", "1,890",state.unitPriceInput,onUnitPriceChange,modifier=Modifier.weight(1f),
                            errorText=state.unitPriceError,enabled=!state.isSaving,supporting="영수증에 표시된 리터당 가격이에요.")
                    }
                    if(fuel) {
                        val liters=state.fuelLiters
                        Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text("계산한 주유량",Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp)
                                Text(liters?.let{"약 $it L"}?:"금액과 단가를 입력해 주세요",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)
                            }
                        }
                        Surface(onClick={details=!details},enabled=!state.isSaving,shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                            Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                        Text("주유 상세 정보",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                                        Surface(color=MaterialTheme.colorScheme.background,shape=RoundedCornerShape(6.dp)) { Text("선택",Modifier.padding(horizontal=8.dp,vertical=4.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                    Note("주유소와 날짜를 함께 남겨요")
                                }
                                Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(10.dp)) {
                                    Box(Modifier.size(36.dp),contentAlignment=Alignment.Center) { Text("⌄",modifier=Modifier.graphicsLayer{rotationZ=detailRotation},fontSize=24.sp,color=MaterialTheme.colorScheme.primary) }
                                }
                            }
                        }
                    }
                    AnimatedVisibility(visible=!fuel||details,
                        enter=fadeIn(tween(120))+expandVertically(tween(160)),
                        exit=fadeOut(tween(80))+shrinkVertically(tween(140))) {
                        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        TextInput(when(state.kind){CostKind.FUEL->"주유소 · 선택";CostKind.PARKING->"주차 장소 · 선택";CostKind.TOLL->"톨게이트 / 이용 구간 · 선택";else->"비용 내용 · 선택"},
                            state.descriptionInput,onDescriptionChange,errorText=state.descriptionError,enabled=!state.isSaving)
                        if(fuel){
                            Action("주유일  ${state.paidOnInput.ifBlank{LocalDate.now().toString()}}",primary=false,enabled=!state.isSaving){calendar=true}
                            state.paidOnError?.let{ErrorMessage(it)}
                        }
                    }
                    }
                    if(fuel)Note("주유 비용은 '주유 구간'의 단가를 정하는 데 써요. 주유한 뒤에 입력해도 되고, 주유 횟수와 비용 건수가 맞아야 여행을 마칠 수 있어요.")
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

/** HTML 금액 입력과 같은 외부 라벨·큰 숫자·단위. 검증과 값은 ViewModel에 위임한다. */
@Composable
private fun CostAmountField(label:String,unit:String,placeholder:String,value:String,onValueChange:(String)->Unit,
    modifier:Modifier=Modifier,errorText:String?=null,enabled:Boolean=true,supporting:String="") {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=15.sp)
        OutlinedTextField(value,onValueChange,modifier=Modifier.fillMaxWidth().semantics{contentDescription=label},
            enabled=enabled,singleLine=true,isError=errorText!=null,
            textStyle=MaterialTheme.typography.headlineMedium.copy(fontSize=32.sp),
            placeholder={Text(placeholder,fontSize=32.sp,color=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.5f))},
            suffix={Text(unit,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp)},
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),shape=RoundedCornerShape(12.dp),
            colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=MaterialTheme.colorScheme.background,
                focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedBorderColor=MaterialTheme.colorScheme.outlineVariant))
        if(errorText!=null) Text(errorText,color=MaterialTheme.colorScheme.error,fontSize=13.sp) else Note(supporting)
    }
}

private fun costLabel(kind:CostKind)=when(kind) {
    CostKind.FUEL->"주유"
    CostKind.PARKING->"주차비"
    CostKind.TOLL->"톨비"
    CostKind.OTHER->"기타 비용"
}
