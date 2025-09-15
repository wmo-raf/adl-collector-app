package com.climtech.adlcollector.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.climtech.adlcollector.core.data.db.StationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Query("SELECT * FROM stations WHERE tenantId = :tenantId ORDER BY name ASC")
    fun observeStations(tenantId: String): Flow<List<StationEntity>>

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun upsertAll(entities: List<StationEntity>)

    @Query("DELETE FROM stations WHERE tenantId = :tenantId")
    suspend fun clearForTenant(tenantId: String)
}