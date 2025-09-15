package com.climtech.adlcollector.core.net


sealed class NetworkException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    object Unauthorized : NetworkException("Session expired")
    object Forbidden : NetworkException("You don’t have permission")
    object NotFound : NetworkException("Not found")
    data class Client(val code: Int, val body: String?) : NetworkException("Client error ($code)")
    data class Server(val code: Int, val body: String?) : NetworkException("Server error ($code)")
    object EmptyBody : NetworkException("Empty response")
    object Offline : NetworkException("You’re offline")
    object Timeout : NetworkException("Network timeout")
    class Serialization(cause: Throwable) : NetworkException("Bad response format", cause)
    class UnexpectedBody(val mime: String?, val snippet: String?) :
        NetworkException("Response is not JSON")

    class LoginRedirect(val location: String?) : NetworkException("Redirected to login")

    class Unknown(cause: Throwable) : NetworkException("Unexpected error", cause)
}
