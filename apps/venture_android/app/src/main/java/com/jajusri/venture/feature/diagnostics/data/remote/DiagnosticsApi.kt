package com.jajusri.venture.feature.diagnostics.data.remote

import retrofit2.http.GET

interface DiagnosticsApi {
    @GET("diagnostics/connection")
    suspend fun getConnectionDiagnostics(): ConnectionDiagnosticsEnvelopeDto
}
