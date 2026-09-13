package com.jajusri.venture.feature.company.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Confirmed Connector company/session endpoints.
 */
interface CompanyApi {
    @GET("companies")
    suspend fun getCompanies(): CompanyListResultDto

    @GET("session")
    suspend fun getSession(): SessionEnvelopeDto

    @POST("session/company")
    suspend fun selectCompany(
        @Body request: SelectCompanyRequestDto,
    ): Response<CompanySelectionResultDto>

    @POST("session/validate")
    suspend fun validateSession(): Response<SessionValidationResultDto>

    @DELETE("session/company")
    suspend fun clearSession(): Response<SessionClearResultDto>
}
