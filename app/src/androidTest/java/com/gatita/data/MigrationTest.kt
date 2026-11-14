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
    @Test
    fun version2CostsAndPolicySurviveSettingsAndSessionMigrations() {
        val name="migration-v2-current.db"
        helper.createDatabase(name,2).apply {
            execSQL("INSERT INTO trips (id,status,source,startOdometerKm,startedAtMillis,activeSlot,people,includeDriver) VALUES (1,'IN_PROGRESS','MANUAL','1000',1000,1,4,0)")
            execSQL("INSERT INTO costs (tripId,requestId,kind,amountWon,description,unitPrice,paidOn,included,createdAtMillis,updatedAtMillis) VALUES (1,'migration-cost','PARKING',3000,'시장',NULL,NULL,1,1000,1000)")
            close()
        }
        val db=helper.runMigrationsAndValidate(name,4,true,*Migrations.ALL)
        try {
            db.query("SELECT amountWon,description FROM costs").use { check(it.moveToFirst());check(it.getLong(0)==3000L);check(it.getString(1)=="시장") }
            db.query("SELECT people,includeDriver FROM trips WHERE id=1").use { check(it.moveToFirst());check(it.getInt(0)==4);check(it.getInt(1)==0) }
            db.execSQL("INSERT INTO app_settings VALUES (1,'민수',0,'야간')")
            db.execSQL("INSERT INTO trip_sessions VALUES (1,2000,0)")
        } finally { db.close();InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name) }
    }

    @Test
    fun version4TripsAndCostsKeepDataAndGetPaymentsDefaults() {
        val name="migration-v4-to-v5.db"
        helper.createDatabase(name,4).apply {
            execSQL("INSERT INTO trips (id,status,source,startOdometerKm,startedAtMillis,activeSlot,people,includeDriver) VALUES (1,'IN_PROGRESS','PLEOS','1000',1000,1,2,1)")
            execSQL("INSERT INTO costs (tripId,requestId,kind,amountWon,description,unitPrice,paidOn,included,createdAtMillis,updatedAtMillis) VALUES (1,'v4-cost','TOLL',2000,NULL,NULL,NULL,1,1000,1000)")
            close()
        }
        val db=helper.runMigrationsAndValidate(name,5,true,*Migrations.ALL)
        try {
            // 기존 여행은 결제액 방식으로 남고 직접 입력 단가는 없다.
            db.query("SELECT calculationVersion,manualFuelUnitPrice FROM trips WHERE id=1").use {
                check(it.moveToFirst());check(it.getString(0)=="payments-v1");check(it.isNull(1))
            }
            db.query("SELECT amountWon,undetected FROM costs WHERE tripId=1").use { check(it.moveToFirst());check(it.getLong(0)==2000L);check(it.getInt(1)==0) }
            db.execSQL("INSERT INTO fuel_snapshots (tripId,takenAtMillis,kind,odometerKm,source) VALUES (1,2000,'START','1000','PLEOS')")
            db.execSQL("INSERT INTO refuel_events (tripId,detectedAtMillis,percentBefore,percentAfter) VALUES (1,3000,20,80)")
            db.query("SELECT dismissed FROM refuel_events").use { check(it.moveToFirst());check(it.getInt(0)==0) }
        } finally { db.close();InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name) }
    }
}
