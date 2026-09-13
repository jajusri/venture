package com.jajusri.venture.feature.masterdata.presentation

import java.text.DateFormat
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Date

/**
 * Honest account-data freshness copy. Connected != fresh. Missing timestamps stay "not recorded"
 * rather than inventing a live/now claim.
 */
fun accountDataFreshnessLine(
    dataFreshnessAt: String?,
    isRefreshing: Boolean,
    isOnline: Boolean,
    hasContent: Boolean,
): String? {
    if (!hasContent) return null
    val synced = formatAccountDataFreshness(dataFreshnessAt)
    val base = if (synced != null) {
        "Last successful sync: $synced"
    } else {
        "Last successful sync: not recorded"
    }
    return buildList {
        add(base)
        if (isRefreshing) add("Updating")
        if (!isOnline) add("Offline — showing saved data")
    }.joinToString(" · ")
}

fun formatAccountDataFreshness(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val epochMillis = parseFreshnessEpochMillis(value) ?: return value
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
}

private fun parseFreshnessEpochMillis(value: String): Long? {
    value.toLongOrNull()?.let { numeric ->
        return if (numeric < 1_000_000_000_000L) numeric * 1000L else numeric
    }
    return try {
        Instant.parse(value).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}
