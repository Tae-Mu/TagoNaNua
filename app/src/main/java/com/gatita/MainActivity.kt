package com.gatita

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gatita.core.ui.theme.GatitaTheme
import com.gatita.navigation.AppNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as GatitaApplication).container
        setContent {
            GatitaTheme {
                AppNavHost(container)
            }
        }
    }
}
