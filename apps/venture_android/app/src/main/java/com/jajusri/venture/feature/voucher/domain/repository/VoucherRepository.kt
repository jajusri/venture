package com.jajusri.venture.feature.voucher.domain.repository

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.voucher.domain.model.VoucherDetails
import com.jajusri.venture.feature.voucher.domain.model.VoucherPage
import com.jajusri.venture.feature.voucher.domain.model.VoucherQuery
import com.jajusri.venture.feature.voucher.domain.model.VoucherSummary

interface VoucherRepository {
    /** Reads the locally cached page immediately. Never contacts the Connector. */
    suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage>

    /** Attempts a bounded Connector fetch and persists it on success. Never falls back to cache on failure. */
    suspend fun refreshVouchers(query: VoucherQuery): AppResult<VoucherPage>

    /** Reads locally cached details immediately. Never contacts the Connector. */
    suspend fun getVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails>

    /** Attempts a bounded Connector fetch and persists it on success. Never falls back to cache on failure. */
    suspend fun refreshVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails>

    /** Best-effort local header lookup (e.g. from the list sync) even when full details are not yet stored. Never contacts the Connector. */
    suspend fun getCachedVoucherSummary(companyId: String, voucherId: String): VoucherSummary?
}
