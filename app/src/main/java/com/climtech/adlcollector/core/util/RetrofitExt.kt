package com.climtech.adlcollector.core.util

import retrofit2.Response

fun <T> Response<T>.asResult(): Result<T> = if (isSuccessful) {
    val body = body()
    if (body != null) Result.Ok(body)
    else Result.Err(IllegalStateException("Empty body with HTTP ${code()}"))
} else {
    val msg = errorBody()?.string()?.take(2_000) ?: "HTTP ${code()}"
    Result.Err(IllegalStateException(msg))
}