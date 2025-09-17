package com.climtech.adlcollector.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.tenantDataStore: DataStore<Preferences> by preferencesDataStore(name = "tenant_prefs")