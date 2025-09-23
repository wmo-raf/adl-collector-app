package com.climtech.adlcollector.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservationDao {

    @Query(
        """
        SELECT * FROM observations
        WHERE tenantId = :tenantId AND stationId = :stationId
        ORDER BY obsTimeUtcMs DESC
        """
    )
    fun streamForStation(tenantId: String, stationId: Long): Flow<List<ObservationEntity>>

    @Query(
        """
        SELECT * FROM observations
        WHERE tenantId = :tenantId
        ORDER BY obsTimeUtcMs DESC
        """
    )
    fun streamAllForTenant(tenantId: String): Flow<List<ObservationEntity>>

    @Query(
        """
        SELECT * FROM observations
        WHERE tenantId = :tenantId 
        AND obsTimeUtcMs BETWEEN :startMs AND :endMs
        ORDER BY obsTimeUtcMs DESC
        """
    )
    suspend fun getObservationsInDateRange(
        tenantId: String,
        startMs: Long,
        endMs: Long
    ): List<ObservationEntity>

    @Query(
        """
        SELECT * FROM observations
        WHERE status IN ('QUEUED','FAILED') AND tenantId = :tenantId
        ORDER BY createdAtMs ASC
        LIMIT :limit
        """
    )
    suspend fun nextPending(tenantId: String, limit: Int = 20): List<ObservationEntity>

    @Query(
        """
        SELECT * FROM observations
        WHERE status = 'FAILED' AND tenantId = :tenantId
        ORDER BY updatedAtMs DESC
        LIMIT :limit
        """
    )
    suspend fun getFailedObservations(tenantId: String, limit: Int = 100): List<ObservationEntity>

    @Query(
        """
        SELECT COUNT(*) FROM observations
        WHERE status = :status AND tenantId = :tenantId
        """
    )
    suspend fun countByStatus(tenantId: String, status: ObservationEntity.SyncStatus): Int

    @Query(
        """
        SELECT COUNT(*) FROM observations
        WHERE status IN ('QUEUED', 'UPLOADING', 'FAILED') AND tenantId = :tenantId
        """
    )
    suspend fun countPending(tenantId: String): Int

    @Query(
        """
        SELECT * FROM observations
        WHERE status = 'UPLOADING' AND updatedAtMs < :timeoutMs AND tenantId = :tenantId
        """
    )
    suspend fun getStuckUploading(tenantId: String, timeoutMs: Long): List<ObservationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ObservationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ObservationEntity>)

    @Query(
        """
        UPDATE observations 
        SET status = :status, lastError = :error, updatedAtMs = :updatedAt 
        WHERE obsKey = :key
        """
    )
    suspend fun markStatus(
        key: String,
        status: ObservationEntity.SyncStatus,
        error: String?,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE observations 
        SET remoteId = :remoteId, status = 'SYNCED', lastError = NULL, updatedAtMs = :updatedAt 
        WHERE obsKey = :key
        """
    )
    suspend fun markSynced(key: String, remoteId: Long?, updatedAt: Long)

    @Query(
        """
        UPDATE observations 
        SET status = 'QUEUED', lastError = NULL, updatedAtMs = :updatedAt 
        WHERE status = 'FAILED' AND tenantId = :tenantId
        """
    )
    suspend fun resetFailedToQueued(tenantId: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE observations 
        SET status = 'QUEUED', lastError = 'Upload timeout - retrying', updatedAtMs = :updatedAt 
        WHERE status = 'UPLOADING' AND updatedAtMs < :timeoutMs AND tenantId = :tenantId
        """
    )
    suspend fun resetStuckUploading(tenantId: String, timeoutMs: Long, updatedAt: Long): Int

    @Query(
        """
        DELETE FROM observations 
        WHERE status = 'SYNCED' AND tenantId = :tenantId AND obsTimeUtcMs < :beforeMs
        """
    )
    suspend fun cleanupOldSynced(tenantId: String, beforeMs: Long): Int

    @Query(
        """
        DELETE FROM observations 
        WHERE tenantId = :tenantId
        """
    )
    suspend fun clearForTenant(tenantId: String): Int

    @Query(
        """
        SELECT * FROM observations
        WHERE obsKey = :key
        LIMIT 1
        """
    )
    suspend fun getByKey(key: String): ObservationEntity?

    @Query(
        """
        SELECT DISTINCT tenantId FROM observations
        WHERE status IN ('QUEUED', 'FAILED')
        """
    )
    suspend fun getTenantsWithPendingObservations(): List<String>

    /**
     * Get upload statistics for monitoring
     */
    @Query(
        """
        SELECT 
            status,
            COUNT(*) as count
        FROM observations 
        WHERE tenantId = :tenantId
        GROUP BY status
        """
    )
    suspend fun getStatusCounts(tenantId: String): List<StatusCount>

    /**
     * Get observations that have been retried multiple times
     */
    @Query(
        """
        SELECT * FROM observations
        WHERE status = 'FAILED' 
        AND tenantId = :tenantId
        AND (lastError LIKE '%retry%' OR lastError LIKE '%attempt%')
        ORDER BY updatedAtMs DESC
        LIMIT :limit
        """
    )
    suspend fun getRepeatedFailures(tenantId: String, limit: Int = 50): List<ObservationEntity>

    /**
     * Get the oldest pending observation for a tenant
     */
    @Query(
        """
        SELECT * FROM observations
        WHERE status IN ('QUEUED', 'FAILED') AND tenantId = :tenantId
        ORDER BY createdAtMs ASC
        LIMIT 1
        """
    )
    suspend fun getOldestPending(tenantId: String): ObservationEntity?

    /**
     * Check if there are any observations for a specific time period
     */
    @Query(
        """
        SELECT COUNT(*) > 0 FROM observations
        WHERE tenantId = :tenantId 
        AND stationId = :stationId 
        AND obsTimeUtcMs BETWEEN :startMs AND :endMs
        """
    )
    suspend fun hasObservationsInRange(
        tenantId: String,
        stationId: Long,
        startMs: Long,
        endMs: Long
    ): Boolean
}

/**
 * Data class for status count queries
 */
data class StatusCount(
    val status: ObservationEntity.SyncStatus,
    val count: Int
)