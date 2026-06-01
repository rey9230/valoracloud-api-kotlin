package com.valoracloud.api.common.utils

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Formats a date into a human-readable string.
 *
 * Output examples:
 *  - en: "May 28, 2026 at 10:30 AM UTC"
 *  - es: "28 de mayo de 2026 a las 10:30 AM UTC"
 */
fun formatDate(date: Instant, language: String = "en"): String {
    val locale = if (language == "es") Locale("es", "ES") else Locale.US
    val formatter = DateTimeFormatter
        .ofPattern("MMMM d, yyyy 'at' hh:mm a 'UTC'", locale)
        .withZone(ZoneId.of("UTC"))
    val formatted = formatter.format(date)
    return if (language == "es") formatted.replace(" at ", " a las ") else formatted
}

/** Returns the current UTC time formatted as a human-readable string. */
fun formatNow(language: String = "en"): String = formatDate(Instant.now(), language)
