package com.gatita.feature.settlement

import com.gatita.feature.*
import com.gatita.feature.common.MapAttemptStore
import com.gatita.domain.model.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettlementViewModelTest {
    @get:Rule val main=MainDispatcherRule()
    private fun vm(f:Fixture,id:Long)=SettlementViewModel(id,f.previewPayment,f.updatePolicy,f.confirmPayment,MapAttemptStore())
    @Test fun `included payments drive preview and driver exclusion preserves exact total`()=runTest {
        val f=Fixture();val id=f.seedFinishedTrip()
        f.addCost(id,CostInput(CostKind.OTHER,55400),"one")
        val v=vm(f,id);v.onPeopleChange(4);advanceUntilIdle();v.onIncludeDriverChange(false);advanceUntilIdle()
        assertEquals(listOf(0L,18467L,18467L,18466L),v.state.value.shares.map{it.amountWon})
        v.requestConfirm();assertTrue(v.state.value.showConfirm);v.confirm();advanceUntilIdle()
        assertEquals(55400L,f.payments.payments.single().totalWon)
        assertEquals(TripStatus.SETTLED,f.store.trips.getValue(id).status)
    }
    @Test fun `no included expenses blocks confirmation`()=runTest {
        val f=Fixture();val v=vm(f,f.seedFinishedTrip());v.requestConfirm()
        assertFalse(v.state.value.showConfirm);assertFalse(v.state.value.preview!!.canConfirm)
    }
    @Test fun `stale preview cannot confirm altered expenses`()=runTest {
        val f=Fixture();val id=f.seedFinishedTrip();f.addCost(id,CostInput(CostKind.TOLL,100),"one")
        val v=vm(f,id);f.addCost(id,CostInput(CostKind.TOLL,200),"two")
        v.requestConfirm();v.confirm();advanceUntilIdle()
        assertTrue(f.payments.payments.isEmpty());assertNotNull(v.state.value.confirmError)
    }
    @Test fun `long driver name cannot be confirmed`()=runTest {
        val f=Fixture();val id=f.seedFinishedTrip();f.addCost(id,CostInput(CostKind.OTHER,1),"one")
        val v=vm(f,id);v.onDriverNameChange("가".repeat(21));v.requestConfirm()
        assertNotNull(v.state.value.nameError);assertFalse(v.state.value.showConfirm)
    }
    @Test fun `unknown trip is reported`()=runTest { assertTrue(vm(Fixture(),999).state.value.notFound) }
}
