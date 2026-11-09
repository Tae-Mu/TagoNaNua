package com.gatita

import com.gatita.domain.model.*
import com.gatita.domain.repository.*
import kotlinx.coroutines.flow.flow

/** Unit tests exercise use cases; actual constraints and rollback are tested with Room. */
class PaymentFakes(val store: FakeStore): CostRepository, TripLedgerRepository, EmptyTripRepository {
    val costs = linkedMapOf<Long,Cost>()
    val payments = mutableListOf<PaymentSettlement>()
    private fun bump(id:Long) { store.trips[id]?.let { store.trips[id]=it.copy(revision=it.revision+1) } }
    override suspend fun insert(cost:Cost):Cost = cost.copy(id=store.nextId++).also { costs[it.id]=it; bump(it.tripId) }
    override suspend fun update(cost:Cost):Boolean { if(cost.id !in costs)return false; costs[cost.id]=cost; bump(cost.tripId); return true }
    override suspend fun setIncluded(costId:Long,included:Boolean,updatedAtMillis:Long):Boolean = costs[costId]?.let { update(it.copy(included=included,updatedAtMillis=updatedAtMillis)) } ?: false
    override suspend fun delete(costId:Long,updatedAtMillis:Long):Boolean = costs.remove(costId)?.let { bump(it.tripId);true } ?: false
    override suspend fun get(costId:Long)=costs[costId]
    // Kotlin cannot implement both repositories' differently typed findByRequestId; payment adapter below.
    override suspend fun findByRequestId(requestId:String):Cost? = costs.values.firstOrNull { it.requestId==requestId }
    override suspend fun getCosts(tripId:Long)=costs.values.filter { it.tripId==tripId }
    override fun observeTripWithCosts(tripId:Long)=flow { emit(store.trips[tripId]?.let { TripWithCosts(it,getCosts(tripId)) }) }
    override suspend fun updateSplitPolicy(tripId:Long,people:Int,includeDriver:Boolean,expectedRevision:Long,updatedAtMillis:Long):Boolean {
        val trip=store.trips[tripId] ?: return false
        if(trip.status!=TripStatus.FINISHED || trip.revision!=expectedRevision)return false
        store.trips[tripId]=trip.copy(people=people,includeDriver=includeDriver,revision=trip.revision+1);return true
    }
    override fun observeSummaries()=flow { emit(store.trips.values.map { t -> val c=getCosts(t.id)
        TripSummary(t.id,t.name,t.tripDate,t.status,t.source,t.people,t.includeDriver,t.revision,t.startedAtMillis,t.endedAtMillis,t.endOdometerKm?.subtract(t.startOdometerKm),c.size,c.count{it.included},c.sumOf{it.amountWon},c.filter{it.included}.sumOf{it.amountWon},null)
    }) }
    override suspend fun deleteIfEmptyActive(tripId:Long):Boolean {
        val t=store.trips[tripId] ?: return false
        if(t.status!=TripStatus.IN_PROGRESS || store.segments.any{it.tripId==tripId} || getCosts(tripId).isNotEmpty())return false
        store.trips.remove(tripId);return true
    }
    val settlements = object: PaymentSettlementRepository {
        override suspend fun findByRequestId(requestId:String)=payments.firstOrNull{it.requestId==requestId}
        override suspend fun findByTripId(tripId:Long)=payments.firstOrNull{it.tripId==tripId}
        override suspend fun saveAndMarkTripSettled(settlement:PaymentSettlement,expectedTripRevision:Long):PaymentSettlement {
            val t=checkNotNull(store.trips[settlement.tripId]);check(t.status==TripStatus.FINISHED && t.revision==expectedTripRevision)
            check(payments.none{it.tripId==t.id || it.requestId==settlement.requestId})
            val saved=settlement.copy(id=store.nextId++);payments+=saved;store.trips[t.id]=t.copy(status=TripStatus.SETTLED,revision=t.revision+1);return saved
        }
    }
}
