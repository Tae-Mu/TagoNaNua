package com.gatita.domain.calculator

import com.gatita.domain.model.EfficiencyScope
import com.gatita.domain.model.FuelInterval
import com.gatita.domain.model.FuelRules
import com.gatita.domain.model.FuelSnapshot
import com.gatita.domain.model.FuelSnapshotKind
import com.gatita.domain.model.RefuelEvent
import java.math.BigDecimal

/**
 * 저장된 측정과 주유 이벤트로 주유 구간([FuelInterval])을 만든다. 순수 함수다.
 *
 * - 제외("주유 아님")한 이벤트는 구간을 나누지 않는다. 차량의 "주유 이후" 누적이 리셋되지 않았으므로 구간을 합친다.
 * - 주유 k번째 이후의 측정은 구간 k에 속한다(이벤트 감지 시각 이하의 측정부터).
 * - 구간 0의 시작은 첫 측정, 주유 직후 구간의 시작은 누적 0이라 null이다. 구간의 끝은 그 구간의 마지막 측정이며
 *   그 측정의 연비가 없으면 null이다(앞선 측정으로 대신하거나 평균으로 메우지 않는다).
 * - 거리는 주행거리계 차이다(표시용). 측정이 없는 구간은 0이다.
 */
object FuelIntervalBuilder {

    fun build(snapshots: List<FuelSnapshot>, refuels: List<RefuelEvent>): List<FuelInterval> {
        val ordered = snapshots.sortedWith(compareBy({ it.takenAtMillis }, { it.id }))
        val active = refuels.filter { !it.dismissed }.sortedWith(compareBy({ it.detectedAtMillis }, { it.id }))
        val first = ordered.firstOrNull()
            ?: return List(active.size + 1) { FuelInterval(it, BigDecimal.ZERO, null, null) }

        val epochs = List(active.size + 1) { mutableListOf<FuelSnapshot>() }
        for (snapshot in ordered) {
            val epoch = active.count { it.detectedAtMillis <= snapshot.takenAtMillis }
            epochs[epoch] += snapshot
        }

        var previousOdometer: BigDecimal = first.odometerKm
        return epochs.mapIndexed { index, inEpoch ->
            val last = inEpoch.lastOrNull()
            val distance = if (last == null) BigDecimal.ZERO else last.odometerKm.subtract(previousOdometer).max(BigDecimal.ZERO)
            if (last != null) previousOdometer = last.odometerKm
            val key = "refuel-$index"
            val end = last?.efficiency?.takeIf { it.scope == EfficiencyScope.CUMULATIVE_AVERAGE }?.copy(aggregationKey = key)
            val start = if (index == 0) {
                first.efficiency?.takeIf { it.scope == EfficiencyScope.CUMULATIVE_AVERAGE }?.copy(aggregationKey = key)
            } else {
                null
            }
            FuelInterval(index, distance, start, end)
        }
    }
}

/** 정차 경계 판정. 순수 함수다. */
object StopDetector {
    /**
     * [history]는 현재 측정 직전까지의 측정(시간순), [current]는 이번 측정이다.
     * 현재까지 주행거리가 변하지 않은 연속 구간이 [FuelRules.PARK_STOP_SECONDS] 이상이고, 그동안 P단이 아니었던 적이 없고
     * (기어를 읽지 못해 null이면 거리 변화 없음만으로 판정), 그 연속 구간에 이미 정차 경계가 없으면 true.
     */
    fun isNewStop(history: List<FuelSnapshot>, current: FuelSnapshot, stopSeconds: Long = FuelRules.PARK_STOP_SECONDS): Boolean {
        if (current.parked == false) return false
        val run = ArrayList<FuelSnapshot>()
        for (snapshot in history.asReversed()) {
            if (snapshot.odometerKm.compareTo(current.odometerKm) != 0) break
            if (snapshot.parked == false) break
            run += snapshot
        }
        if (run.isEmpty()) return false
        if (run.any { it.kind == FuelSnapshotKind.STOP }) return false
        val since = run.last().takenAtMillis
        return current.takenAtMillis - since >= stopSeconds * 1_000
    }
}
