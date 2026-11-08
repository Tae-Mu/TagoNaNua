package com.gatita.data.local.migration

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * DB 수준 불변·무결성 규칙. Room이 만들 수 없는 제약(CHECK 성격)을 트리거로 보장한다.
 *
 * - 확정 정산 스냅샷(v1 유가·정산·항목·분담액, v2 결제액 정산·비용 복사본·분담액)의 UPDATE/DELETE 거부.
 * - 확정(SETTLED)된 여행의 비용 INSERT/UPDATE/DELETE와 분담 정책 변경 거부.
 * - 비용 금액 범위·종류별 null 규칙, 분담 정책(인원 1..8, 1명이면 운전자 포함), 정산 합계 형식 검사.
 *
 * `IF NOT EXISTS`라 DB를 열 때마다 실행해도 안전하므로 스키마 버전을 올리지 않고 보장한다.
 * Room 스키마 검증 대상이 아니므로 app/schemas JSON에는 나타나지 않는다.
 * 앞으로 확정 여행 삭제를 지원하려면 이 트리거와 RESTRICT 외래키를 함께 설계해야 한다.
 */
object ImmutabilityTriggers {
    private val IMMUTABLE_TABLES = listOf(
        "fuel_prices",
        "settlements",
        "settlement_items",
        "settlement_shares",
        "payment_settlements",
        "payment_settlement_costs",
        "payment_settlement_shares",
    )

    private fun trigger(name: String, timing: String, on: String, whenClause: String?, message: String): String =
        "CREATE TRIGGER IF NOT EXISTS $name $timing ON $on " +
            (if (whenClause != null) "WHEN $whenClause " else "") +
            "BEGIN SELECT RAISE(ABORT, '$message'); END"

    private fun invalidCost(row: String): String =
        "$row.amountWon < 1 OR $row.amountWon > 10000000 " +
            "OR $row.kind NOT IN ('FUEL', 'PARKING', 'TOLL', 'OTHER') " +
            "OR ($row.kind = 'FUEL' AND ($row.unitPrice IS NULL OR $row.paidOn IS NULL)) " +
            "OR ($row.kind <> 'FUEL' AND ($row.unitPrice IS NOT NULL OR $row.paidOn IS NOT NULL)) " +
            "OR $row.included NOT IN (0, 1)"

    private const val INVALID_POLICY =
        "NEW.people < 1 OR NEW.people > 8 OR (NEW.people = 1 AND NEW.includeDriver <> 1)"

    val statements: List<String> = buildList {
        IMMUTABLE_TABLES.forEach { table ->
            listOf("UPDATE", "DELETE").forEach { op ->
                add(trigger("${table}_no_${op.lowercase()}", "BEFORE $op", table, null, "$table rows are immutable"))
            }
        }

        // 확정된 여행의 비용은 바꿀 수 없다.
        add(trigger("costs_locked_insert", "BEFORE INSERT", "costs", "(SELECT status FROM trips WHERE id = NEW.tripId) = 'SETTLED'", "settled trip costs are locked"))
        add(trigger("costs_locked_update", "BEFORE UPDATE", "costs", "(SELECT status FROM trips WHERE id = OLD.tripId) = 'SETTLED'", "settled trip costs are locked"))
        add(trigger("costs_locked_delete", "BEFORE DELETE", "costs", "(SELECT status FROM trips WHERE id = OLD.tripId) = 'SETTLED'", "settled trip costs are locked"))

        // 비용 금액·종류별 필드 규칙.
        add(trigger("costs_valid_insert", "BEFORE INSERT", "costs", invalidCost("NEW"), "invalid cost"))
        add(trigger("costs_valid_update", "BEFORE UPDATE", "costs", invalidCost("NEW"), "invalid cost"))
        add(trigger("payment_settlement_costs_valid_insert", "BEFORE INSERT", "payment_settlement_costs", invalidCost("NEW"), "invalid cost"))

        // 분담 정책. 인원 범위는 새 값을 쓰는 INSERT와 정책 컬럼 UPDATE에만 적용해 v1에서 올라온 여행의 옛 인원을 건드리지 않는다.
        add(trigger("trips_policy_valid_insert", "BEFORE INSERT", "trips", INVALID_POLICY, "invalid split policy"))
        add(trigger("trips_policy_valid_update", "BEFORE UPDATE OF people, includeDriver", "trips", INVALID_POLICY, "invalid split policy"))
        add(trigger("trips_settled_policy_locked", "BEFORE UPDATE OF people, includeDriver", "trips", "OLD.status = 'SETTLED'", "settled trip policy is locked"))

        // 결제액 정산 형식. 합계 일치는 저장소 트랜잭션이 자식 행 삽입 뒤에 검사한다.
        add(
            trigger(
                "payment_settlements_valid_insert",
                "BEFORE INSERT",
                "payment_settlements",
                "NEW.totalWon < 1 OR NEW.fuelTotalWon + NEW.otherTotalWon <> NEW.totalWon " +
                    "OR NEW.people < 1 OR NEW.people > 8 OR NEW.splitPeople < 1 OR NEW.splitPeople > NEW.people",
                "invalid payment settlement",
            ),
        )
        add(trigger("payment_settlement_shares_valid_insert", "BEFORE INSERT", "payment_settlement_shares", "NEW.amountWon < 0", "invalid share"))
    }

    fun ensure(db: SupportSQLiteDatabase) {
        if (db.isReadOnly) return
        statements.forEach { db.execSQL(it) }
    }
}
