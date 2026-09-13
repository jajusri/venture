package com.jajusri.venture.feature.voucher.domain.usecase

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.voucher.domain.model.VoucherDetails
import com.jajusri.venture.feature.voucher.domain.model.VoucherPage
import com.jajusri.venture.feature.voucher.domain.model.VoucherQuery
import com.jajusri.venture.feature.voucher.domain.model.VoucherSummary
import com.jajusri.venture.feature.voucher.domain.repository.VoucherRepository
import javax.inject.Inject

/** Reads the cached voucher page immediately. Never contacts the Connector. */
class LoadVouchersUseCase @Inject constructor(
    private val repository: VoucherRepository,
) {
    suspend operator fun invoke(query: VoucherQuery): AppResult<VoucherPage> =
        repository.listVouchers(query)
}

/** Explicit, bounded Connector refresh. Only invoked when the user asks to refresh. */
class RefreshVouchersUseCase @Inject constructor(
    private val repository: VoucherRepository,
) {
    suspend operator fun invoke(query: VoucherQuery): AppResult<VoucherPage> =
        repository.refreshVouchers(query)
}

/** Reads cached voucher details immediately. Never contacts the Connector. */
class GetVoucherDetailsUseCase @Inject constructor(
    private val repository: VoucherRepository,
) {
    suspend operator fun invoke(companyId: String, voucherId: String): AppResult<VoucherDetails> =
        repository.getVoucherDetails(companyId, voucherId)
}

/** Explicit, bounded Connector refresh for a single voucher's details. */
class RefreshVoucherDetailsUseCase @Inject constructor(
    private val repository: VoucherRepository,
) {
    suspend operator fun invoke(companyId: String, voucherId: String): AppResult<VoucherDetails> =
        repository.refreshVoucherDetails(companyId, voucherId)
}

/** Best-effort local header lookup, usable even before full details have been downloaded. Never contacts the Connector. */
class GetCachedVoucherSummaryUseCase @Inject constructor(
    private val repository: VoucherRepository,
) {
    suspend operator fun invoke(companyId: String, voucherId: String): VoucherSummary? =
        repository.getCachedVoucherSummary(companyId, voucherId)
}
