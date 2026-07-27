package com.budcom.android.feature.masterdata.ledger.data.remote

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
