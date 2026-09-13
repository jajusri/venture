package com.jajusri.venture.feature.diagnostics.domain.port

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.diagnostics.domain.model.ConnectionDiagnostics

/**
 * Stable read-only port for Connector `GET /diagnostics/connection`.
 */
interface ConnectionDiagnosticsPort {
    suspend fun loadConnectionDiagnostics(): AppResult<ConnectionDiagnostics>
}
