package com.gatita.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class SettingsEntity(@PrimaryKey val id: Int = 1, val driverName: String = "", val includeDriver: Boolean = true, val appearance: String = "시스템")
