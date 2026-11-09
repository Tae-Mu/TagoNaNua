package com.gatita.domain.calculator

import com.gatita.*
import com.gatita.domain.model.*
import com.gatita.domain.usecase.*
import com.gatita.feature.Fixture
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PaymentEdgeCasesTest {
    private fun cost(amount:Long, included:Boolean=true, id:Long=1)=Cost(id,1,"r$id",CostKind.OTHER,amount,null,null,null,included,1,1)
    @Test fun `all supported people and small remainders conserve total`() {
        for(people in 1..8) for(include in listOf(true,false)) for(total in listOf(1L,2,7,8,9,3685,55400,10000000)) {
            val shares=PaymentSettlementCalculator.calculateShares(total,people,include,"나")
            assertEquals(total,shares.sumOf{it.amountWon});assertEquals(people,shares.size)
            assertTrue(shares.all{it.amountWon>=0})
            if(!include && people>1)assertEquals(0L,shares.first().amountWon)
            val payers=if(include || people==1)shares else shares.drop(1)
            assertTrue(payers.maxOf{it.amountWon}-payers.minOf{it.amountWon}<=1)
        }
    }
    @Test fun `money and fuel price boundaries`() {
        for(amount in listOf(-1L,0,10000001,Long.MAX_VALUE)) assertTrue(CostValidator.validate(CostInput(CostKind.OTHER,amount)) is DomainResult.Failure)
        for(amount in listOf(1L,10000000)) assertTrue(CostValidator.validate(CostInput(CostKind.OTHER,amount)) is DomainResult.Success)
        for(price in listOf("0","-1","10000","1890.123")) assertTrue(CostValidator.validate(CostInput(CostKind.FUEL,50000,unitPriceWonPerLiter=d(price))) is DomainResult.Failure)
        for(price in listOf("1","9999","1890.12")) assertTrue(CostValidator.validate(CostInput(CostKind.FUEL,50000,unitPriceWonPerLiter=d(price))) is DomainResult.Success)
    }
    @Test fun `non fuel strips stale fuel fields and description is optional`() {
        val value=CostValidator.validate(CostInput(CostKind.PARKING,1,"  ",d("1890"),java.time.LocalDate.now())).success()
        assertNull(value.description);assertNull(value.unitPriceWonPerLiter);assertNull(value.paidOn)
    }
    @Test fun `description limits count unicode and reject controls`() {
        assertTrue(CostValidator.validate(CostInput(CostKind.OTHER,1,"가".repeat(101))) is DomainResult.Failure)
        assertTrue(CostValidator.validate(CostInput(CostKind.OTHER,1,"a\u0000b")) is DomainResult.Failure)
        assertTrue(CostValidator.validate(CostInput(CostKind.OTHER,1,"😀".repeat(100))) is DomainResult.Success)
    }
    @Test fun `overflow fails rather than wrapping and excluded costs remain in ledger`() {
        assertTrue(PaymentSettlementCalculator.summarize(finishedTrip(),listOf(cost(Long.MAX_VALUE),cost(1,id=2))) is DomainResult.Failure)
        val ledger=PaymentSettlementCalculator.summarize(finishedTrip(),listOf(cost(100),cost(200,false,2))).success()
        assertEquals(300L,ledger.registeredTotalWon);assertEquals(100L,ledger.includedTotalWon);assertEquals(200L,ledger.excludedTotalWon)
    }
    @Test fun `fuel liters never replace actual payment amount`() {
        assertEquals(d("26.455"),FuelLiters.calculate(50000,d("1890")))
        val c=cost(50000).copy(kind=CostKind.FUEL,unitPriceWonPerLiter=d("1890"))
        assertEquals(50000L,PaymentSettlementCalculator.preview(finishedTrip(),listOf(c),emptyList(),null).success().totalWon)
    }
    @Test fun `duplicate add is idempotent and reused id with changed payload is rejected`()=runTest {
        val f=Fixture();val id=f.seedActiveTrip();val input=CostInput(CostKind.PARKING,3000)
        val first=f.addCost(id,input,"one").success();assertEquals(first,f.addCost(id,input,"one").success())
        assertTrue(f.addCost(id,input.copy(amountWon=4000),"one") is DomainResult.Failure)
        assertEquals(1,f.payments.costs.size)
    }
    @Test fun `snapshot includes excluded cost and locks mutations with retry idempotency`()=runTest {
        val f=Fixture();val id=f.seedFinishedTrip();val c=f.addCost(id,CostInput(CostKind.OTHER,100),"one").success()
        val excluded=f.addCost(id,CostInput(CostKind.OTHER,200),"two").success();f.setIncluded(id,excluded.id,false)
        val rev=f.trips.getTrip(id)!!.revision;val command=ConfirmPaymentCommand(id,"confirm",rev,"민수")
        val snapshot=f.confirmPayment(command).success()
        assertEquals(100L,snapshot.totalWon);assertEquals(2,snapshot.costs.size)
        assertEquals(snapshot,f.confirmPayment(command).success())
        assertTrue(f.addCost(id,CostInput(CostKind.TOLL,1),"three") is DomainResult.Failure)
        assertTrue(f.deleteCost(id,c.id) is DomainResult.Failure)
        assertTrue(f.setIncluded(id,c.id,false) is DomainResult.Failure)
    }
}
