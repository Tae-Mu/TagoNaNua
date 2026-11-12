package com.gatita.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gatita.data.local.dao.CostDao
import com.gatita.data.local.dao.FuelTrackingDao
import com.gatita.data.local.dao.PaymentSettlementDao
import com.gatita.data.local.dao.SettlementDao
import com.gatita.data.local.dao.TripDao
import com.gatita.data.local.entity.CostEntity
import com.gatita.data.local.entity.FuelPriceEntity
import com.gatita.data.local.entity.FuelSnapshotEntity
import com.gatita.data.local.entity.PaymentSettlementCostEntity
import com.gatita.data.local.entity.PaymentSettlementEntity
import com.gatita.data.local.entity.PaymentSettlementFuelIntervalEntity
import com.gatita.data.local.entity.PaymentSettlementShareEntity
import com.gatita.data.local.entity.RefuelEventEntity
import com.gatita.data.local.entity.SegmentEntity
import com.gatita.data.local.entity.SettlementEntity
import com.gatita.data.local.entity.SettlementItemEntity
import com.gatita.data.local.entity.SettlementShareEntity
import com.gatita.data.local.entity.TripEntity
import com.gatita.data.local.migration.ImmutabilityTriggers
import com.gatita.data.local.migration.Migrations

/**
 * 버전 이력: 1 = 거리·연비 기반 추정 정산, 2 = 결제액 방식(costs, payment_settlements 등) 추가.
 * 5 = 구간별 유류비 방식(fuel-segments-v2): 차량 측정·주유 이벤트·구간 결과 테이블과 여행·비용 컬럼 추가.
 * v1의 settlements·fuel_prices·settlement_items·settlement_shares는 옛 정산(legacy) 이력으로 그대로 읽는다.
 */
@Database(
    entities = [
        TripEntity::class,
        com.gatita.data.local.entity.SettingsEntity::class,
        com.gatita.data.local.entity.TripSessionEntity::class,
        SegmentEntity::class,
        FuelPriceEntity::class,
        SettlementEntity::class,
        SettlementItemEntity::class,
        SettlementShareEntity::class,
        CostEntity::class,
        PaymentSettlementEntity::class,
        PaymentSettlementCostEntity::class,
        PaymentSettlementShareEntity::class,
        FuelSnapshotEntity::class,
        RefuelEventEntity::class,
        PaymentSettlementFuelIntervalEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): com.gatita.data.local.dao.SettingsDao
    abstract fun tripSessionDao(): com.gatita.data.local.dao.TripSessionDao
    abstract fun tripDao(): TripDao
    abstract fun settlementDao(): SettlementDao
    abstract fun costDao(): CostDao
    abstract fun paymentSettlementDao(): PaymentSettlementDao
    abstract fun fuelTrackingDao(): FuelTrackingDao

    companion object {
        const val NAME = "gatita.db"

        /** DB를 열 때마다 정산 불변·입력 무결성 트리거를 보장한다. 테스트용 인메모리 DB에도 동일하게 추가한다. */
        val immutabilityCallback: RoomDatabase.Callback = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                ImmutabilityTriggers.ensure(db)
            }
        }

        /** 앱 내부 저장소에만 만든다. 파괴적 마이그레이션 fallback은 의도적으로 사용하지 않는다. */
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .addCallback(immutabilityCallback)
                .build()
    }
}
