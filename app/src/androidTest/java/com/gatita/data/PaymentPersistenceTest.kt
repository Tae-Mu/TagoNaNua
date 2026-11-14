package com.gatita.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.gatita.data.local.AppDatabase
import com.gatita.data.repository.*
import com.gatita.domain.model.*
import com.gatita.domain.repository.AppSettings
import com.gatita.domain.usecase.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PaymentPersistenceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="payment-edge-test.db"
    private lateinit var db:AppDatabase
    private val clock=Clock.fixed(Instant.ofEpochMilli(10000),ZoneOffset.UTC)
    private fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).addCallback(AppDatabase.immutabilityCallback).build()
    @Before fun setup(){context.deleteDatabase(name);db=open()}
    @After fun cleanup(){db.close();context.deleteDatabase(name)}
    private fun <T> DomainResult<T>.ok():T=(this as DomainResult.Success).value
    @Test fun settingsPersistAndResetDoesNotRemoveTrips()=runBlocking {
        val tx=RoomTransactionRunner(db);val trips=RoomTripRepository(db)
        val trip=StartTripUseCase(trips,tx)(StartTripCommand(BigDecimal.ZERO,1000,VehicleSource.MANUAL)).ok()
        val settings=RoomSettingsRepository(db.settingsDao())
        SaveSettingsUseCase(settings)(AppSettings("민수",false,"야간")).ok()
        db.close();db=open()
        val restored=RoomSettingsRepository(db.settingsDao())
        assertEquals(AppSettings("민수",false,"야간"),restored.get())
        SaveSettingsUseCase(restored)(AppSettings()).ok()
        assertEquals(AppSettings(),restored.get());assertNotNull(RoomTripRepository(db).getTrip(trip.id))
    }
    @Test fun paymentSnapshotSurvivesRestartAndSqlMutationIsRejected()=runBlocking {
        val tx=RoomTransactionRunner(db);val trips=RoomTripRepository(db);val costs=RoomCostRepository(db);val settlements=RoomPaymentSettlementRepository(db)
        val trip=StartTripUseCase(trips,tx)(StartTripCommand(BigDecimal.ZERO,1000,VehicleSource.MANUAL,people=4,includeDriver=false)).ok()
        val add=AddCostUseCase(trips,costs,tx,clock)
        add(trip.id,CostInput(CostKind.PARKING,55400),"one").ok()
        val excluded=add(trip.id,CostInput(CostKind.OTHER,1000),"two").ok()
        SetCostIncludedUseCase(trips,costs,tx,clock)(trip.id,excluded.id,false).ok()
        CompleteTripUseCase(trips,costs,tx)(CompleteTripCommand(trip.id,10000)).ok()
        val revision=trips.getTrip(trip.id)!!.revision
        val saved=ConfirmPaymentSettlementUseCase(trips,costs,settlements,tx,clock)(ConfirmPaymentCommand(trip.id,"confirm",revision,"민수")).ok()
        assertEquals(listOf(0L,18467L,18467L,18466L),saved.shares.map{it.amountWon})
        assertEquals(2,saved.costs.size)
        assertThrows(android.database.sqlite.SQLiteException::class.java) { db.openHelper.writableDatabase.execSQL("UPDATE costs SET amountWon = 2 WHERE tripId = ?",arrayOf(trip.id)) }
        assertThrows(android.database.sqlite.SQLiteException::class.java) { db.openHelper.writableDatabase.execSQL("DELETE FROM payment_settlements WHERE id = ?",arrayOf(saved.id)) }
        db.close();db=open()
        assertEquals(saved,RoomPaymentSettlementRepository(db).findByTripId(trip.id))
    }
    @Test fun invalidShareSnapshotRollsBackTripAndAllChildren()=runBlocking {
        val tx=RoomTransactionRunner(db);val trips=RoomTripRepository(db);val costs=RoomCostRepository(db)
        val trip=StartTripUseCase(trips,tx)(StartTripCommand(BigDecimal.ZERO,1000,VehicleSource.MANUAL)).ok()
        AddCostUseCase(trips,costs,tx,clock)(trip.id,CostInput(CostKind.OTHER,100),"one").ok()
        CompleteTripUseCase(trips,costs,tx)(CompleteTripCommand(trip.id,10000)).ok()
        val current=trips.getTrip(trip.id)!!
        val snapshot=com.gatita.domain.calculator.PaymentSettlementCalculator.buildSnapshot(current,costs.getCosts(trip.id),emptyList(),"나","confirm",10000).ok()
        val invalid=snapshot.copy(shares=snapshot.shares.map{it.copy(amountWon=0)})
        try { RoomPaymentSettlementRepository(db).saveAndMarkTripSettled(invalid,current.revision);fail("invalid transaction accepted") } catch(e:IllegalStateException) { }
        assertEquals(TripStatus.FINISHED,trips.getTrip(trip.id)!!.status)
        assertNull(RoomPaymentSettlementRepository(db).findByTripId(trip.id))
        assertEquals(1,costs.getCosts(trip.id).size)
    }
    @Test fun pauseResumeAndListRemovalPersistWithoutChangingSettlement()=runBlocking {
        val tx=RoomTransactionRunner(db);val trips=RoomTripRepository(db);val costs=RoomCostRepository(db)
        val trip=StartTripUseCase(trips,tx)(StartTripCommand(BigDecimal.ZERO,1000,VehicleSource.MANUAL)).ok()
        val sessions=RoomTripSessionRepository(db.tripSessionDao())
        val pause=SetTripPausedUseCase(trips,sessions,tx,clock)
        pause(trip.id,true).ok()
        assertTrue(HideTripUseCase(trips,sessions,tx)(trip.id) is DomainResult.Failure)
        db.close();db=open()
        val restored=RoomTripSessionRepository(db.tripSessionDao())
        assertEquals(10000L,restored.observePausedAt(trip.id).first())
        val t=RoomTripRepository(db);val c=RoomCostRepository(db);val transaction=RoomTransactionRunner(db)
        SetTripPausedUseCase(t,restored,transaction,clock)(trip.id,false).ok()
        assertNull(restored.observePausedAt(trip.id).first())
        AddCostUseCase(t,c,transaction,clock)(trip.id,CostInput(CostKind.OTHER,100),"cost").ok()
        CompleteTripUseCase(t,c,transaction)(CompleteTripCommand(trip.id,10000)).ok()
        assertTrue(SetTripPausedUseCase(t,restored,transaction,clock)(trip.id,true) is DomainResult.Failure)
        val settlements=RoomPaymentSettlementRepository(db)
        val saved=ConfirmPaymentSettlementUseCase(t,c,settlements,transaction,clock)(ConfirmPaymentCommand(trip.id,"confirm",t.getTrip(trip.id)!!.revision,"나")).ok()
        val other=StartTripUseCase(t,transaction)(StartTripCommand(BigDecimal.ZERO,20000,VehicleSource.MANUAL)).ok()
        HideTripUseCase(t,restored,transaction)(trip.id).ok()
        assertEquals(listOf(other.id),RoomTripLedgerRepository(db).observeSummaries().first().map { it.tripId })
        assertEquals(saved,settlements.findByTripId(trip.id))
    }
}
