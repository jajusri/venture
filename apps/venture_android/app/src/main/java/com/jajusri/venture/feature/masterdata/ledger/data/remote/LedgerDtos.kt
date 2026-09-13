package com.jajusri.venture.feature.masterdata.ledger.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class LedgerListResponseDto(
    val schemaVersion: String? = null,
    val dataFreshnessAt: String? = null,
    val items: List<LedgerSummaryDto> = emptyList(),
    val pagination: LedgerPaginationDto,
)

@Serializable
data class LedgerPaginationDto(
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
)

@Serializable
data class LedgerSummaryDto(
    val id: String,
    val name: String,
    val normalizedName: String? = null,
    val alias: String? = null,
    val parentGroup: String? = null,
    val status: String,
    val openingBalance: NormalizedAmountDto? = null,
    val closingBalance: NormalizedAmountDto? = null,
    val balanceNature: String? = null,
    val guid: String? = null,
    val alterId: String? = null,
    val masterId: String? = null,
    val identitySource: String? = null,
    val dataQuality: String,
    val isBillWiseOn: Boolean? = null,
    val isDeleted: Boolean = false,
    val syncedAt: String,
)

@Serializable
data class NormalizedAmountDto(
    val amount: String,
    val currencyCode: String,
    val side: String,
)

/**
 * Response envelope for `GET /ledgers/{id}` — the fuller `LedgerDetails` shape. Only the
 * contact-compatible fields (mailing/contact/gst) are modeled here; every other `LedgerDetails`
 * field the Connector returns is intentionally ignored (`ignoreUnknownKeys = true` on the shared
 * [kotlinx.serialization.json.Json] instance makes this safe).
 */
@Serializable
data class LedgerDetailEnvelopeDto(
    val schemaVersion: String? = null,
    val dataFreshnessAt: String? = null,
    val ledger: LedgerDetailDto,
)

@Serializable
data class LedgerDetailDto(
    val id: String,
    val name: String,
    val guid: String? = null,
    val mailing: LedgerMailingDto? = null,
    val contact: LedgerContactDto? = null,
    val gst: LedgerGstDto? = null,
)

@Serializable
data class LedgerMailingDto(
    val mailingName: String? = null,
    val address: String? = null,
    val state: String? = null,
    val country: String? = null,
    val pincode: String? = null,
)

@Serializable
data class LedgerContactDto(
    val email: String? = null,
    val phone: String? = null,
    val mobile: String? = null,
)

@Serializable
data class LedgerGstDto(
    val gstin: String? = null,
    val registrationType: String? = null,
    val applicableFrom: String? = null,
)

/**
 * Response envelope for `POST /sync/ledgers/contact-details` — the manually-triggered, occasional
 * bulk fetch of mailing/contact/GST fields for every ledger in one Tally round-trip (Connect
 * address/email/GSTIN auto-population). Deliberately separate from [LedgerDetailEnvelopeDto]
 * above, which is one ledger at a time.
 */
@Serializable
data class LedgerContactDetailsBulkResponseDto(
    val schemaVersion: String? = null,
    val requestedAt: String? = null,
    val durationMs: Long = 0,
    val ledgerCount: Int = 0,
    val updatedCount: Int = 0,
    val skippedCount: Int = 0,
    val items: List<LedgerContactDetailsBulkItemDto> = emptyList(),
)

@Serializable
data class LedgerContactDetailsBulkItemDto(
    val ledgerId: String,
    val mobile: String? = null,
    val email: String? = null,
    val address: String? = null,
    val state: String? = null,
    val pincode: String? = null,
    val gstin: String? = null,
)
