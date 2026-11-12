@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gatita.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 모든 화면의 공통 틀. 내용이 길면 세로로 스크롤되고 키보드가 올라오면 입력란이 가려지지 않는다. */
@Composable
fun GatitaScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    maxWidthDp: Int = 1400,
    subtitle: String = "",
    statusLabel: String? = null,
    backLabel: String? = null,
    showJourneyBanner: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // [목적] 화면 내용 컨테이너 정렬
    // [이유] 가운데 정렬이면 maxWidthDp가 좁은 화면(비용 추가 960dp)만 시작선이 오른쪽으로 밀려 탭 전환 시 제목이 튐 → 왼쪽 정렬로 통일
    // [사이드 임팩트] maxWidthDp보다 넓은 창에서는 오른쪽에 여백이 남음
    // [수정 시 고려사항] 가운데 정렬로 되돌리면 비용 추가 화면 시작선이 다시 어긋남
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopStart) {
    Column(Modifier.widthIn(max=maxWidthDp.dp).fillMaxSize().imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal=28.dp, vertical=28.dp), verticalArrangement=Arrangement.spacedBy(18.dp)) {
        if(showJourneyBanner) LocalActiveJourney.current?.let { journey ->
            androidx.compose.material3.Surface(onClick=journey.onOpen,color=MaterialTheme.colorScheme.secondaryContainer,
                shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal=22.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(com.gatita.R.drawable.ic_route),null,tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {Text("여행을 기록하고 있어요",color=MaterialTheme.colorScheme.primary);Note(journey.name)}
                    Text("기록으로 돌아가기",color=MaterialTheme.colorScheme.primary);ChevronRight()
                }
            }
        }
        // [목적] 이전 화면으로 돌아가는 버튼
        // [이유] 기본 TextButton(14sp, '‹' 문자)은 차량 화면에서 작고 터치 영역이 좁아 56dp·18sp와 선 아이콘으로 키움
        // [사이드 임팩트] onBack을 넘기는 모든 화면 상단 높이가 약 16dp 늘어남
        // [수정 시 고려사항] 56dp 터치 하한 유지. 아이콘은 ChevronRight를 180도 회전해 재사용함
        if(onBack!=null) TextButton(onClick=onBack,modifier=Modifier.heightIn(min=56.dp),contentPadding=androidx.compose.foundation.layout.PaddingValues(start=4.dp,end=16.dp)) {
            ChevronRight(Modifier.graphicsLayer { rotationZ=180f })
            Spacer(Modifier.size(6.dp))
            Text(backLabel?:"돌아가기",fontSize=androidx.compose.ui.unit.TextUnit(18f,androidx.compose.ui.unit.TextUnitType.Sp),fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
        if(title.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(bottom=8.dp), verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(title, style=MaterialTheme.typography.headlineMedium.copy(fontSize=androidx.compose.ui.unit.TextUnit(30f,androidx.compose.ui.unit.TextUnitType.Sp)),
                    fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
                if(subtitle.isNotEmpty()) Text(subtitle, style=MaterialTheme.typography.bodyLarge, color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // [목적] 화면 우측 상단의 상태 표시(예: 차량 연결 상태)
            // [이유] 흰 바탕+테두리 모양이 '전체 정산' 같은 보조 버튼과 똑같아 눌러지는 버튼으로 오인됨 → 테두리 없는 tint 배지와 상태 점으로 변경
            // [사이드 임팩트] statusLabel을 쓰는 홈·출발 준비 화면의 배지 모양만 바뀜. 클릭 동작은 원래 없음
            // [수정 시 고려사항] 이 배지에 클릭을 붙이려면 버튼 컴포넌트(Action)로 바꿀 것
            if(statusLabel!=null) androidx.compose.material3.Surface(
                color=MaterialTheme.colorScheme.secondaryContainer,
                shape=androidx.compose.foundation.shape.RoundedCornerShape(999.dp)) {
                Row(Modifier.padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.onSurfaceVariant,androidx.compose.foundation.shape.CircleShape))
                    Text(statusLabel, color=MaterialTheme.colorScheme.onSecondaryContainer,fontSize=androidx.compose.ui.unit.TextUnit(16f,androidx.compose.ui.unit.TextUnitType.Sp))
                }
            }
        }
        content()
        Spacer(Modifier.height(16.dp))
    }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        shape=androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** 오류 문구. 스크린 리더가 나타나는 즉시 읽도록 live region으로 표시한다. */
@Composable
fun ErrorMessage(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            "오류: $text",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun NoticeMessage(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun LoadingBlock(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
        Spacer(Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ErrorMessage(message)
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("다시 불러오기")
        }
    }
}

@Composable
// [목적] 빈 상태 안내. 테두리 박스 없이 가운데 정렬로 그림(art)·제목·설명을 세로로 쌓음
// [이유] 빈 화면마다 테두리 카드에 넣으니 '안내 박스'가 반복돼 답답하고, 그림이 글과 붙어 어색했음
// [사이드 임팩트] EmptyBlock·EmptyState를 쓰는 모든 화면(내역·정산·비용·찾을 수 없음)이 박스 없는 모양으로 바뀜
// [수정 시 고려사항] core는 feature를 참조하지 않으므로 마스코트는 호출하는 화면에서 넘겨줄 것. 설명 폭(560dp)을 넘기면 가운데 정렬 글이 읽기 어려워짐
fun EmptyBlock(title: String, description: String, art: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = if (art == null) 40.dp else 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        art?.invoke()
        Text(title, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(description, Modifier.widthIn(max = 560.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp, lineHeight = 25.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** 데이터 출처 배지. 수동 입력·샘플·차량 값을 화면에서 구분한다. */
@Composable
fun SourceLabel(text: String) {
    Text(
        "출처: $text",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    supporting: String? = null,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = errorText != null,
        supportingText = {
            val text = errorText ?: supporting
            if (text != null) Text(text)
        },
        enabled = enabled,
        singleLine = true,
        colors=androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor=MaterialTheme.colorScheme.background,
            focusedContainerColor=MaterialTheme.colorScheme.surface,
            unfocusedBorderColor=MaterialTheme.colorScheme.outlineVariant),
        shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (errorText != null) error(errorText) },
    )
}

/** 진행 중에는 비활성화하고 문구로 상태를 알린다. 화면에서도 중복 탭을 막는다(ViewModel도 따로 막는다). */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    busyText: String = "처리 중…",
    enabled: Boolean = true,
) {
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Button(
        interactionSource = interaction,
        shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.then(pressMotion(interaction, enabled && !busy))
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(8.dp))
            Text(busyText)
        } else {
            Text(text, fontSize=androidx.compose.ui.unit.TextUnit(18f,androidx.compose.ui.unit.TextUnitType.Sp),fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
    }
}

@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    outlined: Boolean = true,
) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                border=androidx.compose.foundation.BorderStroke(1.dp,if(outlined)MaterialTheme.colorScheme.outlineVariant else Color.Transparent),
                shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors=androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor=MaterialTheme.colorScheme.primary,selectedLabelColor=MaterialTheme.colorScheme.onPrimary),
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled,
                label = { Text(label(option),fontSize=androidx.compose.ui.unit.TextUnit(16f,androidx.compose.ui.unit.TextUnitType.Sp)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
fun LabeledValue(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // [목적] 라벨-값 쌍의 라벨
        // [이유] labelMedium(12sp)이 차량 화면 글자 하한(15sp)보다 작아 올림 (스타일 이름으로 지정돼 일괄 상향에서 빠졌던 곳)
        // [사이드 임팩트] LabeledValue를 쓰는 모든 화면의 라벨이 커짐
        // [수정 시 고려사항] 15sp 미만으로 내리지 말 것
        Text(label, fontSize = androidx.compose.ui.unit.TextUnit(15f,androidx.compose.ui.unit.TextUnitType.Sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun VerticalGap(height: Int = 8) = Spacer(Modifier.height(height.dp))

/** Fade only: navigation must not move the page vertically. */
internal data class ActiveJourney(val name:String,val onOpen:()->Unit)
internal val LocalActiveJourney=androidx.compose.runtime.staticCompositionLocalOf<ActiveJourney?> { null }
