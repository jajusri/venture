package com.budcom.android.feature.voucher.data.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.port.SearchVouchersPort
import com.budcom.android.feature.voucher.domain.repository.VoucherRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchVouchersPortImpl @Inject constructor(
    private val repository: VoucherRepository,
) : SearchVouchersPort {
    override suspend fun search(query: VoucherQuery): AppResult<VoucherPage> =
        repository.listVouchers(query)
}
