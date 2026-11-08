package com.gatita.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.gatita.data.local.AppDatabase
import com.gatita.data.local.migration.Migrations
import org.junit.Rule
import org.junit.Test

/**
 * app/schemas의 내보낸 스키마 JSON(KSP가 빌드 시 생성)으로 버전 1 DB를 만든 뒤, 등록된 마이그레이션을 거쳐
 * 현재 스키마로 열리는지 검증한다. 버전을 올릴 때 `runMigrationsAndValidate`로 기존 행 보존 테스트를 추가한다.
 * 파괴적 fallback을 쓰지 않으므로 마이그레이션이 누락되면 이 테스트가 실패한다.
 */
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun version1SchemaOpensWithRegisteredMigrationsAndKeepsData() {
        val name = "migration-test.db"
        helper.createDatabase(name, 1).apply {
            execSQL(
                "INSERT INTO trips (status, source, startOdometerKm, startedAtMillis, activeSlot) " +
                    "VALUES ('IN_PROGRESS', 'MANUAL', '1000', 1, 1)",
            )
            close()
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .addCallback(AppDatabase.immutabilityCallback)
            .build()
        try {
            val cursor = db.openHelper.readableDatabase.query("SELECT startOdometerKm FROM trips")
            cursor.use {
                check(it.moveToFirst() && it.getString(0) == "1000") { "existing trip was not preserved" }
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
