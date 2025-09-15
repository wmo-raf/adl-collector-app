package com.climtech.adlcollector.core.util

sealed interface Result<out T> {
    data class Ok<out T>(val value: T) : Result<T>
    data class Err(val error: Throwable) : Result<Nothing>

    fun onSuccess(block: (T) -> Unit): Result<T> {
        if (this is Ok) block(value)
        return this
    }

    fun onFailure(block: (Throwable) -> Unit): Result<T> {
        if (this is Err) block(error)
        return this
    }

    fun <R> map(transform: (T) -> R): Result<R> =
        when (this) {
            is Ok -> Ok(transform(value))
            is Err -> this
        }

    fun <R> flatMap(transform: (T) -> Result<R>): Result<R> =
        when (this) {
            is Ok -> transform(value)
            is Err -> this
        }


    fun getOrNull(): T? = when (this) {
        is Ok -> value
        is Err -> null
    }

    fun exceptionOrNull(): Throwable? = when (this) {
        is Ok -> null
        is Err -> error
    }
}
