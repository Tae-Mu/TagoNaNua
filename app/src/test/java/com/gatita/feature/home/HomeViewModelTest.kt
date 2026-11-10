package com.gatita.feature.home

import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.VehicleSource
import com.gatita.feature.Fixture
import com.gatita.feature.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
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
        observeSettlementList = fixture.observeList,
        restoreActiveTrip = fixture.restoreActiveTrip,
        startTripUseCase = fixture.startTrip,
        clock = fixture.clock,
        vehicle = object: com.gatita.domain.vehicle.VehicleDataSource {
            override val source=VehicleSource.PLEOS
            override suspend fun readSnapshot() = com.gatita.domain.vehicle.VehicleReadResult.Success(
                com.gatita.domain.vehicle.VehicleSnapshot(java.math.BigDecimal("12000.5"),null,fixture.clock.millis(),source))
        },
    )

    @Test
    fun `launch with no trips shows the empty start state`() = runTest {
        val vm = viewModel(Fixture())
        val state = vm.state.value
        assertFalse(state.isLoading)
        assertNull(state.activeTrip)
        assertEquals(emptyList<Any>(), state.pendingTrips)
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
        assertEquals(listOf(tripId), vm.state.value.pendingTrips.map { it.tripId })
    }

    @Test
    fun `start trip stores SDK odometer without user distance input`() = runTest {
        val fixture = Fixture()
        val vm = viewModel(fixture)

        vm.startTrip()
        advanceUntilIdle()

        val trip = fixture.store.trips.values.single()
        assertEquals(VehicleSource.PLEOS, trip.source)
        assertEquals(TripStatus.IN_PROGRESS, trip.status)
        assertEquals(0, trip.startOdometerKm.compareTo(java.math.BigDecimal("12000.5")))
        assertEquals(HomeEvent.OpenTrip(trip.id), vm.events.first())
    }

    @Test
    fun `SDK unavailable does not create a zero distance trip`() = runTest {
        val fixture=Fixture()
        val vm=HomeViewModel(fixture.restoreActiveTrip,fixture.observeList,fixture.startTrip,fixture.clock)
        vm.onNameChange("주말 여행")
        vm.startTrip()
        advanceUntilIdle()
        assertNotNull(vm.state.value.startError)
        assertEquals("주말 여행",vm.state.value.nameInput)
        assertEquals(0,fixture.store.trips.size)
        assertFalse(vm.state.value.isStarting)
    }

    @Test
    fun `sample snapshot cannot be used as real SDK data`() = runTest {
        val f=Fixture()
        val vm=HomeViewModel(f.restoreActiveTrip,f.observeList,f.startTrip,f.clock,vehicle=com.gatita.data.vehicle.SampleVehicleDataSource(f.clock))
        vm.startTrip();advanceUntilIdle()
        assertNotNull(vm.state.value.startError)
        assertTrue(f.store.trips.isEmpty())
    }

    @Test
    fun `starting while a trip is active explains and does not create a second trip`() = runTest {
        val fixture = Fixture()
        fixture.seedActiveTrip()
        val vm = viewModel(fixture)

        vm.startTrip()
        advanceUntilIdle()

        assertEquals(1, fixture.store.trips.size)
        assertNotNull(vm.state.value.startError)
        assertFalse(vm.state.value.isStarting)
    }
    @Test
    fun `saved driver default applies but solo travel always includes driver`() = runTest {
        val f=Fixture()
        val settings=object:com.gatita.domain.repository.SettingsRepository {
            val value=com.gatita.domain.repository.AppSettings("민수",false,"주간")
            override fun observe()=kotlinx.coroutines.flow.flowOf(value)
            override suspend fun get()=value
            override suspend fun save(settings:com.gatita.domain.repository.AppSettings) { }
        }
        val v=HomeViewModel(f.restoreActiveTrip,f.observeList,f.startTrip,f.clock,settings)
        assertFalse(v.state.value.includeDriver)
        v.onPeopleChange(1);v.onIncludeDriverChange(false)
        assertTrue(v.state.value.includeDriver)
        v.onPeopleChange(0);assertEquals(1,v.state.value.people)
        v.onPeopleChange(9);assertEquals(1,v.state.value.people)
    }
}
