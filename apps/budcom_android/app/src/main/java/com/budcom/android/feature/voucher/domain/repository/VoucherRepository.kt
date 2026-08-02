package com.budcom.android.feature.voucher.domain.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery

interface VoucherRepository {
    suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage>

    suspend fun getVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails>
}
