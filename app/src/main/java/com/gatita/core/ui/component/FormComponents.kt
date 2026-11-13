package com.gatita.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 일반 텍스트 입력. 오류는 필드 아래와 접근성 semantics 양쪽에 표시한다. */
@Composable
fun TextInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    supporting: String? = null,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    placeholder: String? = null,
) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,fontSize=15.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f)) } },
        isError = errorText != null,

        enabled = enabled,
        singleLine = true,
        colors=androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor=MaterialTheme.colorScheme.background,
            focusedContainerColor=MaterialTheme.colorScheme.surface,
            unfocusedBorderColor=MaterialTheme.colorScheme.outlineVariant),
        shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription=label; if (errorText != null) error(errorText) },
    )
        (errorText?:supporting)?.let { Text(it,fontSize=15.sp,color=if(errorText!=null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
    }

}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) { Text(text) }
}

/** 행 전체가 체크 영역인 체크박스. 최소 터치 높이 56dp. */
@Composable
fun CheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            // ponytail: bodySmall(12sp)은 글자 하한 미달이라 15sp로 고정
            if (supporting != null) Text(supporting, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** 정수 범위를 − / + 로 바꾸는 입력. 범위 밖으로는 움직이지 않는다. */
@Composable
fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    unit: String = "명",
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(
        shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
            contentPadding=PaddingValues(0.dp),
            onClick = { onValueChange(value - 1) },
            enabled = enabled && value > range.first,
            modifier = Modifier
                .size(56.dp) // ponytail: 차량 화면 터치 하한 56dp
                .semantics { contentDescription = "$label 줄이기" },
        ) { StepGlyph(plus=false) }
        Text("$value$unit", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(
        shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
            contentPadding=PaddingValues(0.dp),
            onClick = { onValueChange(value + 1) },
            enabled = enabled && value < range.last,
            modifier = Modifier
                .size(56.dp) // ponytail: 차량 화면 터치 하한 56dp
                .semantics { contentDescription = "$label 늘리기" },
        ) { StepGlyph(plus=true) }
    }
}

// [목적] 인원 증감 버튼의 −/+ 아이콘
// [이유] 유니코드 '−' '+' 글자는 폰트마다 굵기·위치가 달라 다른 선 아이콘(ChevronRight)과 어긋나 직접 그림
// [사이드 임팩트] 없음 (StepperRow 버튼 내부 표시만 변경, 접근성 설명은 버튼 semantics가 담당)
// [수정 시 고려사항] 선 굵기는 ChevronRight와 맞춰 유지
@Composable
private fun StepGlyph(plus: Boolean) {
    val color = androidx.compose.material3.LocalContentColor.current
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val w = 2.dp.toPx()
        drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .2f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width * .8f, size.height / 2), w, androidx.compose.ui.graphics.StrokeCap.Round)
        if (plus) drawLine(color, androidx.compose.ui.geometry.Offset(size.width / 2, size.height * .2f), androidx.compose.ui.geometry.Offset(size.width / 2, size.height * .8f), w, androidx.compose.ui.graphics.StrokeCap.Round)
    }
}
