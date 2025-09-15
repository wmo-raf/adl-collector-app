package com.climtech.adlcollector.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Query("SELECT * FROM stations WHERE tenantId = :tenantId ORDER BY name ASC")
    fun observeStations(tenantId: String): Flow<List<StationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<StationEntity>)

    @Query("DELETE FROM stations WHERE tenantId = :tenantId")
    suspend fun clearForTenant(tenantId: String)

    @androidx.room.Transaction
    suspend fun replaceForTenant(tenantId: String, entities: List<StationEntity>) {
        clearForTenant(tenantId)
        if (entities.isNotEmpty()) upsertAll(entities)
    }
}