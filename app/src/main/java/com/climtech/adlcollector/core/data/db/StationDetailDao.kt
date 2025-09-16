package com.climtech.adlcollector.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDetailDao {

    @Query("SELECT * FROM station_details WHERE stationKey = :key LIMIT 1")
    fun observeByKey(key: String): Flow<StationDetailEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StationDetailEntity)

    @Query("DELETE FROM station_details WHERE tenantId = :tenantId")
    suspend fun clearForTenant(tenantId: String)
}