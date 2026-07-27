package com.budcom.android.feature.diagnostics.data.remote

import retrofit2.http.GET

interface DiagnosticsApi {
    @GET("diagnostics/connection")
    suspend fun getConnectionDiagnostics(): ConnectionDiagnosticsEnvelopeDto
}
