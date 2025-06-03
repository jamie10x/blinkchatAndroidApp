package com.jamie.blinkchat.core.common

import timber.log.Timber
import java.time.Instant
import java.time.format.DateTimeParseException

fun String.toEpochMillis(): Long? {
    return try {
        Instant.parse(this).toEpochMilli()
    } catch (e: DateTimeParseException) {
        Timber.e(e, "Failed to parse ISO8601 timestamp: $this")
        null
    }
}