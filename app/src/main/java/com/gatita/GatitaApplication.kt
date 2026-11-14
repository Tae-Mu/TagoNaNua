package com.gatita

import android.app.Application
import com.gatita.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GatitaApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 앱이 켜져 있는 동안 진행 중 여행의 차량 값을 주기적으로 기록한다. 앱이 꺼져 있던 동안은 기록되지 않는다.
        appScope.launch { container.fuelRecordingLoop.run() }
    }
}
