package com.jajusri.venture.feature.transaction.data.repository

import com.jajusri.venture.core.util.DispatcherProvider
import com.jajusri.venture.feature.transaction.data.local.AuthenticatedCounterpartyBindingDao
import com.jajusri.venture.feature.transaction.data.local.AuthenticatedCounterpartyBindingEntity
import com.jajusri.venture.feature.transaction.domain.model.AuthenticatedCounterpartyBinding
import com.jajusri.venture.feature.transaction.domain.model.AuthenticatedCounterpartyBindingRepository
import com.jajusri.venture.feature.transaction.domain.model.CounterpartyBindingStatus
import com.jajusri.venture.feature.transaction.domain.port.CredentialVerificationOutcome
import com.jajusri.venture.feature.transaction.domain.port.CredentialVerificationRequest
import com.jajusri.venture.feature.transaction.domain.port.TransportCredentialVerifier
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthenticatedCounterpartyBindingRepositoryImpl @Inject constructor(
    private val dao: AuthenticatedCounterpartyBindingDao,
    private val dispatchers: DispatcherProvider,
    private val credentialVerifier: TransportCredentialVerifier,
) : AuthenticatedCounterpartyBindingRepository {
    override suspend fun verifyAndRecord(
        localBusinessId: String,
        partyId: String,
        verificationRequest: CredentialVerificationRequest,
        verifiedAtEpochMillis: Long,
    ): AuthenticatedCounterpartyBinding? = withContext(dispatchers.io) {
        val credential = verificationRequest.credential
        if (localBusinessId.isBlank() || partyId.isBlank() || verifiedAtEpochMillis < 0 ||
            credential.credentialId.isBlank() || credential.businessId == localBusinessId ||
            verificationRequest.expectedBusinessId != credential.businessId ||
            verificationRequest.actualRecipient != verificationRequest.expectedRecipient ||
            verificationRequest.expectedRecipient.businessId != localBusinessId ||
            verificationRequest.expectedRecipient.partyId != partyId
        ) return@withContext null
        val outcome = credentialVerifier.verify(verificationRequest) as? CredentialVerificationOutcome.Valid
            ?: return@withContext null
        val verifiedAuthority = outcome.authorityContext
        if (verifiedAuthority.businessId != credential.businessId ||
            verifiedAuthority.actorId.isBlank() || verifiedAuthority.deviceId.isBlank() ||
            verifiedAuthority.authorityEpoch < 0 || REQUIRED_BINDING_SCOPE !in verifiedAuthority.authorityScope
        ) return@withContext null
        val candidate = AuthenticatedCounterpartyBindingEntity(
            localBusinessId, partyId, verifiedAuthority.businessId, verifiedAuthority.actorId,
            verifiedAuthority.deviceId, verifiedAuthority.authorityEpoch, credential.credentialId,
            "ACTIVE", verifiedAtEpochMillis,
        )
        val existing = dao.find(localBusinessId, partyId)
        if (existing != null) return@withContext existing.takeIf { it == candidate }?.toDomain()
        dao.insert(candidate)
        candidate.toDomain()
    }

    override suspend fun resolveActive(localBusinessId: String, partyId: String) = withContext(dispatchers.io) {
        dao.find(localBusinessId, partyId)?.takeIf { it.status == "ACTIVE" }?.toDomain()
    }

    override suspend fun revoke(localBusinessId: String, partyId: String) = withContext(dispatchers.io) {
        dao.revoke(localBusinessId, partyId)
    }

    private companion object {
        const val REQUIRED_BINDING_SCOPE = "send_orders"
    }
}

private fun AuthenticatedCounterpartyBindingEntity.toDomain() = AuthenticatedCounterpartyBinding(
    localBusinessId, partyId, counterpartyBusinessId, verifiedActorId, verifiedDeviceId, authorityEpoch,
    verificationReference, CounterpartyBindingStatus.valueOf(status.lowercase().replaceFirstChar(Char::uppercase)),
    verifiedAtEpochMillis,
)
