package com.gatita.domain.usecase

import com.gatita.domain.model.*
import com.gatita.domain.repository.*
import java.time.Clock

class SetTripPausedUseCase(private val trips:TripRepository, private val sessions:TripSessionRepository, private val transactions:TransactionRunner,private val clock:Clock) {
    suspend operator fun invoke(tripId:Long,paused:Boolean):DomainResult<Unit> = transactions.run {
        val trip=trips.getTrip(tripId) ?: return@run domainFailure(ErrorCode.TRIP_NOT_FOUND,"Trip not found")
        if(trip.status!=TripStatus.IN_PROGRESS) return@run domainFailure(ErrorCode.TRIP_NOT_IN_PROGRESS,"Only active trips can pause or resume")
        sessions.setPausedAt(tripId,if(paused)clock.millis() else null)
        DomainResult.Success(Unit)
    }
}

/** Remove from user lists without destroying immutable or legacy settlement evidence. */
class HideTripUseCase(private val trips:TripRepository,private val sessions:TripSessionRepository,private val transactions:TransactionRunner) {
    suspend operator fun invoke(tripId:Long):DomainResult<Unit> = transactions.run {
        val trip=trips.getTrip(tripId) ?: return@run domainFailure(ErrorCode.TRIP_NOT_FOUND,"Trip not found")
        if(trip.status==TripStatus.IN_PROGRESS) return@run domainFailure(ErrorCode.TRIP_NOT_FINISHED,"Finish the active trip first")
        sessions.hideFromLists(tripId)
        DomainResult.Success(Unit)
    }
}
