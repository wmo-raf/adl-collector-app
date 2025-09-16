package com.climtech.adlcollector.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AdlApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        Log.d("AdlApp", "AdlApp onCreate() called")
    }

    override val workManagerConfiguration: Configuration
        get() {
            Log.d("AdlApp", "Setting up WorkManager with HiltWorkerFactory")
            return Configuration.Builder().setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(Log.DEBUG).build()
        }
}
