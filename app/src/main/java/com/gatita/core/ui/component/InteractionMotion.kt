package com.gatita.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

// [목적] 버튼을 누르면 살짝 들어갔다가, 떼면 가볍게 튕기며 돌아오는 반응
// [이유] 0.975배로 뚝 줄었다 뚝 돌아와 반응이 딱딱하고 잘 보이지 않아, 눌림을 조금 키우고 돌아올 때 스프링을 줌
// [사이드 임팩트] Action·ActionButton을 쓰는 모든 버튼에 적용. 그리기만 바꾸고 터치 영역·레이아웃은 그대로
// [수정 시 고려사항] 눌림은 0.95 밑으로 내리지 말 것(글자가 흔들려 보임). 스프링 감쇠를 낮추면 차량 화면에서 산만해짐
@Composable
internal fun pressMotion(source: MutableInteractionSource, enabled: Boolean): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if(enabled && pressed) .96f else 1f,if(pressed) tween(80) else androidx.compose.animation.core.spring(dampingRatio=.55f,stiffness=500f),label="buttonPress")
    return Modifier.graphicsLayer { scaleX=scale;scaleY=scale }
}
