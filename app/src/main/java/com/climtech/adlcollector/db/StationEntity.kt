package com.climtech.adlcollector.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val key: String,     // tenantId:stationId (composite)
    val tenantId: String,
    val stationId: Long,
    val name: String,
    val updatedAt: Long
)