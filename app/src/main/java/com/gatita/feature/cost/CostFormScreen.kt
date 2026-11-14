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
    androidx.activity.compose.BackHandler(selected && !state.isEdit) { selected=false }
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
            // [목적] 비용 종류 선택/입력 영역 컨테이너
            // [이유] 종류 선택 단계에서 큰 카드 안에 다시 카드 4개가 들어가 '카드 안 카드'가 됨 → 선택 단계는 카드 없이, 입력 단계만 카드로 감쌈
            // [사이드 임팩트] 없음 (배치만 변경)
            // [수정 시 고려사항] body는 두 컨테이너가 공유하므로 ColumnScope 전제로 작성할 것
            else->{ val body:@Composable ColumnScope.()->Unit={
                // [목적] 어느 여행의 비용인지 알려주는 한 줄 (박스 없이 글만)
                // [이유] 색 박스로 감싸니 기록 중 배너·입력 카드와 겹쳐 안내 박스가 세 겹으로 쌓여 보였음
                // [사이드 임팩트] 없음 (표시만 변경)
                // [수정 시 고려사항] 여러 여행이 있을 때 대상 여행을 확인하는 유일한 단서이므로 지우지 말 것
                Text("${state.tripName.ifBlank{"이번 여행"}}${if(state.isEdit) "의 비용을 고쳐요" else "에 추가해요"}",color=MaterialTheme.colorScheme.primary,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
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
                                            Icon(androidx.compose.ui.res.painterResource(when(kind){CostKind.FUEL->com.gatita.R.drawable.ic_fuel;CostKind.PARKING->com.gatita.R.drawable.ic_parking;CostKind.TOLL->com.gatita.R.drawable.ic_toll;CostKind.OTHER->com.gatita.R.drawable.ic_receipt}),null,Modifier.size(28.dp),tint=MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(12.dp))
                                            Text(costLabel(kind),Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                                            ChevronRight()
                                        }
                                        Note(when(kind){CostKind.FUEL->"결제 금액과 1L당 가격";CostKind.PARKING->"결제 금액과 주차 장소";CostKind.TOLL->"결제 금액과 이용 구간";CostKind.OTHER->"결제 금액과 비용 내용"})
                                    }
                                }
                            }
                        }
                    }
                    // ponytail: 타일은 왼쪽에 모여 있는데 안내문만 화면 가운데라 어긋나 보여 왼쪽 선에 맞춤
                    Note("장소와 비용 내용은 선택 사항이에요.")
                } else {
                    // [목적] 비용 종류 변경 칩 (수정할 때만)
                    // [이유] 추가할 때는 '비용 종류로 돌아가기'와 역할이 겹치고 제목('주차비')과 표기('주차')도 달라 숨김. 수정 화면에는 종류 선택 단계가 없어 유지
                    // [사이드 임팩트] 없음
                    // [수정 시 고려사항] 주유는 입력 항목이 달라 칩으로 전환하지 않음
                    if(!fuel && state.isEdit) ChoiceChips(CostKind.entries.filter{it!=CostKind.FUEL},state.kind,{costLabel(it)},onKindChange,!state.isSaving)
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
                                Text("계산한 주유량",Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=16.sp)
                                Text(liters?.let{"약 $it L"}?:"금액과 단가를 입력해 주세요",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)
                            }
                        }
                        Surface(onClick={details=!details},enabled=!state.isSaving,shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                            Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                        Text("주유 상세 정보",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                                        Surface(color=MaterialTheme.colorScheme.background,shape=RoundedCornerShape(6.dp)) { Text("선택",Modifier.padding(horizontal=8.dp,vertical=4.dp),fontSize=15.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                    Note("주유소와 날짜를 함께 남겨요")
                                }
                                Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(10.dp)) {
                                    Box(Modifier.size(36.dp),contentAlignment=Alignment.Center) { Icon(androidx.compose.ui.res.painterResource(com.gatita.R.drawable.ic_chevron_down),null,Modifier.size(22.dp).graphicsLayer{rotationZ=detailRotation},tint=MaterialTheme.colorScheme.primary) }
                                }
                            }
                        }
                    }
                    AnimatedVisibility(visible=!fuel||details,
                        enter=fadeIn(tween(120))+expandVertically(tween(160)),
                        exit=fadeOut(tween(80))+shrinkVertically(tween(140))) {
                        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        TextInput(when(state.kind){CostKind.FUEL->"주유소 · 선택";CostKind.PARKING->"주차 장소 · 선택";CostKind.TOLL->"톨게이트 / 이용 구간 · 선택";else->"비용 내용 · 선택"},
                            state.descriptionInput,onDescriptionChange,errorText=state.descriptionError,enabled=!state.isSaving,
                            placeholder=when(state.kind){CostKind.FUEL->"예: 강릉 나들목 주유소";CostKind.PARKING->"예: 경포 해변 주차장";CostKind.TOLL->"예: 서울 → 강릉";else->"예: 휴게소 간식"})
                        if(fuel){
                            Action("주유일  ${state.paidOnInput.ifBlank{LocalDate.now().toString()}}",primary=false,enabled=!state.isSaving){calendar=true}
                            state.paidOnError?.let{ErrorMessage(it)}
                        }
                    }
                    }
                    if(fuel)Note("주유 횟수와 주유 비용 건수가 맞아야 여행을 마칠 수 있어요.") // ponytail: 안내문이 다섯 곳이라 버튼이 밀려, 꼭 필요한 한 줄만 남김
                    state.saveError?.let{ErrorMessage(it)}
                    if(state.conflict) SecondaryButton("최신 내용 다시 불러오기",onRetryLoad)
                    ActionButton(if(state.isEdit)"수정 완료" else "추가하기",onSave,busy=state.isSaving,
                        enabled=state.amountInput.isNotBlank()&&(!fuel||state.unitPriceInput.isNotBlank()))
                }
            }
            if(selected) Panel(Modifier.fillMaxWidth(),content=body) else Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(14.dp),content=body) }
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
            suffix={Text(unit,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=16.sp)},
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),shape=RoundedCornerShape(12.dp),
            visualTransformation=ThousandsTransformation,
            colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=MaterialTheme.colorScheme.background,
                focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedBorderColor=MaterialTheme.colorScheme.outlineVariant))
        if(errorText!=null) Text(errorText,color=MaterialTheme.colorScheme.error,fontSize=15.sp) else Note(supporting)
    }
}

// [목적] 금액 입력 중 천 단위 쉼표 표시 (저장 값은 숫자만 유지)
// [이유] 입력 중 '50000'처럼 보여 자릿수를 잘못 읽기 쉬움. 예시 문구('50,000')와 표기도 맞춤
// [사이드 임팩트] 없음 (표시만 변환, onValueChange로 전달되는 값은 그대로)
// [수정 시 고려사항] 숫자 외 문자가 섞이면 변환하지 않음. 소수 입력을 허용하면 별도 처리 필요
private object ThousandsTransformation : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val raw = text.text
        if (raw.isEmpty() || !raw.all { it.isDigit() }) return androidx.compose.ui.text.input.TransformedText(text, androidx.compose.ui.text.input.OffsetMapping.Identity)
        val n = raw.length
        val out = StringBuilder()
        val toT = IntArray(n + 1)
        raw.forEachIndexed { i, c -> if (i > 0 && (n - i) % 3 == 0) out.append(','); toT[i] = out.length; out.append(c) }
        toT[n] = out.length
        return androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(out.toString()), object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int) = toT[offset.coerceIn(0, n)]
            override fun transformedToOriginal(offset: Int) = (0..n).last { toT[it] <= offset }
        })
    }
}

private fun costLabel(kind:CostKind)=when(kind) {
    CostKind.FUEL->"주유"
    CostKind.PARKING->"주차비"
    CostKind.TOLL->"톨비"
    CostKind.OTHER->"기타 비용"
}
