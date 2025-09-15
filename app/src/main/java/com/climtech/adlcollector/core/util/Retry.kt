package com.climtech.adlcollector.core.util

import com.climtech.adlcollector.core.net.NetworkException
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random

suspend fun <T> retryNetwork(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 300,
    maxDelayMs: Long = 5_000,
    isRetriable: (Throwable) -> Boolean = { err ->
        when (err) {
            is NetworkException.Timeout -> true
            is NetworkException.Server -> true                 // 5xx
            is NetworkException.Client -> err.code == 429      // Too Many Requests
            // Optional: treat Offline as non-retriable here; better to queue with WorkManager
            else -> false
        }
    },
    block: suspend () -> Result<T>
): Result<T> {
    var attempt = 1
    var delayMs = initialDelayMs
    while (true) {
        val res = block()
        if (res is Result.Ok) return res
        val e = res.exceptionOrNull() ?: return res
        if (!isRetriable(e) || attempt >= maxAttempts) return res
        val jitter = Random.nextLong(0, delayMs / 2)
        delay(delayMs + jitter)
        delayMs = min(delayMs * 2, maxDelayMs)
        attempt++
    }
}
