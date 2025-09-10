package com.climtech.adlcollector

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.tenantDataStore by preferencesDataStore(name = "tenant_prefs")
