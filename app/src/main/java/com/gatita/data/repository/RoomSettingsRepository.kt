package com.gatita.data.repository

import com.gatita.data.local.dao.SettingsDao
import com.gatita.data.local.entity.SettingsEntity
import com.gatita.domain.repository.*
import kotlinx.coroutines.flow.map

class RoomSettingsRepository(private val dao: SettingsDao): SettingsRepository {
    private fun SettingsEntity?.domain() = this?.let { AppSettings(it.driverName, it.includeDriver, it.appearance) } ?: AppSettings()
    override fun observe() = dao.observe().map { it.domain() }
    override suspend fun get() = dao.get().domain()
    override suspend fun save(settings: AppSettings) = dao.save(SettingsEntity(driverName=settings.driverName, includeDriver=settings.includeDriver, appearance=settings.appearance))
}
