package com.budcom.android.feature.company.domain.usecase

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.company.domain.model.CompanyDiscoverySnapshot
import com.budcom.android.feature.company.domain.model.SessionValidationOutcome
import com.budcom.android.feature.company.domain.repository.CompanyRepository
import javax.inject.Inject

class LoadCompaniesUseCase @Inject constructor(
    private val repository: CompanyRepository,
) {
    suspend operator fun invoke(refresh: Boolean): AppResult<CompanyDiscoverySnapshot> =
        if (refresh) repository.refreshCompanies() else repository.loadCompanies()
}

class RestoreCompanySelectionUseCase @Inject constructor(
    private val repository: CompanyRepository,
) {
    suspend operator fun invoke(): AppResult<SessionValidationOutcome?> = repository.restoreSelection()
}

class SelectCompanyUseCase @Inject constructor(
    private val repository: CompanyRepository,
) {
    suspend operator fun invoke(companyId: String): AppResult<SessionValidationOutcome> =
        repository.selectCompany(companyId)
}

class ValidateSessionUseCase @Inject constructor(
    private val repository: CompanyRepository,
) {
    suspend operator fun invoke(): AppResult<SessionValidationOutcome> = repository.validateSession()
}

class ClearSessionUseCase @Inject constructor(
    private val repository: CompanyRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> = repository.clearSelection()
}
