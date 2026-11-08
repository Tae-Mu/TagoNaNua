package com.gatita.feature.settlement

import com.gatita.domain.model.TripStatus
import com.gatita.feature.Fixture
import com.gatita.feature.LostAckSettlementRepository
import com.gatita.feature.MainDispatcherRule
import com.gatita.feature.common.MapAttemptStore
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
class SettlementViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun viewModel(fixture: Fixture, tripId: Long, attempts: MapAttemptStore = MapAttemptStore()) =
        SettlementViewModel(
            tripId = tripId,
            trips = fixture.trips,
            settlements = fixture.settlements,
            previewSettlement = fixture.previewSettlement,
            confirmSettlement = fixture.confirmSettlement,
            attempts = attempts,
        )

    private fun SettlementViewModel.fill(price: String = "368.5", headcount: String = "4") {
        onPriceChange(price)
        onHeadcountChange(headcount)
    }

    @Test
    fun `preview shows the segment-sum basis and the 3685 won over 4 people split`() = runTest {
        val fixture = Fixture()
        val vm = viewModel(fixture, fixture.seedFinishedTrip())

        vm.fill()
        vm.preview()
        advanceUntilIdle()

        val ready = vm.state.value.preview as PreviewState.Ready
        assertFalse(ready.view.isEstimate)
        assertEquals("3,685원", ready.view.totalCost)
        assertEquals(listOf("922원", "921원", "921원", "921원"), ready.view.shareLines.map { it.amount })
        assertEquals("수동 입력", ready.view.sourceLabel)
        assertEquals(1, ready.view.basisLines.size)
        assertTrue(fixture.store.settlements.isEmpty())
    }

    @Test
    fun `invalid price and headcount are reported and nothing is calculated`() = runTest {
        val fixture = Fixture()
        val vm = viewModel(fixture, fixture.seedFinishedTrip())

        vm.fill(price = "abc", headcount = "0")
        vm.preview()
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.priceError)
        assertNotNull(state.headcountError)
        assertEquals(PreviewState.None, state.preview)
        assertEquals("abc", state.priceInput)
    }

    @Test
    fun `unmeasured segment blocks the settlement instead of estimating`() = runTest {
        val fixture = Fixture()
        val vm = viewModel(fixture, fixture.seedFinishedTrip(measured = false))

        vm.fill()
        vm.preview()
        advanceUntilIdle()

        val state = vm.state.value
        val blocked = state.preview as PreviewState.Blocked
        assertTrue(blocked.reasons.isNotEmpty())
        assertTrue(state.fallbackVisible)
        assertFalse(state.useFallback)
        assertEquals(1, state.unmeasuredSegmentCount)
    }

    @Test
    fun `explicit reference efficiency produces a labelled estimate with reasons`() = runTest {
        val fixture = Fixture()
        val vm = viewModel(fixture, fixture.seedFinishedTrip(measured = false))
        vm.fill()
        vm.preview()
        advanceUntilIdle()

        vm.onUseFallbackChange(true)
        vm.onFallbackInputChange("12.5")
        vm.preview()
        advanceUntilIdle()

        val ready = vm.state.value.preview as PreviewState.Ready
        assertTrue(ready.view.isEstimate)
        assertNotNull(ready.input.fallbackEfficiency)
        assertTrue(ready.view.fallbackReasons.isNotEmpty())
        assertEquals("추정: 기준 연비로 전체 거리 계산", ready.view.methodLabel)
    }

    @Test
    fun `fallback is enabled but its value is blank - error and no silent default`() = runTest {
        val fixture = Fixture()
        val vm = viewModel(fixture, fixture.seedFinishedTrip(measured = false))
        vm.fill()
        vm.preview()
        advanceUntilIdle()

        vm.onUseFallbackChange(true)
        vm.preview()
        advanceUntilIdle()

        assertNotNull(vm.state.value.fallbackError)
        assertEquals(PreviewState.None, vm.state.value.preview)
    }

    @Test
    fun `confirm requires a preview and editing an input invalidates it`() = runTest {
        val fixture = Fixture()
        val vm = viewModel(fixture, fixture.seedFinishedTrip())
        vm.fill()

        vm.confirm()
        advanceUntilIdle()
        assertTrue(fixture.store.settlements.isEmpty())

        vm.preview()
        advanceUntilIdle()
        assertTrue(vm.state.value.preview is PreviewState.Ready)
        vm.onHeadcountChange("3")
        assertEquals(PreviewState.None, vm.state.value.preview)

        vm.confirm()
        advanceUntilIdle()
        assertTrue(fixture.store.settlements.isEmpty())
    }

    @Test
    fun `confirm saves exactly one settlement, settles the trip and opens the result`() = runTest {
        val fixture = Fixture()
        val tripId = fixture.seedFinishedTrip()
        val vm = viewModel(fixture, tripId)
        vm.fill()
        vm.preview()
        advanceUntilIdle()

        vm.confirm()
        advanceUntilIdle()
        vm.confirm() // 확정 뒤 다시 눌러도 새 정산이 생기지 않는다
        advanceUntilIdle()

        assertEquals(1, fixture.store.settlements.size)
        assertEquals(TripStatus.SETTLED, fixture.store.trips.getValue(tripId).status)
        assertEquals(SettlementEvent.OpenResult(tripId), vm.events.first())
    }

    @Test
    fun `retry after a lost acknowledgement reuses the request id and does not duplicate`() = runTest {
        val fixture = Fixture(settlementsWrapper = { LostAckSettlementRepository(it) })
        val attempts = MapAttemptStore()
        val tripId = fixture.seedFinishedTrip()
        val vm = viewModel(fixture, tripId, attempts)
        vm.fill()
        vm.preview()
        advanceUntilIdle()

        vm.confirm()
        advanceUntilIdle()
        assertEquals(1, fixture.store.settlements.size)
        assertNotNull(vm.state.value.confirmError)
        val requestId = attempts.get("confirmRequestId")
        assertEquals(requestId, fixture.store.settlements.single().requestId)
        assertTrue(vm.state.value.preview is PreviewState.Ready)

        vm.confirm()
        advanceUntilIdle()

        assertEquals(1, fixture.store.settlements.size)
        assertNull(vm.state.value.confirmError)
        assertNull(attempts.get("confirmRequestId"))
        assertEquals(SettlementEvent.OpenResult(tripId), vm.events.first())
    }

    @Test
    fun `an already settled trip redirects to the result`() = runTest {
        val fixture = Fixture()
        val tripId = fixture.seedFinishedTrip()
        val first = viewModel(fixture, tripId)
        first.fill()
        first.preview()
        advanceUntilIdle()
        first.confirm()
        advanceUntilIdle()

        val again = viewModel(fixture, tripId)
        assertEquals(SettlementEvent.OpenResult(tripId), again.events.first())
    }

    @Test
    fun `an in-progress trip cannot be previewed`() = runTest {
        val fixture = Fixture()
        val vm = viewModel(fixture, fixture.seedActiveTrip())
        vm.fill()
        vm.preview()
        advanceUntilIdle()
        assertEquals(PreviewState.None, vm.state.value.preview)
    }
}
