package com.budcom.android.feature.masterdata.ledger.data.repository

import com.budcom.android.feature.masterdata.ledger.data.local.LedgerDao
import com.budcom.android.feature.masterdata.ledger.data.local.toDomain
import com.budcom.android.feature.masterdata.ledger.domain.model.Ledger
import com.budcom.android.feature.masterdata.ledger.domain.port.LedgerSnapshotPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedgerSnapshotPortImpl @Inject constructor(
    private val ledgerDao: LedgerDao,
) : LedgerSnapshotPort {
    override suspend fun getCachedLedgers(companyId: String): List<Ledger> =
        ledgerDao.getAllForCompany(companyId).map { it.toDomain() }
}
