package com.gatita

import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.gatita.feature.launch.LaunchScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gatita.navigation.AppNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as GatitaApplication).container
        setContent {
            var showLaunch by remember { mutableStateOf(savedInstanceState == null) }
            Box(Modifier.fillMaxSize()) {
                Box(if (showLaunch) Modifier.clearAndSetSemantics {} else Modifier) { AppNavHost(container) }
                AnimatedVisibility(visible=showLaunch, exit=fadeOut(tween(650))) {
                    Box(Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitPointerEventScope { while(true) awaitPointerEvent().changes.forEach { it.consume() } }
                    }) {
                        LaunchScreen { showLaunch=false }
                    }
                }
            }
        }
    }
}
