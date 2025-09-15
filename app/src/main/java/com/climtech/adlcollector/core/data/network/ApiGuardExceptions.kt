package com.climtech.adlcollector.core.data.network

import java.io.IOException

sealed class ApiGuardIOException(message: String) : IOException(message)

class UnexpectedBodyIOException(val mime: String?, val snippet: String?) :
    ApiGuardIOException("Response is not JSON (mime=$mime)")
