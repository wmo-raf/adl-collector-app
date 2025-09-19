package com.climtech.adlcollector.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [StationEntity::class, StationDetailEntity::class, ObservationEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun stationDetailDao(): StationDetailDao
    abstract fun observationDao(): ObservationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "adl.db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}