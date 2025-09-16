package com.climtech.adlcollector.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached detail for a single station (per tenant).
 * We store the full server JSON in [detailJson] so the DTO can evolve
 * without schema churn, and we denormalize a few fields for quick display.
 */
@Entity(tableName = "station_details")
data class StationDetailEntity(
    @PrimaryKey val stationKey: String,        // tenantId:stationId
    val tenantId: String,
    val stationId: Long,
    val name: String,
    val timezone: String,
    val scheduleMode: String,
    val detailJson: String,             // full DTO JSON (verbatim)
    val updatedAtMs: Long               // when we wrote this cache
)
