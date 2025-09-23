package com.climtech.adlcollector.core.util

import android.util.Log
import com.climtech.adlcollector.BuildConfig

object Logger {
    private const val TAG_PREFIX = "ADL_"

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d("$TAG_PREFIX$tag", message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.e("$TAG_PREFIX$tag", message, throwable)
            } else {
                Log.e("$TAG_PREFIX$tag", message)
            }
        }
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.i("$TAG_PREFIX$tag", message, throwable)
            } else {
                Log.i("$TAG_PREFIX$tag", message)
            }
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.w("$TAG_PREFIX$tag", message, throwable)
            } else {
                Log.w("$TAG_PREFIX$tag", message)
            }
        }
    }
}