package com.jajusri.venture.feature.voucher.data.repository

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.voucher.domain.model.VoucherPage
import com.jajusri.venture.feature.voucher.domain.model.VoucherQuery
import com.jajusri.venture.feature.voucher.domain.port.SearchVouchersPort
import com.jajusri.venture.feature.voucher.domain.repository.VoucherRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchVouchersPortImpl @Inject constructor(
    private val repository: VoucherRepository,
) : SearchVouchersPort {
    override suspend fun search(query: VoucherQuery): AppResult<VoucherPage> =
        repository.listVouchers(query)
}
