package com.budcom.android.feature.transaction.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val COMMERCIAL_EVENT_CONTENT_TYPE = "application/vnd.budcom.commercial-event+json"
const val COMMERCIAL_EVENT_CONTENT_VERSION = 1

@Serializable
data class CommercialReturnEvent(
    val contractVersion: Int,
    val eventType: String,
    val originBusinessId: String,
    val respondingBusinessId: String,
    val orderId: String,
    val orderVersion: Int,
    val eventId: String,
    val idempotencyKey: String,
    val occurredAtEpochMillis: Long,
    val occurredAtSource: String,
) {
    fun deterministicEncoding(): String {
        require(isBounded())
        return JSON.encodeToString(serializer(), this)
    }

    private fun isBounded() = contractVersion == COMMERCIAL_EVENT_CONTENT_VERSION &&
        eventType == TYPE_ORDER_CONFIRMED &&
        originBusinessId.valid(128) && respondingBusinessId.valid(128) && originBusinessId != respondingBusinessId &&
        orderId.valid(128) && orderVersion > 0 && eventId.valid(128) && idempotencyKey.valid(256) &&
        occurredAtEpochMillis >= 0 && TransactionTimestampSource.entries.any { it.name == occurredAtSource }

    companion object {
        const val TYPE_ORDER_CONFIRMED = "ORDER_CONFIRMED"
        private val JSON = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }

        fun parse(canonical: String): CommercialReturnEvent? {
            if (canonical.toByteArray(Charsets.UTF_8).size > 4096) return null
            val event = runCatching { JSON.decodeFromString(serializer(), canonical) }.getOrNull() ?: return null
            if (!event.isBounded() || event.deterministicEncoding() != canonical) return null
            return event
        }
    }
}

private fun String.valid(max: Int) = isNotBlank() && length <= max && none { it.code < 0x20 }
