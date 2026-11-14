package com.gatita.domain.calculator

import com.gatita.d
import com.gatita.domain.model.Cost
import com.gatita.domain.model.CostKind
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.EfficiencyReading
import com.gatita.domain.model.EfficiencyScope
import com.gatita.domain.model.EfficiencyUnit
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.FuelSnapshot
import com.gatita.domain.model.FuelSnapshotKind
import com.gatita.domain.model.RefuelEvent
import com.gatita.domain.model.Trip
import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.VehicleSource
import com.gatita.domain.model.FUEL_SEGMENTS_CALCULATION_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class FuelTrackingCalculatorTest {

    private fun reading(lPer100: String, km: String) = EfficiencyReading(
        d(lPer100), EfficiencyUnit.LITERS_PER_100_KM, EfficiencyScope.CUMULATIVE_AVERAGE, "since-refuel", d(km),
    )

    private var nextId = 1L
    private fun snap(
        t: Long,
        odo: String,
        pct: Int? = 80,
        eff: EfficiencyReading? = null,
        parked: Boolean? = null,
        kind: FuelSnapshotKind = FuelSnapshotKind.PERIODIC,
    ) = FuelSnapshot(nextId++, 1, t, kind, d(odo), pct, eff, parked, VehicleSource.PLEOS)

    private fun refuel(t: Long, dismissed: Boolean = false) = RefuelEvent(nextId++, 1, t, 20, 80, dismissed)

    private fun fuelCost(id: Long, unit: String, undetected: Boolean = false) = Cost(
        id = id, tripId = 1, requestId = "r$id", kind = CostKind.FUEL, amountWon = 50_000, description = "",
        unitPriceWonPerLiter = d(unit), paidOn = LocalDate.of(2026, 11, 12), included = true,
        createdAtMillis = id, updatedAtMillis = id, undetected = undetected,
    )

    private fun otherCost(id: Long, won: Long, included: Boolean = true) = Cost(
        id = id, tripId = 1, requestId = "r$id", kind = CostKind.TOLL, amountWon = won, description = "",
        unitPriceWonPerLiter = null, paidOn = null, included = included, createdAtMillis = id, updatedAtMillis = id,
    )

    private fun trip(manual: String? = null, status: TripStatus = TripStatus.FINISHED, people: Int = 2) = Trip(
        id = 1, status = status, source = VehicleSource.PLEOS, startOdometerKm = d("1000"), startedAtMillis = 0,
        endOdometerKm = d("1200"), endedAtMillis = 99_000, people = people, includeDriver = true,
        calculationVersion = FUEL_SEGMENTS_CALCULATION_VERSION, manualFuelUnitPrice = manual?.let(::d),
    )

    // ---- FuelIntervalBuilder

    @Test fun `no refuel gives one interval with start and end`() {
        val out = FuelIntervalBuilder.build(
            listOf(snap(0, "1000", eff = reading("8", "1")), snap(60_000, "1100", eff = reading("8", "100"))),
            emptyList(),
        )
        assertEquals(1, out.size)
        assertEquals(0, d("100").compareTo(out[0].distanceKm))
        assertEquals("refuel-0", out[0].start?.aggregationKey)
        assertEquals("refuel-0", out[0].end?.aggregationKey)
    }

    @Test fun `refuel splits intervals and later start is null`() {
        val out = FuelIntervalBuilder.build(
            listOf(
                snap(0, "1000", eff = reading("8", "1")),
                snap(60_000, "1100", eff = reading("8", "100")),
                snap(120_000, "1100", eff = reading("0.1", "0.1")),
                snap(180_000, "1200", eff = reading("9", "100")),
            ),
            listOf(refuel(120_000)),
        )
        assertEquals(2, out.size)
        assertNull(out[1].start)
        assertEquals("refuel-1", out[1].end?.aggregationKey)
        assertEquals(0, d("100").compareTo(out[1].distanceKm))
    }

    @Test fun `dismissed refuel does not split`() {
        val out = FuelIntervalBuilder.build(
            listOf(snap(0, "1000"), snap(60_000, "1100")),
            listOf(refuel(30_000, dismissed = true)),
        )
        assertEquals(1, out.size)
    }

    @Test fun `no snapshots still yields one interval per refuel plus one`() {
        val out = FuelIntervalBuilder.build(emptyList(), listOf(refuel(1), refuel(2)))
        assertEquals(3, out.size)
        assertTrue(out.all { it.end == null })
    }

    @Test fun `missing end efficiency stays null and is not borrowed`() {
        val out = FuelIntervalBuilder.build(
            listOf(snap(0, "1000", eff = reading("8", "1")), snap(60_000, "1100", eff = null)),
            emptyList(),
        )
        assertNull(out[0].end)
    }

    // ---- StopDetector

    @Test fun `stop after two minutes unchanged odometer`() {
        val h = listOf(snap(0, "1000", parked = true), snap(60_000, "1000", parked = true))
        assertTrue(StopDetector.isNewStop(h, snap(120_000, "1000", parked = true)))
    }

    @Test fun `no stop before two minutes`() {
        val h = listOf(snap(0, "1000", parked = true), snap(60_000, "1000", parked = true))
        assertFalse(StopDetector.isNewStop(h, snap(100_000, "1000", parked = true)))
    }

    @Test fun `no stop when moving or not parked`() {
        val h = listOf(snap(0, "1000"), snap(60_000, "1000"))
        assertFalse(StopDetector.isNewStop(h, snap(120_000, "1001")))
        assertFalse(StopDetector.isNewStop(h, snap(120_000, "1000", parked = false)))
    }

    @Test fun `stop reported once per run`() {
        val h = listOf(
            snap(0, "1000"), snap(120_000, "1000", kind = FuelSnapshotKind.STOP),
        )
        assertFalse(StopDetector.isNewStop(h, snap(180_000, "1000")))
    }

    // ---- FuelSettlementPlanner

    private fun twoIntervalSnapshots() = listOf(
        snap(0, "1000", eff = reading("8", "1")),
        snap(60_000, "1100", eff = reading("10", "100")),
        snap(120_000, "1100", eff = reading("0.1", "0.1")),
        snap(180_000, "1200", eff = reading("5", "100")),
    )

    @Test fun `preview totals fuel plus other and splits by headcount`() {
        // 구간0: 0.1배? 1km→100km 의 차이로 10L*... 직접 계산은 계산기 테스트가 검증하므로 여기서는 합산·분담 일관성만 본다.
        val costs = listOf(fuelCost(1, "1700"), otherCost(2, 3_000))
        val result = FuelSettlementPlanner.preview(
            trip(), costs, twoIntervalSnapshots(), listOf(refuel(120_000)), null,
        ) as DomainResult.Success
        val p = result.value
        assertEquals(p.fuelTotalWon + 3_000, p.totalWon)
        assertEquals(p.totalWon, p.shares.sumOf { it.amountWon })
        assertEquals(2, p.shares.size)
        assertTrue(p.canConfirm)
        assertEquals(2, p.fuel!!.intervals.size)
    }

    @Test fun `excluded other costs are not counted`() {
        val p = (FuelSettlementPlanner.preview(
            trip(manual = "1700"), listOf(otherCost(1, 3_000), otherCost(2, 9_000, included = false)),
            listOf(snap(0, "1000", eff = reading("8", "1")), snap(60_000, "1100", eff = reading("8", "100"))),
            emptyList(), null,
        ) as DomainResult.Success).value
        assertEquals(3_000, p.otherTotalWon)
    }

    @Test fun `no fuel price excludes fuel but does not block`() {
        val p = (FuelSettlementPlanner.preview(
            trip(), listOf(otherCost(1, 4_000)),
            listOf(snap(0, "1000", eff = reading("8", "1")), snap(60_000, "1100", eff = reading("8", "100"))),
            emptyList(), null,
        ) as DomainResult.Success).value
        assertTrue(p.fuel!!.fuelNotIncluded)
        assertEquals(4_000, p.totalWon)
        assertTrue(p.canConfirm)
    }

    @Test fun `in progress trip cannot confirm`() {
        val p = (FuelSettlementPlanner.preview(
            trip(status = TripStatus.IN_PROGRESS), listOf(otherCost(1, 4_000)), emptyList(), emptyList(), null,
        ) as DomainResult.Success).value
        assertFalse(p.canConfirm)
        assertEquals(ErrorCode.TRIP_NOT_FINISHED, p.blockingReason)
    }

    @Test fun `completion requires matching refuel and fuel cost counts`() {
        assertTrue(FuelSettlementPlanner.checkCompletion(emptyList(), emptyList()) is DomainResult.Success)
        assertTrue(FuelSettlementPlanner.checkCompletion(emptyList(), listOf(refuel(1))) is DomainResult.Failure)
        assertTrue(FuelSettlementPlanner.checkCompletion(listOf(fuelCost(1, "1700")), emptyList()) is DomainResult.Failure)
        assertTrue(
            FuelSettlementPlanner.checkCompletion(listOf(fuelCost(1, "1700", undetected = true)), emptyList())
                is DomainResult.Success,
        )
        assertTrue(
            FuelSettlementPlanner.checkCompletion(listOf(fuelCost(1, "1700")), listOf(refuel(1, dismissed = true)))
                is DomainResult.Failure,
        )
    }

    @Test fun `build snapshot copies all costs and intervals`() {
        val costs = listOf(fuelCost(1, "1700"), otherCost(2, 3_000, included = false))
        val s = (FuelSettlementPlanner.buildSnapshot(
            trip(), costs + otherCost(3, 1_000), twoIntervalSnapshots(), listOf(refuel(120_000)), "나", "req", 5,
        ) as DomainResult.Success).value
        assertEquals(FUEL_SEGMENTS_CALCULATION_VERSION, s.calculationVersion)
        assertEquals(3, s.costs.size)
        assertEquals(2, s.fuelIntervals.size)
        assertEquals(s.totalWon, s.shares.sumOf { it.amountWon })
        assertEquals(1_000L, s.otherTotalWon)
    }
}
