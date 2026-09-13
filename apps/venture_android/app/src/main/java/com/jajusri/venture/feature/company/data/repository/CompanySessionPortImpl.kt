package com.jajusri.venture.feature.company.data.repository

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.company.domain.port.CompanySessionPort
import com.jajusri.venture.feature.company.domain.port.SelectedCompanyStatus
import com.jajusri.venture.feature.company.domain.port.SessionValidationStatus
import com.jajusri.venture.feature.company.domain.port.SessionValidity
import com.jajusri.venture.feature.company.domain.repository.CompanyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapts [CompanyRepository] to the stable [CompanySessionPort] for cross-feature consumers.
 */
@Singleton
class CompanySessionPortImpl @Inject constructor(
    private val repository: CompanyRepository,
) : CompanySessionPort {

    override fun observeSelectedCompany() = repository.observeSelectedCompany()

    override fun observeSelectedCompanyId(): Flow<String?> = repository.observeSelectedCompanyId()

    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        when (val session = repository.getSession()) {
            is AppResult.Success -> {
                val selected = session.value.selectedCompany
                AppResult.Success(
                    SelectedCompanyStatus(
                        companyId = selected?.id,
                        companyName = selected?.name,
                    ),
                )
            }
            is AppResult.Failure -> AppResult.Failure(session.error)
        }

    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> {
        val selectedId = repository.observeSelectedCompanyId().first()
        if (selectedId.isNullOrBlank()) {
            return AppResult.Success(
                SessionValidationStatus(
                    validity = SessionValidity.NoCompany,
                    companyId = null,
                    companyName = null,
                ),
            )
        }
        return when (val validation = repository.validateSession()) {
            is AppResult.Success -> AppResult.Success(
                SessionValidationStatus(
                    validity = SessionValidity.Valid,
                    companyId = validation.value.companyId ?: selectedId,
                    companyName = validation.value.companyName
                        ?: validation.value.session.selectedCompany?.name,
                ),
            )
            is AppResult.Failure -> AppResult.Success(
                SessionValidationStatus(
                    validity = when (validation.error) {
                        is AppError.Message -> SessionValidity.Invalid
                        else -> SessionValidity.Unknown
                    },
                    companyId = selectedId,
                    companyName = null,
                    error = validation.error,
                ),
            )
        }
    }
}
