package com.gatita.feature.trip

import com.gatita.domain.model.TripStatus
import com.gatita.feature.Fixture
import com.gatita.feature.MainDispatcherRule
import com.gatita.feature.common.MapAttemptStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
