package com.budcom.android.feature.voucher.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class VoucherListEnvelopeDto(
    val schemaVersion: String? = null,
    val data: VoucherListDataDto,
)

@Serializable
data class VoucherListDataDto(
    val companyId: String,
    val items: List<VoucherPublicRecordDto> = emptyList(),
    val pagination: VoucherPaginationDto,
)

@Serializable
data class VoucherPaginationDto(
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
)

@Serializable
data class VoucherPublicRecordDto(
    val id: String,
    val date: String,
    val type: String,
    val number: String? = null,
    val partyName: String? = null,
    val referenceNumber: String? = null,
    val amount: VoucherAmountDto? = null,
    val status: String,
    val dataQuality: String,
)

@Serializable
data class VoucherAmountDto(
    val value: String,
    val side: String? = null,
)

@Serializable
data class VoucherDetailsEnvelopeDto(
    val schemaVersion: String? = null,
    val data: VoucherDetailsDataDto,
)

@Serializable
data class VoucherDetailsDataDto(
    val companyId: String,
    val voucher: VoucherPublicDetailsDto? = null,
)

@Serializable
data class VoucherPublicDetailsDto(
    val id: String,
    val date: String,
    val type: String,
    val number: String? = null,
    val partyName: String? = null,
    val referenceNumber: String? = null,
    val amount: VoucherAmountDto? = null,
    val status: String,
    val dataQuality: String,
    val effectiveDate: String? = null,
    val narration: String? = null,
    val ledgerEntries: List<VoucherLedgerEntryDto> = emptyList(),
    val inventoryEntries: List<VoucherInventoryEntryDto> = emptyList(),
)

@Serializable
data class VoucherLedgerEntryDto(
    val lineNumber: Int,
    val ledgerName: String,
    val amount: VoucherAmountDto,
    val isDeemedPositive: Boolean? = null,
)

@Serializable
data class VoucherInventoryEntryDto(
    val lineNumber: Int,
    val itemName: String,
    val quantity: String? = null,
    val rate: String? = null,
    val amount: VoucherAmountDto? = null,
)
