package com.jajusri.venture.feature.transaction.domain.model

import com.jajusri.venture.feature.transaction.domain.port.CredentialVerificationRequest

enum class CounterpartyBindingStatus { Active, Revoked }

data class AuthenticatedCounterpartyBinding(
    val localBusinessId: String,
    val partyId: String,
    val counterpartyBusinessId: String,
    val verifiedActorId: String,
    val verifiedDeviceId: String,
    val authorityEpoch: Long,
    val verificationReference: String,
    val status: CounterpartyBindingStatus,
    val verifiedAtEpochMillis: Long,
)

interface AuthenticatedCounterpartyBindingRepository {
    suspend fun verifyAndRecord(
        localBusinessId: String,
        partyId: String,
        verificationRequest: CredentialVerificationRequest,
        verifiedAtEpochMillis: Long,
    ): AuthenticatedCounterpartyBinding?

    suspend fun resolveActive(localBusinessId: String, partyId: String): AuthenticatedCounterpartyBinding?
    suspend fun revoke(localBusinessId: String, partyId: String)
}
