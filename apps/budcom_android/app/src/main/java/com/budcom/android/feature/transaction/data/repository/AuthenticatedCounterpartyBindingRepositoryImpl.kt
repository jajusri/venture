package com.budcom.android.feature.transaction.data.repository

import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.transaction.data.local.AuthenticatedCounterpartyBindingDao
import com.budcom.android.feature.transaction.data.local.AuthenticatedCounterpartyBindingEntity
import com.budcom.android.feature.transaction.domain.model.AuthenticatedCounterpartyBinding
import com.budcom.android.feature.transaction.domain.model.AuthenticatedCounterpartyBindingRepository
import com.budcom.android.feature.transaction.domain.model.CounterpartyBindingStatus
import com.budcom.android.feature.transaction.domain.port.TransportAuthorityContext
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthenticatedCounterpartyBindingRepositoryImpl @Inject constructor(
    private val dao: AuthenticatedCounterpartyBindingDao,
    private val dispatchers: DispatcherProvider,
) : AuthenticatedCounterpartyBindingRepository {
    override suspend fun recordVerified(
        localBusinessId: String,
        partyId: String,
        verifiedAuthority: TransportAuthorityContext,
        verificationReference: String,
        verifiedAtEpochMillis: Long,
    ): AuthenticatedCounterpartyBinding? = withContext(dispatchers.io) {
        if (localBusinessId.isBlank() || partyId.isBlank() || verificationReference.isBlank() || verifiedAtEpochMillis < 0 ||
            verifiedAuthority.businessId.isBlank() || verifiedAuthority.businessId == localBusinessId ||
            verifiedAuthority.actorId.isBlank() || verifiedAuthority.deviceId.isBlank() || verifiedAuthority.authorityEpoch < 0
        ) return@withContext null
        val candidate = AuthenticatedCounterpartyBindingEntity(
            localBusinessId, partyId, verifiedAuthority.businessId, verifiedAuthority.actorId,
            verifiedAuthority.deviceId, verifiedAuthority.authorityEpoch, verificationReference,
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
}

private fun AuthenticatedCounterpartyBindingEntity.toDomain() = AuthenticatedCounterpartyBinding(
    localBusinessId, partyId, counterpartyBusinessId, verifiedActorId, verifiedDeviceId, authorityEpoch,
    verificationReference, CounterpartyBindingStatus.valueOf(status.lowercase().replaceFirstChar(Char::uppercase)),
    verifiedAtEpochMillis,
)
