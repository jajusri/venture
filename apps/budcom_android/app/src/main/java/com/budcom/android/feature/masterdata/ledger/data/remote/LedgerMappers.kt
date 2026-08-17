package com.budcom.android.feature.masterdata.ledger.data.remote

import com.budcom.android.feature.masterdata.domain.MasterDataBrowserDefaults
import com.budcom.android.feature.masterdata.ledger.domain.model.AmountSide
import com.budcom.android.feature.masterdata.ledger.domain.model.Ledger
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerContactDetails
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerDataQuality
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerSortBy
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerSortDirection
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatus
import com.budcom.android.feature.masterdata.ledger.domain.model.MoneyAmount

internal fun LedgerListResponseDto.toDomain(): LedgerPage = LedgerPage(
    items = items.filterNot { it.isDeleted }.map { it.toDomain() },
    page = pagination.page,
    pageSize = pagination.pageSize,
    totalItems = pagination.totalItems,
    totalPages = pagination.totalPages,
    dataFreshnessAt = dataFreshnessAt,
)

internal fun LedgerSummaryDto.toDomain(): Ledger = Ledger(
    id = id,
    name = name,
    alias = alias?.takeIf { it.isNotBlank() },
    parentGroup = parentGroup?.takeIf { it.isNotBlank() },
    status = status.toLedgerStatus(),
    closingBalance = closingBalance?.toDomain(),
    dataQuality = dataQuality.toDataQuality(),
    syncedAt = syncedAt,
)

internal fun NormalizedAmountDto.toDomain(): MoneyAmount = MoneyAmount(
    amount = amount,
    currencyCode = currencyCode,
    side = when (side.lowercase()) {
        "cr" -> AmountSide.Cr
        else -> AmountSide.Dr
    },
)

internal fun String.toLedgerStatus(): LedgerStatus = when (lowercase()) {
    "active" -> LedgerStatus.Active
    "inactive" -> LedgerStatus.Inactive
    "reserved" -> LedgerStatus.Reserved
    else -> LedgerStatus.Unknown
}

internal fun String.toDataQuality(): LedgerDataQuality = when (lowercase()) {
    "complete" -> LedgerDataQuality.Complete
    "partial" -> LedgerDataQuality.Partial
    "invalid" -> LedgerDataQuality.Invalid
    else -> LedgerDataQuality.Partial
}

internal fun LedgerQuery.toApiSortBy(): String = when (sortBy) {
    LedgerSortBy.Name -> "name"
    LedgerSortBy.ParentGroup -> "parentGroup"
    LedgerSortBy.ClosingBalance -> "closingBalance"
    LedgerSortBy.SyncedAt -> "syncedAt"
}

internal fun LedgerQuery.toApiSortDirection(): String = when (sortDirection) {
    LedgerSortDirection.Asc -> "asc"
    LedgerSortDirection.Desc -> "desc"
}

internal fun LedgerQuery.normalizedText(): String? =
    text?.trim()?.takeIf { it.isNotEmpty() }?.take(MasterDataBrowserDefaults.MAX_QUERY_LENGTH)

internal fun LedgerDetailDto.toContactDetails(): LedgerContactDetails = LedgerContactDetails(
    ledgerId = id,
    mobile = contact?.mobile?.takeIf { it.isNotBlank() },
    email = contact?.email?.takeIf { it.isNotBlank() },
    address = mailing?.address?.takeIf { it.isNotBlank() },
    state = mailing?.state?.takeIf { it.isNotBlank() },
    pincode = mailing?.pincode?.takeIf { it.isNotBlank() },
    gstin = gst?.gstin?.takeIf { it.isNotBlank() },
)
