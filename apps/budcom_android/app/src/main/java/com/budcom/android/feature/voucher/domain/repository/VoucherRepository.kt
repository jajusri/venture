package com.budcom.android.feature.voucher.domain.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery

interface VoucherRepository {
    /** Reads the locally cached page immediately. Never contacts the Connector. */
    suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage>

    /** Attempts a bounded Connector fetch and persists it on success. Never falls back to cache on failure. */
    suspend fun refreshVouchers(query: VoucherQuery): AppResult<VoucherPage>

    /** Reads locally cached details immediately. Never contacts the Connector. */
    suspend fun getVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails>

    /** Attempts a bounded Connector fetch and persists it on success. Never falls back to cache on failure. */
    suspend fun refreshVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails>
}
