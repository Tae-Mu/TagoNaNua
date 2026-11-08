package com.gatita.feature.home

import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.VehicleSource
import com.gatita.feature.Fixture
import com.gatita.feature.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun viewModel(fixture: Fixture) = HomeViewModel(
        trips = fixture.trips,
        restoreActiveTrip = fixture.restoreActiveTrip,
        startTripUseCase = fixture.startTrip,
        clock = fixture.clock,
    )

    @Test
    fun `launch with no trips shows the empty start state`() = runTest {
        val vm = viewModel(Fixture())
        val state = vm.state.value
        assertFalse(state.isLoading)
        assertNull(state.activeTrip)
        assertEquals(emptyList<Any>(), state.unsettledTrips)
    }

    @Test
    fun `launch restores the in-progress trip saved earlier`() = runTest {
        val fixture = Fixture()
        val tripId = fixture.seedActiveTrip(startKm = "12000")

        val vm = viewModel(fixture) // 앱 재실행: 새 ViewModel이 저장소에서 읽는다

        assertEquals(tripId, vm.state.value.activeTrip?.id)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `finished but unsettled trips are offered for settlement`() = runTest {
        val fixture = Fixture()
        val tripId = fixture.seedFinishedTrip()

        val vm = viewModel(fixture)

        assertNull(vm.state.value.activeTrip)
        assertEquals(listOf(tripId), vm.state.value.unsettledTrips.map { it.id })
    }

    @Test
    fun `start trip stores a MANUAL trip and opens it`() = runTest {
        val fixture = Fixture()
        val vm = viewModel(fixture)

        vm.onStartOdometerChange("12,000.5")
        vm.startTrip()
        advanceUntilIdle()

        val trip = fixture.store.trips.values.single()
        assertEquals(VehicleSource.MANUAL, trip.source)
        assertEquals(TripStatus.IN_PROGRESS, trip.status)
        assertEquals(0, trip.startOdometerKm.compareTo(java.math.BigDecimal("12000.5")))
        assertEquals(HomeEvent.OpenTrip(trip.id), vm.events.first())
    }

    @Test
    fun `invalid start odometer is rejected and the text is preserved`() = runTest {
        val fixture = Fixture()
        val vm = viewModel(fixture)

        vm.onStartOdometerChange("-5")
        vm.startTrip()
        advanceUntilIdle()

        assertNotNull(vm.state.value.startOdometerError)
        assertEquals("-5", vm.state.value.startOdometerInput)
        assertEquals(0, fixture.store.trips.size)
    }

    @Test
    fun `starting while a trip is active explains and does not create a second trip`() = runTest {
        val fixture = Fixture()
        fixture.seedActiveTrip()
        val vm = viewModel(fixture)

        vm.onStartOdometerChange("100")
        vm.startTrip()
        advanceUntilIdle()

        assertEquals(1, fixture.store.trips.size)
        assertNotNull(vm.state.value.startError)
        assertEquals("100", vm.state.value.startOdometerInput)
        assertFalse(vm.state.value.isStarting)
    }
}
