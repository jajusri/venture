package com.budcom.android.feature.company.domain.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.company.domain.model.CompanyDiscoverySnapshot
import com.budcom.android.feature.company.domain.model.ConnectorSessionSnapshot
import com.budcom.android.feature.company.domain.model.SessionValidationOutcome
import com.budcom.android.feature.company.domain.model.SessionSelectedCompany
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for company discovery and connector session selection lifecycle.
 */
interface CompanyRepository {
    fun observeSelectedCompany(): Flow<SessionSelectedCompany?> =
        observeSelectedCompanyId().map { id -> id?.let { SessionSelectedCompany(it, it) } }
    fun observeSelectedCompanyId(): Flow<String?>

    suspend fun loadCompanies(): AppResult<CompanyDiscoverySnapshot>

    suspend fun refreshCompanies(): AppResult<CompanyDiscoverySnapshot>

    /** Reads the current Connector session (`GET /session`) without mutating selection. */
    suspend fun getSession(): AppResult<ConnectorSessionSnapshot>

    suspend fun restoreSelection(): AppResult<SessionValidationOutcome?>

    suspend fun selectCompany(companyId: String): AppResult<SessionValidationOutcome>

    suspend fun validateSession(): AppResult<SessionValidationOutcome>

    suspend fun clearSelection(): AppResult<Unit>
}
