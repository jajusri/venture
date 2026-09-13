package com.jajusri.venture.feature.masterdata.ledger.domain.usecase

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.voucher.domain.model.VoucherDateRange
import com.jajusri.venture.feature.voucher.domain.model.VoucherPage
import com.jajusri.venture.feature.voucher.domain.model.VoucherQuery
import com.jajusri.venture.feature.voucher.domain.usecase.RefreshVouchersUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Ledger statement screen's only network-reaching action (Phase 6, locked): it re-runs the
 * SAME [RefreshVouchersUseCase] every other Voucher-driven screen already uses, scoped to
 * whichever date range the Ledger statement is currently showing — never a bespoke per-ledger
 * HTTP call, and never `GET /ledgers/{id}/statement` (that endpoint is retained only as
 * diagnostic/fallback compatibility; see the accompanying report). A successful refresh
 * atomically replaces every Voucher (and therefore every Ledger movement derived from it, see
 * [GetLocalLedgerStatementUseCase]) in that window; the caller must re-run
 * [GetLocalLedgerStatementUseCase] afterward to observe the refreshed local data — this use case
 * does not itself return a statement.
 */
@Singleton
class RefreshLedgerCoverageUseCase @Inject constructor(
    private val refreshVouchers: RefreshVouchersUseCase,
) {
    suspend operator fun invoke(companyId: String, from: String, to: String): AppResult<VoucherPage> =
        refreshVouchers(VoucherQuery(companyId = companyId, dateRange = VoucherDateRange(from, to)))
}
