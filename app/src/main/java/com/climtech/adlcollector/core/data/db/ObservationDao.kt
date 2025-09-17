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
        WHERE status IN ('QUEUED','FAILED') AND tenantId = :tenantId
        ORDER BY createdAtMs ASC
        LIMIT :limit
    """
    )
    suspend fun nextPending(tenantId: String, limit: Int = 20): List<ObservationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ObservationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ObservationEntity>)

    @Query("UPDATE observations SET status = :status, lastError = :error, updatedAtMs = :updatedAt WHERE obsKey = :key")
    suspend fun markStatus(
        key: String, status: ObservationEntity.SyncStatus, error: String?, updatedAt: Long
    )

    @Query("UPDATE observations SET remoteId = :remoteId, status = 'SYNCED', updatedAtMs = :updatedAt WHERE obsKey = :key")
    suspend fun markSynced(key: String, remoteId: Long?, updatedAt: Long)

    @Query(
        """
    SELECT * FROM observations
    WHERE tenantId = :tenantId
    ORDER BY obsTimeUtcMs DESC
    """
    )
    fun streamAllForTenant(tenantId: String): Flow<List<ObservationEntity>>
}