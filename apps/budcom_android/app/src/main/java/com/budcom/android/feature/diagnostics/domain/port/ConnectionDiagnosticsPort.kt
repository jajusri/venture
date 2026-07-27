package com.budcom.android.feature.diagnostics.domain.port

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.diagnostics.domain.model.ConnectionDiagnostics

/**
 * Stable read-only port for Connector `GET /diagnostics/connection`.
 */
interface ConnectionDiagnosticsPort {
    suspend fun loadConnectionDiagnostics(): AppResult<ConnectionDiagnostics>
}
