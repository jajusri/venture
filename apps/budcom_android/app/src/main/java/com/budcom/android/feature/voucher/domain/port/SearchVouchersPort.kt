package com.budcom.android.feature.voucher.domain.port

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery

/**
 * Stable public read-only search port for Vouchers.
 *
 * Cross-feature consumers (e.g. Universal Search) must use this port rather than
 * the Voucher repository implementation.
 */
interface SearchVouchersPort {
    suspend fun search(query: VoucherQuery): AppResult<VoucherPage>
}
