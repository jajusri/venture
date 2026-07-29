package com.budcom.android.feature.voucher.data.remote

import com.budcom.android.feature.voucher.domain.model.VoucherDataQuality
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherIdentity
import com.budcom.android.feature.voucher.domain.model.VoucherInventoryLine
import com.budcom.android.feature.voucher.domain.model.VoucherLedgerLine
import com.budcom.android.feature.voucher.domain.model.VoucherMoney
import com.budcom.android.feature.voucher.domain.model.VoucherMoneySide
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherSort
import com.budcom.android.feature.voucher.domain.model.VoucherSortDirection
import com.budcom.android.feature.voucher.domain.model.VoucherSortField
import com.budcom.android.feature.voucher.domain.model.VoucherStatus
import com.budcom.android.feature.voucher.domain.model.VoucherSummary

internal fun VoucherListEnvelopeDto.toDomain(): VoucherPage = data.toDomain()

internal fun VoucherListDataDto.toDomain(): VoucherPage = VoucherPage(
    companyId = companyId,
    items = items.map { it.toDomain() },
    page = pagination.page,
    pageSize = pagination.pageSize,
    totalItems = pagination.totalItems,
    totalPages = pagination.totalPages,
)

internal fun VoucherPublicRecordDto.toDomain(): VoucherSummary = VoucherSummary(
    identity = VoucherIdentity(id = id),
    date = date,
    type = type,
    number = number?.takeIf { it.isNotBlank() },
    partyName = partyName?.takeIf { it.isNotBlank() },
    referenceNumber = referenceNumber?.takeIf { it.isNotBlank() },
    amount = amount?.toDomain(),
    status = status.toVoucherStatus(),
    dataQuality = dataQuality.toVoucherDataQuality(),
)

internal fun VoucherPublicDetailsDto.toDomain(): VoucherDetails = VoucherDetails(
    summary = VoucherSummary(
        identity = VoucherIdentity(id = id),
        date = date,
        type = type,
        number = number?.takeIf { it.isNotBlank() },
        partyName = partyName?.takeIf { it.isNotBlank() },
        referenceNumber = referenceNumber?.takeIf { it.isNotBlank() },
        amount = amount?.toDomain(),
        status = status.toVoucherStatus(),
        dataQuality = dataQuality.toVoucherDataQuality(),
    ),
    effectiveDate = effectiveDate?.takeIf { it.isNotBlank() },
    narration = narration?.takeIf { it.isNotBlank() },
    ledgerEntries = ledgerEntries.map { it.toDomain() },
    inventoryEntries = inventoryEntries.map { it.toDomain() },
)

internal fun VoucherLedgerEntryDto.toDomain(): VoucherLedgerLine = VoucherLedgerLine(
    lineNumber = lineNumber,
    ledgerName = ledgerName,
    amount = amount.toDomain(),
    isDeemedPositive = isDeemedPositive,
)

internal fun VoucherInventoryEntryDto.toDomain(): VoucherInventoryLine = VoucherInventoryLine(
    lineNumber = lineNumber,
    itemName = itemName,
    quantity = quantity?.takeIf { it.isNotBlank() },
    rate = rate?.takeIf { it.isNotBlank() },
    amount = amount?.toDomain(),
)

internal fun VoucherAmountDto.toDomain(): VoucherMoney = VoucherMoney(
    value = value,
    side = side.toVoucherMoneySide(),
)

internal fun String?.toVoucherMoneySide(): VoucherMoneySide? = when (this?.lowercase()) {
    "debit" -> VoucherMoneySide.Debit
    "credit" -> VoucherMoneySide.Credit
    else -> null
}

internal fun String.toVoucherStatus(): VoucherStatus = when (lowercase()) {
    "active" -> VoucherStatus.Active
    "cancelled" -> VoucherStatus.Cancelled
    else -> VoucherStatus.Unknown
}

internal fun String.toVoucherDataQuality(): VoucherDataQuality = when (lowercase()) {
    "complete" -> VoucherDataQuality.Complete
    "incomplete" -> VoucherDataQuality.Incomplete
    else -> VoucherDataQuality.Incomplete
}

internal fun VoucherSort.toApiSortParam(): String {
    val field = when (field) {
        VoucherSortField.Date -> "date"
        VoucherSortField.VoucherNumber -> "voucherNumber"
        VoucherSortField.Amount -> "amount"
    }
    return when (direction) {
        VoucherSortDirection.Asc -> field
        VoucherSortDirection.Desc -> "-$field"
    }
}

internal fun VoucherQuery.normalizedSearch(): String? =
    searchText?.trim()?.takeIf { it.isNotEmpty() }?.take(128)

internal fun VoucherQuery.normalizedOptional(value: String?): String? =
    value?.trim()?.takeIf { it.isNotEmpty() }?.take(128)
