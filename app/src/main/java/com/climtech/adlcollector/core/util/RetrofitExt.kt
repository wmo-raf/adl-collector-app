package com.climtech.adlcollector.core.util

import com.climtech.adlcollector.core.net.NetworkException
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.CancellationException
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun <T> Response<T>.asResult(): Result<T> = try {
    if (isSuccessful) {
        val body = body()
        if (body != null) Result.Ok(body) else Result.Err(NetworkException.EmptyBody)
    } else {
        val code = code()
        val text = safeString(errorBody())
        val ex = when (code) {
            401 -> NetworkException.Unauthorized
            403 -> NetworkException.Forbidden
            404 -> NetworkException.NotFound
            in 400..499 -> NetworkException.Client(code, text)
            in 500..599 -> NetworkException.Server(code, text)
            else -> NetworkException.Unknown(IOException("HTTP $code: $text"))
        }
        Result.Err(ex)
    }
} catch (e: CancellationException) {
    throw e // never swallow structured cancellation
} catch (e: UnknownHostException) {
    Result.Err(NetworkException.Offline)
} catch (e: SocketTimeoutException) {
    Result.Err(NetworkException.Timeout)
} catch (e: JsonDataException) {
    Result.Err(NetworkException.Serialization(e))
} catch (e: IOException) {
    Result.Err(NetworkException.Unknown(e))
} catch (e: Throwable) {
    Result.Err(NetworkException.Unknown(e))
}

private fun safeString(body: ResponseBody?): String? =
    try {
        body?.string()?.take(4_096)
    } catch (_: Throwable) {
        null
    }
