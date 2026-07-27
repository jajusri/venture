package com.budcom.android.feature.voucher.domain.usecase

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.repository.VoucherRepository
import javax.inject.Inject

class LoadVouchersUseCase @Inject constructor(
    private val repository: VoucherRepository,
) {
    suspend operator fun invoke(query: VoucherQuery): AppResult<VoucherPage> =
        repository.listVouchers(query)
}

class RefreshVouchersUseCase @Inject constructor(
    private val repository: VoucherRepository,
) {
    suspend operator fun invoke(query: VoucherQuery): AppResult<VoucherPage> =
        repository.listVouchers(query)
}

class GetVoucherDetailsUseCase @Inject constructor(
    private val repository: VoucherRepository,
) {
    suspend operator fun invoke(companyId: String, voucherId: String): AppResult<VoucherDetails> =
        repository.getVoucherDetails(companyId, voucherId)
}
