package com.gatita.domain.usecase

import com.gatita.FakeStore
import com.gatita.FakeTripRepository
import com.gatita.PaymentFakes
import com.gatita.SerialTransactionRunner
import com.gatita.d
import com.gatita.domain.model.CostInput
import com.gatita.domain.model.CostKind
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.FUEL_SEGMENTS_CALCULATION_VERSION
import com.gatita.domain.model.FuelSnapshot
import com.gatita.domain.model.FuelSnapshotKind
import com.gatita.domain.model.PAYMENTS_CALCULATION_VERSION
import com.gatita.domain.model.RefuelEvent
import com.gatita.domain.model.Trip
import com.gatita.domain.model.VehicleSource
import com.gatita.domain.repository.FuelTrackingRepository
import com.gatita.domain.vehicle.VehicleDataSource
import com.gatita.domain.vehicle.VehicleReadFailure
import com.gatita.domain.vehicle.VehicleReadResult
import com.gatita.domain.vehicle.VehicleSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private class FakeFuelRepository : FuelTrackingRepository {
    val snapshots = MutableStateFlow<List<FuelSnapshot>>(emptyList())
    val refuels = MutableStateFlow<List<RefuelEvent>>(emptyList())
    private var next = 1L
    override suspend fun insertSnapshot(snapshot: FuelSnapshot) = snapshot.copy(id = next++).also { snapshots.value += it }
    override suspend fun getSnapshots(tripId: Long) = snapshots.value.filter { it.tripId == tripId }
    override fun observeSnapshots(tripId: Long): Flow<List<FuelSnapshot>> = snapshots.map { l -> l.filter { it.tripId == tripId } }
    override suspend fun insertRefuel(event: RefuelEvent) = event.copy(id = next++).also { refuels.value += it }
    override suspend fun getRefuels(tripId: Long) = refuels.value.filter { it.tripId == tripId }
    override fun observeRefuels(tripId: Long): Flow<List<RefuelEvent>> = refuels.map { l -> l.filter { it.tripId == tripId } }
    override suspend fun setRefuelDismissed(eventId: Long, dismissed: Boolean, updatedAtMillis: Long): Boolean {
        refuels.value = refuels.value.map { if (it.id == eventId) it.copy(dismissed = dismissed) else it }
        return refuels.value.any { it.id == eventId }
    }
    override suspend fun setCostUndetected(costId: Long, undetected: Boolean, updatedAtMillis: Long) = false
    override suspend fun setManualFuelUnitPrice(tripId: Long, price: BigDecimal?, updatedAtMillis: Long) = true
}

private class ScriptedVehicle(var next: VehicleReadResult) : VehicleDataSource {
    override val source = VehicleSource.PLEOS
    override suspend fun readSnapshot() = next
    fun set(odo: String, pct: Int?, parked: Boolean? = null) {
        next = VehicleReadResult.Success(VehicleSnapshot(d(odo), null, 0, VehicleSource.PLEOS, pct, parked))
    }
}

class FuelTrackingUseCasesTest {
    private val store = FakeStore()
    private val trips = FakeTripRepository(store)
    private val payments = PaymentFakes(store)
    private val fuel = FakeFuelRepository()
    private val tx = SerialTransactionRunner()
    private val clockMillis = longArrayOf(1_000)
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant(): Instant = Instant.ofEpochMilli(clockMillis[0])
    }
    private val vehicle = ScriptedVehicle(VehicleReadResult.Failure(VehicleReadFailure.UNAVAILABLE))
    private val record = RecordFuelSnapshotUseCase(vehicle, trips, fuel, tx, clock)
    private val complete = CompleteTripUseCase(trips, payments, tx, fuel)
    private val addCost = AddCostUseCase(trips, payments, tx, clock)

    private suspend fun start(version: String = FUEL_SEGMENTS_CALCULATION_VERSION): Trip =
        (StartTripUseCase(trips, tx)(
            StartTripCommand(d("1000"), 1_000, VehicleSource.PLEOS, calculationVersion = version),
        ) as DomainResult.Success).value

    private suspend fun at(millis: Long, odo: String, pct: Int?, parked: Boolean? = null, tripId: Long) =
        run { clockMillis[0] = millis; vehicle.set(odo, pct, parked); record(tripId) }

    @Test fun `first read is START and unreadable vehicle stores nothing`() = runTest {
        val trip = start()
        assertNull(record(trip.id))
        assertTrue(fuel.snapshots.value.isEmpty())
        assertEquals(FuelSnapshotKind.START, at(1_000, "1000", 50, tripId = trip.id)?.kind)
    }

    @Test fun `percent rise of five or more creates a refuel event`() = runTest {
        val trip = start()
        at(1_000, "1000", 50, tripId = trip.id)
        at(61_000, "1010", 53, tripId = trip.id)
        assertTrue(fuel.refuels.value.isEmpty())
        at(121_000, "1020", 90, tripId = trip.id)
        assertEquals(1, fuel.refuels.value.size)
        assertEquals(53, fuel.refuels.value[0].percentBefore)
        assertEquals(90, fuel.refuels.value[0].percentAfter)
    }

    @Test fun `odometer going backwards is not stored`() = runTest {
        val trip = start()
        at(1_000, "1000", 50, tripId = trip.id)
        assertNull(at(61_000, "999", 50, tripId = trip.id))
        assertEquals(1, fuel.snapshots.value.size)
    }

    @Test fun `payments trip is not recorded`() = runTest {
        val trip = start(PAYMENTS_CALCULATION_VERSION)
        assertNull(at(1_000, "1000", 50, tripId = trip.id))
    }

    @Test fun `two minutes parked marks one STOP`() = runTest {
        val trip = start()
        at(1_000, "1000", 50, parked = true, tripId = trip.id)
        at(61_000, "1000", 50, parked = true, tripId = trip.id)
        assertEquals(FuelSnapshotKind.STOP, at(121_000, "1000", 50, parked = true, tripId = trip.id)?.kind)
        assertEquals(FuelSnapshotKind.PERIODIC, at(181_000, "1000", 50, parked = true, tripId = trip.id)?.kind)
    }

    @Test fun `trip cannot complete while refuel count and fuel costs differ`() = runTest {
        val trip = start()
        at(1_000, "1000", 50, tripId = trip.id)
        at(61_000, "1010", 90, tripId = trip.id)
        val blocked = complete(CompleteTripCommand(trip.id, 100_000, d("1010")))
        assertEquals(ErrorCode.REFUEL_COST_MISMATCH, (blocked as DomainResult.Failure).error.code)

        addCost(trip.id, CostInput(CostKind.FUEL, 50_000, null, d("1700"), null), "r1")
        assertTrue(complete(CompleteTripCommand(trip.id, 100_000, d("1010"))) is DomainResult.Success)
    }

    @Test fun `dismissing the refuel lets the trip complete without a fuel cost`() = runTest {
        val trip = start()
        at(1_000, "1000", 50, tripId = trip.id)
        at(61_000, "1010", 90, tripId = trip.id)
        val event = fuel.refuels.value.single()
        assertTrue(SetRefuelDismissedUseCase(trips, fuel, tx, clock)(trip.id, event.id, true) is DomainResult.Success)
        assertTrue(complete(CompleteTripCommand(trip.id, 100_000, d("1010"))) is DomainResult.Success)
        assertNotNull(fuel.snapshots.value.firstOrNull())
    }

    @Test fun `parked event stores a PARKED snapshot even when gear read fails`() = runTest {
        val trip = start()
        at(1_000, "1000", 50, tripId = trip.id)
        clockMillis[0] = 30_000; vehicle.set("1005", 50, null)
        val saved = record(trip.id, FuelSnapshotKind.PARKED)
        assertEquals(FuelSnapshotKind.PARKED, saved?.kind)
        assertEquals(true, saved?.parked)
    }

    @Test fun `manual price must be positive`() = runTest {
        val trip = start()
        val set = SetManualFuelUnitPriceUseCase(trips, fuel, tx, clock)
        assertEquals(ErrorCode.INVALID_FUEL_PRICE, (set(trip.id, d("0")) as DomainResult.Failure).error.code)
        assertTrue(set(trip.id, d("1700")) is DomainResult.Success)
    }
}
