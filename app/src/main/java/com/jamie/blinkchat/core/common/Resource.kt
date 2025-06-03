package com.jamie.blinkchat.core.common

/**
 * A generic class that holds a value with its loading status.
 * @param <T>
 */
sealed class Resource<T>(
    val data: T? = null,
    val message: String? = null, // For error messages or informational messages
    val errorCode: Int? = null // Optional: for HTTP error codes or custom error codes
) {
    class Success<T>(data: T?) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null, errorCode: Int? = null) : Resource<T>(data, message, errorCode)
    class Loading<T>(data: T? = null) : Resource<T>(data) // Optional: can carry data during loading
}