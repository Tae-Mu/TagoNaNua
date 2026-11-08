package com.gatita

import android.app.Application
import com.gatita.di.AppContainer

class GatitaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
