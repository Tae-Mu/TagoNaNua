package com.gatita.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Compact press response; faster press and soft release. Changes drawing only, never the hit area/layout. */
@Composable
internal fun pressMotion(source: MutableInteractionSource, enabled: Boolean): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if(enabled && pressed) .975f else 1f,tween(if(pressed) 70 else 110),label="buttonPress")
    return Modifier.graphicsLayer { scaleX=scale;scaleY=scale }
}
