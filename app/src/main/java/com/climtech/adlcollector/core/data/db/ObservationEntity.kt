package com.climtech.adlcollector.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Offline-first queue + local history for observations.
 *
 * @param key unique per-tenant+station+obsTime (millis) to simplify upserts
 * @param status LOCAL state machine for sync (QUEUED->UPLOADING->SYNCED / FAILED)
 */
@Entity(tableName = "observations")
data class ObservationEntity(
    @PrimaryKey val obsKey: String,        // tenantId:stationId:obsTimeUtcMs
    val tenantId: String,
    val stationId: Long,
    val stationName: String,
    val timezone: String,               // IANA tz from station detail
    val obsTimeUtcMs: Long,             // obs time normalized to UTC millis
    val createdAtMs: Long,              // local client creation time
    val updatedAtMs: Long,
    val late: Boolean,                  // computed by schedule policy
    val locked: Boolean,                // computed by schedule policy
    val scheduleMode: String,           // "fixed_local" | "windowed_only"
    val payloadJson: String,            // JSON blob of submitted values
    val status: SyncStatus = SyncStatus.QUEUED,
    val remoteId: Long? = null,         // set once server accepts
    val lastError: String? = null       // populated on FAILED
) {
    enum class SyncStatus { QUEUED, UPLOADING, SYNCED, FAILED }
}