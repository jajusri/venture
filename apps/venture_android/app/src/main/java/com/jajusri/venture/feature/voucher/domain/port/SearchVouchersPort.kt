package com.jajusri.venture.feature.voucher.domain.port

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.voucher.domain.model.VoucherPage
import com.jajusri.venture.feature.voucher.domain.model.VoucherQuery

/**
 * Stable public read-only search port for Vouchers.
 *
 * Cross-feature consumers (e.g. Universal Search) must use this port rather than
 * the Voucher repository implementation.
 */
interface SearchVouchersPort {
    suspend fun search(query: VoucherQuery): AppResult<VoucherPage>
}
