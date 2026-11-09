@file:OptIn(ExperimentalMaterial3Api::class)

package com.gatita.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
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
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) {
    Column(Modifier.widthIn(max=maxWidthDp.dp).fillMaxSize().imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal=32.dp, vertical=24.dp), verticalArrangement=Arrangement.spacedBy(18.dp)) {
        if(onBack!=null) TextButton(onClick=onBack) { Text("‹ 돌아가기") }
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
            Text(title, modifier=Modifier.weight(1f), style=MaterialTheme.typography.headlineMedium,
                fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
            androidx.compose.material3.Surface(color=MaterialTheme.colorScheme.secondaryContainer,
                shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                Text("수동 기록", Modifier.padding(horizontal=14.dp,vertical=10.dp), color=MaterialTheme.colorScheme.primary)
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
fun EmptyBlock(title: String, description: String) {
    InfoCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium)
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
    Button(
        shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(8.dp))
            Text(busyText)
        } else {
            Text(text)
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
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled,
                label = { Text(label(option)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
fun LabeledValue(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun VerticalGap(height: Int = 8) = Spacer(Modifier.height(height.dp))
