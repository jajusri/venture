package com.jajusri.venture.feature.company.data.repository

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.jajusri.venture.core.connectorauth.domain.ConnectorTransportSelection
import com.jajusri.venture.core.network.ApiResult
import com.jajusri.venture.core.network.ErrorMapper
import com.jajusri.venture.core.util.DispatcherProvider
import com.jajusri.venture.feature.company.data.local.CompanyLocalDataSource
import com.jajusri.venture.feature.company.data.remote.AuthenticatedCompanyRemoteDataSource
import com.jajusri.venture.feature.company.data.remote.CompanyRemoteDataSource
import com.jajusri.venture.feature.company.domain.model.CompanyDiscoverySnapshot
import com.jajusri.venture.feature.company.domain.model.CompanySelectionOutcome
import com.jajusri.venture.feature.company.domain.model.ConnectorSessionSnapshot
import com.jajusri.venture.feature.company.domain.model.SessionValidationOutcome
import com.jajusri.venture.feature.company.domain.model.SessionSelectedCompany
import com.jajusri.venture.feature.company.domain.repository.CompanyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class CompanyRepositoryImpl @Inject constructor(
    private val remoteDataSource: CompanyRemoteDataSource,
    private val localDataSource: CompanyLocalDataSource,
    private val selectedCompanyStore: SelectedCompanyStore,
    private val errorMapper: ErrorMapper,
    private val dispatchers: DispatcherProvider,
    private val transportGate: ConnectorTransportSelectionGate,
    private val authenticatedRemoteDataSource: AuthenticatedCompanyRemoteDataSource,
) : CompanyRepository {

    override fun observeSelectedCompany(): Flow<SessionSelectedCompany?> = selectedCompanyStore.observeSelectedCompany()

    override fun observeSelectedCompanyId(): Flow<String?> = selectedCompanyStore.observeSelectedCompanyId()

    override suspend fun loadCompanies(): AppResult<CompanyDiscoverySnapshot> =
        withContext(dispatchers.io) {
            loadCompaniesWithCache()
        }

    override suspend fun refreshCompanies(): AppResult<CompanyDiscoverySnapshot> =
        withContext(dispatchers.io) {
            loadCompaniesWithCache()
        }

    private suspend fun loadCompaniesWithCache(): AppResult<CompanyDiscoverySnapshot> =
        when (transportGate.resolve()) {
            ConnectorTransportSelection.LEGACY -> when (val remote = remoteDataSource.fetchCompanies()) {
                is ApiResult.Success -> {
                    localDataSource.replaceSnapshot(remote.data)
                    AppResult.Success(remote.data)
                }

                is ApiResult.Failure -> {
                    val cached = localDataSource.readSnapshot()
                    if (cached != null) {
                        AppResult.Success(cached)
                    } else {
                        AppResult.Failure(errorMapper.toAppError(remote.error))
                    }
                }
            }

            ConnectorTransportSelection.AUTHENTICATED -> when (val remote = authenticatedRemoteDataSource.fetchCompanies()) {
                is AppResult.Success -> {
                    localDataSource.replaceSnapshot(remote.value)
                    AppResult.Success(remote.value)
                }

                is AppResult.Failure -> {
                    // An authentication rejection must remain visible, never silently masked by a
                    // stale-cache fallback — every other authenticated failure degrades exactly
                    // like the legacy path (cached data if any, else the mapped failure).
                    if (remote.error.isAuthenticationRejection()) {
                        remote
                    } else {
                        val cached = localDataSource.readSnapshot()
                        if (cached != null) AppResult.Success(cached) else remote
                    }
                }
            }
        }

    override suspend fun getSession(): AppResult<ConnectorSessionSnapshot> =
        withContext(dispatchers.io) {
            when (transportGate.resolve()) {
                ConnectorTransportSelection.LEGACY -> remoteDataSource.fetchSession().toAppResult(errorMapper)
                ConnectorTransportSelection.AUTHENTICATED -> authenticatedRemoteDataSource.fetchSession()
            }
        }

    override suspend fun restoreSelection(): AppResult<SessionValidationOutcome?> =
        withContext(dispatchers.io) {
            val selection = transportGate.resolve()
            val savedId = selectedCompanyStore.getSelectedCompanyId()
            if (savedId.isNullOrBlank()) {
                return@withContext hydrateLocalSelectionFromConnectorSession(selection)
            }
            val isLegacySelection = selectedCompanyStore.observeSelectedCompany().first() == null

            Timber.tag("CompanySession").d("Attempting to restore session for companyId=%s", savedId)

            // First, try to select the company to ensure the connector has the session active
            when (val selectionResult = selectCompanyRemote(selection, savedId)) {
                is AppResult.Failure -> {
                    Timber.tag("CompanySession").w("Remote selection failed during restore, keeping local ID")
                    selectionResult
                }

                is AppResult.Success -> {
                    if (!isSuccessfulSelectionStatus(selectionResult.value.status)) {
                        Timber.tag("CompanySession").e("Selection status invalid: %s. Clearing cache.", selectionResult.value.status)
                        if (!isLegacySelection) {
                            selectedCompanyStore.clearSelectedCompanyIfCurrentId(savedId)
                        }
                        AppResult.Failure(AppError.Message(selectionResult.value.reason ?: "Restore failed"))
                    } else {
                        validateAndPersist(
                            selection,
                            selectionResult.value.session.selectedCompany?.id ?: savedId,
                            preserveIdOnInvalid = isLegacySelection,
                            expectedCurrentId = savedId,
                        )
                    }
                }
            }
        }

    private suspend fun hydrateLocalSelectionFromConnectorSession(selection: ConnectorTransportSelection): AppResult<SessionValidationOutcome?> {
        val sessionResult = when (selection) {
            ConnectorTransportSelection.LEGACY -> remoteDataSource.fetchSession().toAppResult(errorMapper)
            ConnectorTransportSelection.AUTHENTICATED -> authenticatedRemoteDataSource.fetchSession()
        }
        return when (sessionResult) {
            is AppResult.Failure -> sessionResult
            is AppResult.Success -> {
                val selectedId = sessionResult.value.selectedCompany?.id
                if (selectedId.isNullOrBlank()) {
                    AppResult.Success(null)
                } else {
                    if (!selectedCompanyStore.saveSelectedCompanyIfCurrentId(null, sessionResult.value.selectedCompany)) {
                        return AppResult.Success(null)
                    }
                    validateAndPersist(selection, selectedId, expectedCurrentId = selectedId)
                }
            }
        }
    }

    override suspend fun selectCompany(companyId: String): AppResult<SessionValidationOutcome> =
        withContext(dispatchers.io) {
            val selection = transportGate.resolve()
            when (val selectionResult = selectCompanyRemote(selection, companyId)) {
                is AppResult.Failure -> selectionResult
                is AppResult.Success -> {
                    if (!isSuccessfulSelectionStatus(selectionResult.value.status)) {
                        AppResult.Failure(AppError.Message(selectionResult.value.reason ?: "Selection failed"))
                    } else {
                        validateAndPersist(selection, selectionResult.value.session.selectedCompany?.id ?: companyId)
                    }
                }
            }
        }

    override suspend fun validateSession(): AppResult<SessionValidationOutcome> =
        withContext(dispatchers.io) {
            val selection = transportGate.resolve()
            when (val validation = validateSessionRemote(selection)) {
                is AppResult.Failure -> {
                    // Authenticated-transport parity with the branch below: recover only the
                    // two exact, typed session-state sentinels. Both use the same bounded
                    // select-once path; no recursive validation or generic 4xx retry is allowed.
                    val savedId = selectedCompanyStore.getSelectedCompanyId()
                    if (validation.error.isRecoverableSessionRejection() && savedId != null) {
                        Timber.tag("CompanySession").i(
                            "Validation lost or expired its session, attempting one authenticated renewal for %s",
                            savedId,
                        )
                        selectCompany(savedId)
                    } else {
                        validation
                    }
                }
                is AppResult.Success -> {
                    if (validation.value.status == "SUCCESS") {
                        selectedCompanyFrom(validation.value, null)?.let { selectedCompanyStore.saveSelectedCompany(it) }
                        AppResult.Success(validation.value)
                    } else {
                        // If validation fails but we have a saved ID, try one-time re-selection before failing
                        val savedId = selectedCompanyStore.getSelectedCompanyId()
                        if (savedId != null) {
                            Timber.tag("CompanySession").i("Validation failed, attempting auto-recovery for %s", savedId)
                            selectCompany(savedId)
                        } else {
                            AppResult.Failure(AppError.Message(validation.value.reason ?: "Validation failed"))
                        }
                    }
                }
            }
        }

    override suspend fun clearSelection(): AppResult<Unit> = withContext(dispatchers.io) {
        val selection = transportGate.resolve()
        val clear = when (selection) {
            ConnectorTransportSelection.LEGACY -> remoteDataSource.clearSession().toAppResult(errorMapper)
            ConnectorTransportSelection.AUTHENTICATED -> authenticatedRemoteDataSource.clearSession()
        }
        when (clear) {
            is AppResult.Failure -> clear
            is AppResult.Success -> {
                selectedCompanyStore.clearSelectedCompanyId()
                AppResult.Success(Unit)
            }
        }
    }

    private suspend fun selectCompanyRemote(selection: ConnectorTransportSelection, companyId: String): AppResult<CompanySelectionOutcome> =
        when (selection) {
            ConnectorTransportSelection.LEGACY -> remoteDataSource.selectCompany(companyId).toAppResult(errorMapper)
            ConnectorTransportSelection.AUTHENTICATED -> authenticatedRemoteDataSource.selectCompany(companyId)
        }

    private suspend fun validateSessionRemote(selection: ConnectorTransportSelection): AppResult<SessionValidationOutcome> =
        when (selection) {
            ConnectorTransportSelection.LEGACY -> remoteDataSource.validateSession().toAppResult(errorMapper)
            ConnectorTransportSelection.AUTHENTICATED -> authenticatedRemoteDataSource.validateSession()
        }

    private suspend fun validateAndPersist(
        selection: ConnectorTransportSelection,
        candidateCompanyId: String,
        preserveIdOnInvalid: Boolean = false,
        expectedCurrentId: String? = null,
    ): AppResult<SessionValidationOutcome> {
        return when (val validation = validateSessionRemote(selection)) {
            is AppResult.Failure -> {
                // Network error - preserve local selection so user doesn't have to re-configure
                validation
            }

            is AppResult.Success -> {
                if (validation.value.status == "SUCCESS") {
                    selectedCompanyFrom(validation.value, candidateCompanyId)?.let { company ->
                        if (expectedCurrentId == null) {
                            selectedCompanyStore.saveSelectedCompany(company)
                        } else {
                            selectedCompanyStore.saveSelectedCompanyIfCurrentId(expectedCurrentId, company)
                        }
                    }
                    AppResult.Success(validation.value)
                } else {
                    // Critical failure (e.g. company no longer exists on Tally)
                    if (!preserveIdOnInvalid) {
                        if (expectedCurrentId == null) {
                            selectedCompanyStore.clearSelectedCompanyId()
                        } else {
                            selectedCompanyStore.clearSelectedCompanyIfCurrentId(expectedCurrentId)
                        }
                    }
                    AppResult.Failure(AppError.Message(validation.value.reason ?: "Invalid session"))
                }
            }
        }
    }

    private fun isSuccessfulSelectionStatus(status: String): Boolean =
        status == "SUCCESS" || status == "DUPLICATE_SELECTION"
}

private fun selectedCompanyFrom(
    outcome: SessionValidationOutcome,
    fallbackId: String?,
): SessionSelectedCompany? {
    val sessionCompany = outcome.session.selectedCompany
    val id = outcome.companyId ?: sessionCompany?.id ?: fallbackId ?: return null
    val name = outcome.companyName ?: sessionCompany?.name ?: return null
    return SessionSelectedCompany(id, name)
}

private fun <T> ApiResult<T>.toAppResult(errorMapper: ErrorMapper): AppResult<T> = when (this) {
    is ApiResult.Success -> AppResult.Success(data)
    is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(error))
}

/** True only for the two outcomes [com.jajusri.venture.core.connectorauth.domain.AuthenticatedRepositoryFailurePolicy]
 * produces from an authentication rejection (401/403) — see its `AUTHENTICATED_*_CODE` constants. */
private fun AppError.isAuthenticationRejection(): Boolean =
    this is AppError.Remote && (
        code == com.jajusri.venture.core.connectorauth.domain.AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE ||
            code == com.jajusri.venture.core.connectorauth.domain.AUTHENTICATED_ACCESS_DENIED_CODE
        )

/**
 * True only for the authenticated transport's exact `NO_COMPANY_SELECTED` and `SESSION_EXPIRED`
 * sentinels (TD-013). The legacy transport decodes structured business responses as
 * [AppResult.Success], so its recovery remains in the success branch above.
 */
private fun AppError.isRecoverableSessionRejection(): Boolean =
    this is AppError.Remote && (
        code == com.jajusri.venture.core.connectorauth.domain.AUTHENTICATED_NO_COMPANY_SELECTED_CODE ||
            code == com.jajusri.venture.core.connectorauth.domain.AUTHENTICATED_SESSION_EXPIRED_CODE
        )
