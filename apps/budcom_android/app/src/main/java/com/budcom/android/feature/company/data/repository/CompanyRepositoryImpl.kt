package com.budcom.android.feature.company.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.company.data.remote.CompanyRemoteDataSource
import com.budcom.android.feature.company.domain.model.CompanyDiscoverySnapshot
import com.budcom.android.feature.company.domain.model.ConnectorSessionSnapshot
import com.budcom.android.feature.company.domain.model.SessionValidationOutcome
import com.budcom.android.feature.company.domain.repository.CompanyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompanyRepositoryImpl @Inject constructor(
    private val remoteDataSource: CompanyRemoteDataSource,
    private val selectedCompanyStore: SelectedCompanyStore,
    private val errorMapper: ErrorMapper,
    private val dispatchers: DispatcherProvider,
) : CompanyRepository {

    override fun observeSelectedCompanyId(): Flow<String?> = selectedCompanyStore.observeSelectedCompanyId()

    override suspend fun loadCompanies(): AppResult<CompanyDiscoverySnapshot> =
        withContext(dispatchers.io) {
            remoteDataSource.fetchCompanies().toAppResult(errorMapper)
        }

    override suspend fun refreshCompanies(): AppResult<CompanyDiscoverySnapshot> =
        withContext(dispatchers.io) {
            remoteDataSource.fetchCompanies().toAppResult(errorMapper)
        }

    override suspend fun getSession(): AppResult<ConnectorSessionSnapshot> =
        withContext(dispatchers.io) {
            remoteDataSource.fetchSession().toAppResult(errorMapper)
        }

    override suspend fun restoreSelection(): AppResult<SessionValidationOutcome?> =
        withContext(dispatchers.io) {
            val savedId = selectedCompanyStore.getSelectedCompanyId()
            if (savedId.isNullOrBlank()) return@withContext AppResult.Success(null)

            when (val selection = remoteDataSource.selectCompany(savedId)) {
                is ApiResult.Failure -> {
                    selectedCompanyStore.clearSelectedCompanyId()
                    AppResult.Failure(errorMapper.toAppError(selection.error))
                }

                is ApiResult.Success -> {
                    if (!isSuccessfulSelectionStatus(selection.data.status)) {
                        selectedCompanyStore.clearSelectedCompanyId()
                        AppResult.Failure(
                            AppError.Message(
                                message = selection.data.reason ?: "Selection restore failed: ${selection.data.status}",
                            ),
                        )
                    } else {
                        validateAndPersist(selection.data.session.selectedCompany?.id ?: savedId)
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
                        AppResult.Failure(
                            AppError.Message(
                                message = selection.data.reason ?: "Selection failed: ${selection.data.status}",
                            ),
                        )
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
                        validation.data.session.selectedCompany?.id?.let { selectedCompanyStore.saveSelectedCompanyId(it) }
                        AppResult.Success(validation.data)
                    } else {
                        AppResult.Failure(
                            AppError.Message(
                                message = validation.data.reason ?: "Session validation failed: ${validation.data.status}",
                            ),
                        )
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

    private suspend fun validateAndPersist(candidateCompanyId: String): AppResult<SessionValidationOutcome> {
        return when (val validation = remoteDataSource.validateSession()) {
            is ApiResult.Failure -> {
                selectedCompanyStore.clearSelectedCompanyId()
                AppResult.Failure(errorMapper.toAppError(validation.error))
            }

            is ApiResult.Success -> {
                if (validation.data.status == "SUCCESS") {
                    selectedCompanyStore.saveSelectedCompanyId(
                        validation.data.companyId ?: candidateCompanyId,
                    )
                    AppResult.Success(validation.data)
                } else {
                    selectedCompanyStore.clearSelectedCompanyId()
                    AppResult.Failure(
                        AppError.Message(
                            message = validation.data.reason
                                ?: "Session validation failed: ${validation.data.status}",
                        ),
                    )
                }
            }
        }
    }

    private fun isSuccessfulSelectionStatus(status: String): Boolean =
        status == "SUCCESS" || status == "DUPLICATE_SELECTION"
}

private fun <T> ApiResult<T>.toAppResult(errorMapper: ErrorMapper): AppResult<T> = when (this) {
    is ApiResult.Success -> AppResult.Success(data)
    is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(error))
}
