package com.gatita.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 스키마 마이그레이션 목록.
 *
 * 규칙:
 * - 버전을 올릴 때 이곳에 Migration(from, to)을 추가하고 app/schemas의 새 버전 JSON을 함께 커밋한다.
 * - fallbackToDestructiveMigration은 사용하지 않는다. 사용자 여행·정산 데이터를 지우는 마이그레이션은 금지한다.
 * - 새 마이그레이션은 MigrationTestHelper 기반 기기 테스트로 기존 데이터 보존을 검증한다.
 */
object Migrations {
    /**
     * v1(추정 정산) → v2(결제액 방식).
     *
     * 데이터를 지우거나 다시 쓰지 않는다. v1 테이블(trips·segments·fuel_prices·settlements·settlement_items·settlement_shares)은
     * 그대로 두고 trips에 컬럼만 더하며, 새 테이블 4개를 만든다. v1 정산은 옛 추정 이력으로 계속 읽힌다.
     *
     * v1 여행에 채우는 값:
     * - name '새로운 여행', vehicleLabel '', revision 0.
     * - tripDate = startedAtMillis를 UTC로 해석한 날짜, zoneId 'UTC'(v1은 시간대를 저장하지 않았다).
     * - people = 옛 정산이 있으면 그 headcount(8 초과 가능), 없으면 1. includeDriver 1.
     * - createdAtMillis = startedAtMillis, updatedAtMillis = endedAtMillis ?: startedAtMillis.
     *
     * 새 테이블의 열·인덱스·외래키·기본값은 Room 엔티티(CostEntity, Payment*Entity)와 정확히 같아야 한다.
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `trips` ADD COLUMN `name` TEXT NOT NULL DEFAULT '새로운 여행'")
            db.execSQL("ALTER TABLE `trips` ADD COLUMN `tripDate` TEXT NOT NULL DEFAULT '1970-01-01'")
            db.execSQL("ALTER TABLE `trips` ADD COLUMN `zoneId` TEXT NOT NULL DEFAULT 'UTC'")
            db.execSQL("ALTER TABLE `trips` ADD COLUMN `vehicleLabel` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `trips` ADD COLUMN `people` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `trips` ADD COLUMN `includeDriver` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `trips` ADD COLUMN `revision` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `trips` ADD COLUMN `createdAtMillis` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `trips` ADD COLUMN `updatedAtMillis` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "UPDATE `trips` SET " +
                    "`tripDate` = COALESCE(date(`startedAtMillis` / 1000, 'unixepoch'), '1970-01-01'), " +
                    "`createdAtMillis` = `startedAtMillis`, " +
                    "`updatedAtMillis` = COALESCE(`endedAtMillis`, `startedAtMillis`)",
            )
            db.execSQL(
                "UPDATE `trips` SET `people` = (SELECT s.`headcount` FROM `settlements` s WHERE s.`tripId` = `trips`.`id`) " +
                    "WHERE EXISTS (SELECT 1 FROM `settlements` s WHERE s.`tripId` = `trips`.`id`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `costs` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tripId` INTEGER NOT NULL, " +
                    "`requestId` TEXT NOT NULL, `kind` TEXT NOT NULL, `amountWon` INTEGER NOT NULL, " +
                    "`description` TEXT, `unitPrice` TEXT, `paidOn` TEXT, " +
                    "`included` INTEGER NOT NULL DEFAULT 1, " +
                    "`createdAtMillis` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_costs_tripId_createdAtMillis_id` ON `costs` (`tripId`, `createdAtMillis`, `id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_costs_tripId_included` ON `costs` (`tripId`, `included`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_costs_requestId` ON `costs` (`requestId`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `payment_settlements` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tripId` INTEGER NOT NULL, " +
                    "`requestId` TEXT NOT NULL, `tripName` TEXT NOT NULL, `tripDate` TEXT NOT NULL, " +
                    "`tripSource` TEXT NOT NULL, `distanceKm` TEXT, `recordedSeconds` INTEGER, " +
                    "`measurementQuality` TEXT NOT NULL, `totalWon` INTEGER NOT NULL, " +
                    "`fuelTotalWon` INTEGER NOT NULL, `otherTotalWon` INTEGER NOT NULL, " +
                    "`people` INTEGER NOT NULL, `includeDriver` INTEGER NOT NULL, `splitPeople` INTEGER NOT NULL, " +
                    "`sourceTripRevision` INTEGER NOT NULL, `calculationVersion` TEXT NOT NULL, " +
                    "`confirmedAtMillis` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_payment_settlements_tripId` ON `payment_settlements` (`tripId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_payment_settlements_requestId` ON `payment_settlements` (`requestId`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `payment_settlement_costs` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `settlementId` INTEGER NOT NULL, " +
                    "`originalCostId` INTEGER NOT NULL, `ordinal` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                    "`amountWon` INTEGER NOT NULL, `description` TEXT, `unitPrice` TEXT, `paidOn` TEXT, " +
                    "`included` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`settlementId`) REFERENCES `payment_settlements`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_payment_settlement_costs_settlementId_originalCostId` " +
                    "ON `payment_settlement_costs` (`settlementId`, `originalCostId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_payment_settlement_costs_settlementId_ordinal` " +
                    "ON `payment_settlement_costs` (`settlementId`, `ordinal`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `payment_settlement_shares` (" +
                    "`settlementId` INTEGER NOT NULL, `ordinal` INTEGER NOT NULL, `displayName` TEXT NOT NULL, " +
                    "`isDriver` INTEGER NOT NULL, `amountWon` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`settlementId`, `ordinal`), " +
                    "FOREIGN KEY(`settlementId`) REFERENCES `payment_settlements`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `app_settings` (`id` INTEGER NOT NULL, `driverName` TEXT NOT NULL, `includeDriver` INTEGER NOT NULL, `appearance` TEXT NOT NULL, PRIMARY KEY(`id`))")
        }
    }
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `trip_sessions` (`tripId` INTEGER NOT NULL, `pausedAt` INTEGER, `hidden` INTEGER NOT NULL, PRIMARY KEY(`tripId`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        }
    }
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
