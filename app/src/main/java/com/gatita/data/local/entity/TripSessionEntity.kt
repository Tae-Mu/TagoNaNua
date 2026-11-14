package com.gatita.data.local.entity

import androidx.room.*

@Entity(tableName="trip_sessions", foreignKeys=[ForeignKey(entity=TripEntity::class,parentColumns=["id"],childColumns=["tripId"],onDelete=ForeignKey.CASCADE)])
data class TripSessionEntity(@PrimaryKey val tripId:Long,val pausedAt:Long?, val hidden:Boolean=false)
