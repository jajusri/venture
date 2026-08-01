package com.budcom.android.feature.company.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.company.data.local.CompanyLocalDataSource
import com.budcom.android.feature.company.data.remote.CompanyRemoteDataSource
import com.budcom.android.feature.company.domain.model.CompanyDiscoverySnapshot
import com.budcom.android.feature.company.domain.model.ConnectorSessionSnapshot
import com.budcom.android.feature.company.domain.model.SessionValidationOutcome
import com.budcom.android.feature.company.domain.model.SessionSelectedCompany
import com.budcom.android.feature.company.domain.repository.CompanyRepository
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
        when (val remote = remoteDataSource.fetchCompanies()) {
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

    override suspend fun getSession(): AppResult<ConnectorSessionSnapshot> =
        withContext(dispatchers.io) {
            remoteDataSource.fetchSession().toAppResult(errorMapper)
        }

    override suspend fun restoreSelection(): AppResult<SessionValidationOutcome?> =
        withContext(dispatchers.io) {
            val savedId = selectedCompanyStore.getSelectedCompanyId()
            if (savedId.isNullOrBlank()) {
                return@withContext hydrateLocalSelectionFromConnectorSession()
            }
            val isLegacySelection = selectedCompanyStore.observeSelectedCompany().first() == null

            Timber.tag("CompanySession").d("Attempting to restore session for companyId=%s", savedId)

            // First, try to select the company to ensure the connector has the session active
            when (val selection = remoteDataSource.selectCompany(savedId)) {
                is ApiResult.Failure -> {
                    Timber.tag("CompanySession").w("Remote selection failed during restore, keeping local ID")
                    AppResult.Failure(errorMapper.toAppError(selection.error))
                }

                is ApiResult.Success -> {
                    if (!isSuccessfulSelectionStatus(selection.data.status)) {
                        Timber.tag("CompanySession").e("Selection status invalid: %s. Clearing cache.", selection.data.status)
                        if (!isLegacySelection) {
                            selectedCompanyStore.clearSelectedCompanyIfCurrentId(savedId)
                        }
                        AppResult.Failure(AppError.Message(selection.data.reason ?: "Restore failed"))
                    } else {
                        validateAndPersist(
                            selection.data.session.selectedCompany?.id ?: savedId,
                            preserveIdOnInvalid = isLegacySelection,
                            expectedCurrentId = savedId,
                        )
                    }
                }
            }
        }

    private suspend fun hydrateLocalSelectionFromConnectorSession(): AppResult<SessionValidationOutcome?> {
        return when (val session = remoteDataSource.fetchSession()) {
            is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(session.error))
            is ApiResult.Success -> {
                val selectedId = session.data.selectedCompany?.id
                if (selectedId.isNullOrBlank()) {
                    AppResult.Success(null)
                } else {
                    if (!selectedCompanyStore.saveSelectedCompanyIfCurrentId(null, session.data.selectedCompany)) {
                        return AppResult.Success(null)
                    }
                    validateAndPersist(selectedId, expectedCurrentId = selectedId)
                }
            }
        }
    }

    override suspend fun selectCompany(companyId: String): AppResult<SessionValidationOutcome> =
        withContext(dispatchers.io) {
            when (val selection = remoteDataSource.selectCompany(companyId)) {
                is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(selection.error))
                is ApiResult.Success -> {
                    if (!isSuccessfulSelectionStatus(selection.data.status)) {
                        AppResult.Failure(AppError.Message(selection.data.reason ?: "Selection failed"))
                    } else {
                        validateAndPersist(selection.data.session.selectedCompany?.id ?: companyId)
                    }
                }
            }
        }

    override suspend fun validateSession(): AppResult<SessionValidationOutcome> =
        withContext(dispatchers.io) {
            when (val validation = remoteDataSource.validateSession()) {
                is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(validation.error))
                is ApiResult.Success -> {
                    if (validation.data.status == "SUCCESS") {
                        selectedCompanyFrom(validation.data, null)?.let { selectedCompanyStore.saveSelectedCompany(it) }
                        AppResult.Success(validation.data)
                    } else {
                        // If validation fails but we have a saved ID, try one-time re-selection before failing
                        val savedId = selectedCompanyStore.getSelectedCompanyId()
                        if (savedId != null) {
                            Timber.tag("CompanySession").i("Validation failed, attempting auto-recovery for %s", savedId)
                            selectCompany(savedId)
                        } else {
                            AppResult.Failure(AppError.Message(validation.data.reason ?: "Validation failed"))
                        }
                    }
                }
            }
        }

    override suspend fun clearSelection(): AppResult<Unit> = withContext(dispatchers.io) {
        when (val clear = remoteDataSource.clearSession()) {
            is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(clear.error))
            is ApiResult.Success -> {
                selectedCompanyStore.clearSelectedCompanyId()
                AppResult.Success(Unit)
            }
        }
    }

    private suspend fun validateAndPersist(
        candidateCompanyId: String,
        preserveIdOnInvalid: Boolean = false,
        expectedCurrentId: String? = null,
    ): AppResult<SessionValidationOutcome> {
        return when (val validation = remoteDataSource.validateSession()) {
            is ApiResult.Failure -> {
                // Network error - preserve local selection so user doesn't have to re-configure
                AppResult.Failure(errorMapper.toAppError(validation.error))
            }

            is ApiResult.Success -> {
                if (validation.data.status == "SUCCESS") {
                    selectedCompanyFrom(validation.data, candidateCompanyId)?.let { company ->
                        if (expectedCurrentId == null) {
                            selectedCompanyStore.saveSelectedCompany(company)
                        } else {
                            selectedCompanyStore.saveSelectedCompanyIfCurrentId(expectedCurrentId, company)
                        }
                    }
                    AppResult.Success(validation.data)
                } else {
                    // Critical failure (e.g. company no longer exists on Tally)
                    if (!preserveIdOnInvalid) {
                        if (expectedCurrentId == null) {
                            selectedCompanyStore.clearSelectedCompanyId()
                        } else {
                            selectedCompanyStore.clearSelectedCompanyIfCurrentId(expectedCurrentId)
                        }
                    }
                    AppResult.Failure(AppError.Message(validation.data.reason ?: "Invalid session"))
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
