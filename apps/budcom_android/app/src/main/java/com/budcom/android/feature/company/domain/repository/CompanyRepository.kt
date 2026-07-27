package com.budcom.android.feature.company.domain.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.company.domain.model.CompanyDiscoverySnapshot
import com.budcom.android.feature.company.domain.model.ConnectorSessionSnapshot
import com.budcom.android.feature.company.domain.model.SessionValidationOutcome
import kotlinx.coroutines.flow.Flow

/**
 * Repository for company discovery and connector session selection lifecycle.
 */
interface CompanyRepository {
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
