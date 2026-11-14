package com.gatita.core.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Pleos reports a large 160dpi app window. Scale layout AND hit targets together,
 * scoped to this app; never change the vehicle's global display/font settings. */
@Composable
fun VehicleViewport(content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val car = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_CAR
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val factor = if (car) (maxWidth.value / 1200f).coerceIn(1f, 1.5f) else 1f
        CompositionLocalProvider(LocalDensity provides Density(base.density * factor, base.fontScale), content = content)
    }
}

/** Dialog creates another Compose window and resets LocalDensity; retain the app scale. */
@Composable
fun VehicleDialog(
    onDismissRequest: () -> Unit,
    properties: androidx.compose.ui.window.DialogProperties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    scrimAlpha: Float = 0.40f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
        }
        CompositionLocalProvider(LocalDensity provides density) {
            Box(Modifier.fillMaxSize(), contentAlignment=androidx.compose.ui.Alignment.Center) {
                Box(Modifier.matchParentSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha=scrimAlpha))
                    .pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } })
                Box(Modifier.pointerInput(Unit) { detectTapGestures { } }) { content() }
            }
        }
    }
}


@Composable
fun VehicleAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable RowScope.() -> Unit,
    dismissButton: @Composable RowScope.() -> Unit,
) {
    VehicleDialog(onDismissRequest) {
        Surface(Modifier.padding(24.dp).widthIn(max=480.dp).fillMaxWidth(),
            shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(28.dp),
                verticalArrangement=Arrangement.spacedBy(24.dp)) {
                ProvideTextStyle(MaterialTheme.typography.headlineSmall, title)
                ProvideTextStyle(MaterialTheme.typography.bodyLarge, text)
                // [목적] 확인 대화상자의 버튼 줄
                // [이유] 되돌릴 수 없는 확정·삭제 버튼이 작은 글자 링크(TextButton)라 운전석에서 오조작 위험 → RowScope로 열어 호출부가 같은 폭의 큰 버튼(Action, weight 1f)을 쓰게 함
                // [사이드 임팩트] 모든 VehicleAlertDialog 호출부의 버튼 람다가 RowScope 수신자를 가짐
                // [수정 시 고려사항] 확정 쪽은 Action(primary), 취소는 Action(primary=false)로 맞출 것
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    dismissButton()
                    confirmButton()
                }
            }
        }
    }
}
