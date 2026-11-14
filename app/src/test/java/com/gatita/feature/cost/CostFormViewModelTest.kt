package com.gatita.feature.cost

import com.gatita.feature.*
import com.gatita.feature.common.MapAttemptStore
import com.gatita.domain.model.*
import com.gatita.domain.usecase.UpdateCostUseCase
import com.gatita.success
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class CostFormViewModelTest {
    @get:Rule val main=MainDispatcherRule()
    private fun vm(f:Fixture,id:Long,cost:Long=0)=CostFormViewModel(id,cost,f.payments,f.addCost,UpdateCostUseCase(f.trips,f.payments,f.tx,f.clock),MapAttemptStore(),f.trips)
    @Test fun invalidAmountsAndMissingFuelPriceDoNotSave()=runTest {
        val f=Fixture();val id=f.seedActiveTrip();val v=vm(f,id)
        for(input in listOf("0","-1","10000001","1.5","abc")) {v.onAmountChange(input);v.save();advanceUntilIdle();assertNotNull(v.state.value.amountError)}
        v.onAmountChange("50000");v.onKindChange(CostKind.FUEL);v.onUnitPriceChange("");v.save();advanceUntilIdle()
        assertNotNull(v.state.value.unitPriceError);assertTrue(f.payments.costs.isEmpty())
    }
    @Test fun nonFuelOptionalDescriptionAndInvalidDateHandling()=runTest {
        val f=Fixture();val id=f.seedActiveTrip();val v=vm(f,id)
        v.onAmountChange("50000");v.onKindChange(CostKind.FUEL);v.onUnitPriceChange("1890");v.onPaidOnChange("2026-02-30");v.save();advanceUntilIdle()
        assertNotNull(v.state.value.paidOnError);assertTrue(f.payments.costs.isEmpty())
        v.onKindChange(CostKind.PARKING);v.onDescriptionChange("");v.save();advanceUntilIdle()
        val c=f.payments.costs.values.single();assertNull(c.description);assertNull(c.unitPriceWonPerLiter);assertNull(c.paidOn)
    }
    @Test fun staleEditKeepsInputAndDoesNotOverwriteStoredCost()=runTest {
        val f=Fixture();val id=f.seedActiveTrip();val c=f.addCost(id,CostInput(CostKind.PARKING,3000),"first").success()
        val v=vm(f,id,c.id);v.onAmountChange("4000")
        f.addCost(id,CostInput(CostKind.OTHER,1),"second")
        v.save();advanceUntilIdle();assertTrue(v.state.value.conflict)
        assertEquals("4000",v.state.value.amountInput);assertEquals(3000L,f.payments.get(c.id)!!.amountWon)
        v.load();advanceUntilIdle();assertFalse(v.state.value.conflict);assertEquals("3000",v.state.value.amountInput)
    }
    @Test fun missingTripAndMismatchedOwnerAreNotEditable()=runTest {
        val f=Fixture();assertTrue(vm(f,999).state.value.notFound)
        val id=f.seedActiveTrip();val c=f.addCost(id,CostInput(CostKind.OTHER,1),"first").success()
        assertTrue(vm(f,id,c.id+99).state.value.notFound)
    }
    @Test fun duplicateTapAfterSaveBeforeNavigationCannotCreateSecondCost()=runTest {
        val f=Fixture();val id=f.seedActiveTrip();val v=vm(f,id)
        v.onKindChange(CostKind.PARKING);v.onAmountChange("3000")
        v.save();advanceUntilIdle();v.save();advanceUntilIdle()
        assertEquals(1,f.payments.costs.size)
    }
}
