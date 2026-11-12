package com.gatita.feature.launch

import android.animation.ValueAnimator
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gatita.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// [목적] 블렌더로 만든 마스코트 모션(animated WebP)을 한 번 재생. res로 화면별 모션을 고름
// [이유] 모든 화면이 '출발 회전' 한 가지만 재생해, 화면 상황(인사·준비·쉬기·축하·기다림)에 맞는 모션을 고를 수 있게 함
// [사이드 임팩트] 모션마다 0.4~0.7MB 리소스가 APK에 추가됨. 디코딩은 IO 스레드, 준비되면 부드럽게 나타남
// [수정 시 고려사항] 재생만 담당하고 화면 이동 시점은 건드리지 않음. 모션 줄이기 설정이면 첫 장면만 보여줌.
//   모션 원본은 blender/scripts/screen_actions.py로 다시 렌더할 수 있음. 반복 재생(repeatCount)은 주행 중 주의 분산이라 넣지 말 것
@Composable
fun JourneyMascot(modifier: Modifier = Modifier, res: Int = R.drawable.tago_departure_spin, onReady: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var drawable by remember { mutableStateOf<Drawable?>(null) }
    val ready by rememberUpdatedState(onReady)
    val reduced = remember { !ValueAnimator.areAnimatorsEnabled() }
    LaunchedEffect(res) {
        drawable = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, res))
            }.getOrNull()
        }
    }
    LaunchedEffect(drawable) { if(drawable != null) ready() }
    DisposableEffect(drawable, lifecycle) {
        val animation = drawable as? AnimatedImageDrawable
        animation?.repeatCount = 0
        val observer = LifecycleEventObserver { _, event ->
            if(event == Lifecycle.Event.ON_STOP) animation?.stop()
        }
        lifecycle.addObserver(observer)
        if(!reduced && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) animation?.start()
        onDispose { animation?.stop(); lifecycle.removeObserver(observer) }
    }
    val alpha by androidx.compose.animation.core.animateFloatAsState(if(drawable != null) 1f else 0f,androidx.compose.animation.core.tween(220),label="mascotIn")
    AndroidView(
        factory={ ImageView(it).apply {
            scaleType=ImageView.ScaleType.FIT_CENTER
            importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        } },
        update={ if(it.drawable !== drawable) it.setImageDrawable(drawable) },
        modifier=modifier.graphicsLayer { this.alpha=alpha },
    )
}
