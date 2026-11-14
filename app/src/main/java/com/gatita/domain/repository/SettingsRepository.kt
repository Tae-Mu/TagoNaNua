package com.gatita.domain.repository

import kotlinx.coroutines.flow.Flow

data class AppSettings(val driverName: String = "", val includeDriver: Boolean = true, val appearance: String = "시스템")
interface SettingsRepository {
    fun observe(): Flow<AppSettings>
    suspend fun get(): AppSettings
    suspend fun save(settings: AppSettings)
}
