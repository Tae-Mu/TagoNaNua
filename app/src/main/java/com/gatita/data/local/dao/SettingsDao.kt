package com.gatita.data.local.dao

import androidx.room.*
import com.gatita.data.local.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1") fun observe(): Flow<SettingsEntity?>
    @Query("SELECT * FROM app_settings WHERE id = 1") suspend fun get(): SettingsEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(value: SettingsEntity)
}
