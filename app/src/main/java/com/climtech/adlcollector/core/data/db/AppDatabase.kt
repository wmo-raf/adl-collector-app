package com.climtech.adlcollector.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.climtech.adlcollector.core.data.db.StationDao
import com.climtech.adlcollector.core.data.db.StationEntity

@Database(entities = [StationEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao

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