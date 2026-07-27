package com.budcom.android.feature.company.domain.port

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Stable public port for selected-company and session validation status.
 *
 * Cross-feature consumers must use this port rather than company feature internals.
 */
interface CompanySessionPort {
    fun observeSelectedCompanyId(): Flow<String?>

    /** Reads `GET /session` and projects the selected company when present. */
    suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus>

    /** Invokes `POST /session/validate` and maps the outcome for consumers. */
    suspend fun validateSessionStatus(): AppResult<SessionValidationStatus>
}

data class SelectedCompanyStatus(
    val companyId: String?,
    val companyName: String?,
)

data class SessionValidationStatus(
    val validity: SessionValidity,
    val companyId: String?,
    val companyName: String?,
    val error: AppError? = null,
)

enum class SessionValidity {
    NoCompany,
    Valid,
    Invalid,
    Unknown,
}
