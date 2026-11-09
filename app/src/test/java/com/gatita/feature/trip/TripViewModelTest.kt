package com.gatita.feature.trip

import com.gatita.domain.model.EfficiencyScope
import com.gatita.domain.model.EfficiencyUnit
import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.VehicleSource
import com.gatita.feature.Fixture
import com.gatita.feature.GatedTripRepository
import com.gatita.feature.LostAckTripRepository
import com.gatita.feature.MainDispatcherRule
import com.gatita.feature.common.MapAttemptStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun viewModel(fixture: Fixture, tripId: Long, attempts: MapAttemptStore = MapAttemptStore()) = TripViewModel(
        tripId = tripId,
        trips = fixture.trips,
        saveSegmentUseCase = fixture.saveSegment,
        ledgerRepository = fixture.payments,
        observeCostLedger = fixture.observeLedger,
        setCostIncluded = fixture.setIncluded,
        deleteCost = fixture.deleteCost,
        completeTripUseCase = fixture.complete,
        discardEmptyTripUseCase = fixture.discard,
        clock = fixture.clock,
        attempts = attempts,
    )

    @Test
    fun `segment is saved as MANUAL with the entered segment-average efficiency and the form is cleared`() = runTest {
        val fixture = Fixture()
        val vm = viewModel(fixture, fixture.seedActiveTrip())

        vm.onEndOdometerChange("100")
        vm.onEfficiencyChange("12.4")
        vm.saveSegment()
        advanceUntilIdle()

        val saved = fixture.store.segments.single()
        assertEquals(VehicleSource.MANUAL, saved.source)
        assertEquals(EfficiencyScope.SEGMENT_AVERAGE, saved.efficiency?.scope)
        assertEquals(EfficiencyUnit.KM_PER_LITER, saved.efficiency?.unit)
        val state = vm.state.value
        assertEquals(1, state.segments.size)
        assertEquals("수동 입력", state.segments.single().source)
        assertEquals("", state.endOdometerInput)
        assertNull(state.segmentError)
        assertNotNull(state.segmentNotice)
    }

    @Test
    fun `invalid input is rejected without saving and the typed text is preserved`() = runTest {
        val fixture = Fixture()
        val vm = viewModel(fixture, fixture.seedActiveTrip())

        vm.onEndOdometerChange("1e3")
        vm.onEfficiencyChange("12.4")
        vm.saveSegment()
        advanceUntilIdle()

        assertTrue(fixture.store.segments.isEmpty())
        assertNotNull(vm.state.value.endOdometerError)
        assertEquals("1e3", vm.state.value.endOdometerInput)
        assertEquals("12.4", vm.state.value.efficiencyInput)
        assertFalse(vm.state.value.isSavingSegment)
    }

    @Test
    fun `zero efficiency is rejected rather than treated as missing`() = runTest {
        val fixture = Fixture()
        val vm = viewModel(fixture, fixture.seedActiveTrip())

        vm.onEndOdometerChange("100")
        vm.onEfficiencyChange("0")
        vm.saveSegment()
        advanceUntilIdle()

        assertTrue(fixture.store.segments.isEmpty())
        assertNotNull(vm.state.value.efficiencyError)
    }

    @Test
    fun `explicitly unavailable efficiency saves the distance with null efficiency`() = runTest {
        val fixture = Fixture()
        val vm = viewModel(fixture, fixture.seedActiveTrip())

        vm.onEndOdometerChange("100")
        vm.onEfficiencyChange("")
        vm.saveSegment()
        advanceUntilIdle()

        val saved = fixture.store.segments.single()
        assertNull(saved.efficiency)
        assertEquals("연비 미기록", vm.state.value.segments.single().efficiency)
    }

    @Test
    fun `domain rejection shows a message and keeps the input`() = runTest {
        val fixture = Fixture()
        val vm = viewModel(fixture, fixture.seedActiveTrip(startKm = "500"))

        vm.onEndOdometerChange("400")
        vm.onEfficiencyChange("12")
        vm.saveSegment()
        advanceUntilIdle()

        assertTrue(fixture.store.segments.isEmpty())
        assertNotNull(vm.state.value.segmentError)
        assertEquals("400", vm.state.value.endOdometerInput)
    }

    @Test
    fun `second tap while saving is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var gated: GatedTripRepository? = null
        val fixture = Fixture(tripsWrapper = { GatedTripRepository(it, gate).also { g -> gated = g } })
        val vm = viewModel(fixture, fixture.seedActiveTrip())

        vm.onEndOdometerChange("100")
        vm.onEfficiencyChange("12.4")
        vm.saveSegment()
        assertTrue(vm.state.value.isSavingSegment)
        vm.saveSegment()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, fixture.store.segments.size)
        // 두 번째 탭이 막히지 않았다면 유스케이스가 한 번 더 실행되어 조회가 2번이 된다.
        assertEquals(1, gated!!.eventLookups)
        assertFalse(vm.state.value.isSavingSegment)
    }

    @Test
    fun `retry after a lost acknowledgement reuses the event id and does not duplicate the segment`() = runTest {
        val fixture = Fixture(tripsWrapper = { LostAckTripRepository(it) })
        val attempts = MapAttemptStore()
        val vm = viewModel(fixture, fixture.seedActiveTrip(), attempts)

        vm.onEndOdometerChange("100")
        vm.onEfficiencyChange("12.4")
        vm.saveSegment()
        advanceUntilIdle()

        assertEquals(1, fixture.store.segments.size)
        assertNotNull(vm.state.value.segmentError)
        assertEquals("100", vm.state.value.endOdometerInput)
        val eventId = attempts.get("segmentEventId")
        assertNotNull(eventId)
        assertEquals(eventId, fixture.store.segments.single().eventId)

        vm.saveSegment()
        advanceUntilIdle()

        assertEquals(1, fixture.store.segments.size)
        assertNull(vm.state.value.segmentError)
        assertNull(attempts.get("segmentEventId"))
        assertEquals(1, vm.state.value.segments.size)
    }

    @Test
    fun `cost only trip completes once without fabricated distance`() = runTest {
        val fixture = Fixture()
        val tripId = fixture.seedActiveTrip()
        fixture.addCost(tripId, com.gatita.domain.model.CostInput(com.gatita.domain.model.CostKind.PARKING,3000),"cost-1")
        val vm = viewModel(fixture,tripId)
        vm.requestComplete()
        assertTrue(vm.state.value.showCompleteConfirm)
        vm.confirmComplete()
        advanceUntilIdle()
        assertEquals(TripStatus.FINISHED,fixture.store.trips.getValue(tripId).status)
        assertEquals(TripEvent.OpenSettlement(tripId),vm.events.first())
    }

    @Test
    fun `empty trip cannot complete and can be discarded`() = runTest {
        val fixture=Fixture()
        val id=fixture.seedActiveTrip()
        val vm=viewModel(fixture,id)
        vm.requestComplete();vm.confirmComplete();advanceUntilIdle()
        assertEquals(TripStatus.IN_PROGRESS,fixture.store.trips.getValue(id).status)
        vm.requestDiscard();vm.confirmDiscard();advanceUntilIdle()
        assertFalse(fixture.store.trips.containsKey(id))
    }

    @Test
    fun `unknown trip shows the not found state`() = runTest {
        val vm = viewModel(Fixture(), tripId = 999)
        assertTrue(vm.state.value.notFound)
        assertFalse(vm.state.value.isLoading)
    }
}
