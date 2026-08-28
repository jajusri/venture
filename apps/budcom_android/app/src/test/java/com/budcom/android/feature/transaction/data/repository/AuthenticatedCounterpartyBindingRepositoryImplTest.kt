package com.budcom.android.feature.transaction.data.repository

import com.budcom.android.core.security.CachedIssuerVerificationKey
import com.budcom.android.core.security.CachedTransportCredentialVerifier
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.transaction.data.local.AuthenticatedCounterpartyBindingDao
import com.budcom.android.feature.transaction.data.local.AuthenticatedCounterpartyBindingEntity
import com.budcom.android.feature.transaction.domain.model.AuthenticatedCounterpartyBindingRepository
import com.budcom.android.feature.transaction.domain.port.CredentialVerificationRequest
import com.budcom.android.feature.transaction.domain.port.RecipientBinding
import com.budcom.android.feature.transaction.domain.port.TrustedBusinessDeviceCredential
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

class AuthenticatedCounterpartyBindingRepositoryImplTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }

    @Test fun `signed credential establishes namespace scoped persistent binding idempotently`() = runTest(dispatcher) {
        val fixture = VerifiedCredentialFixture()
        val dao = FakeBindingDao()
        val first = repository(dao, fixture)
        val request = fixture.request()
        assertEquals("seller-co", first.verifyAndRecord("buyer-co", "seller-party", request, 100)?.counterpartyBusinessId)
        assertEquals("credential-1", first.resolveActive("buyer-co", "seller-party")?.verificationReference)
        assertEquals("seller-co", first.verifyAndRecord("buyer-co", "seller-party", request, 100)?.counterpartyBusinessId)
        assertEquals("seller-co", repository(dao, fixture).resolveActive("buyer-co", "seller-party")?.counterpartyBusinessId)
        assertNull(first.resolveActive("third-co", "seller-party"))
    }

    @Test fun `claimed authority empty wrong scope and identity tampering cannot establish binding`() = runTest(dispatcher) {
        val fixture = VerifiedCredentialFixture()
        val repository = repository(FakeBindingDao(), fixture)
        val forged = fixture.request(partyId = "forged-party").let {
            it.copy(credential = it.credential.copy(signature = byteArrayOf(1, 2, 3)))
        }
        assertNull(repository.verifyAndRecord("buyer-co", "forged-party", forged, 100))
        assertNull(repository.verifyAndRecord("buyer-co", "empty-party", fixture.request(scope = emptySet(), partyId = "empty-party"), 100))
        assertNull(repository.verifyAndRecord("buyer-co", "wrong-party", fixture.request(scope = setOf("confirm_orders"), partyId = "wrong-party"), 100))
        val signed = fixture.request(partyId = "tampered-party")
        assertNull(repository.verifyAndRecord("buyer-co", "tampered-party", signed.copy(
            credential = signed.credential.copy(businessId = "attacker-co"), expectedBusinessId = "attacker-co",
        ), 100))
        assertNull(repository.verifyAndRecord("buyer-co", "mismatch-party", fixture.request(partyId = "mismatch-party").copy(
            expectedBusinessId = "attacker-co",
        ), 100))
    }

    @Test fun `metadata payload self binding conflict and revoked replay fail closed`() = runTest(dispatcher) {
        val fixture = VerifiedCredentialFixture()
        val dao = FakeBindingDao()
        val repository = repository(dao, fixture)
        assertNull(repository.resolveActive("buyer-co", "party-named-seller-co"))
        assertNull(repository.resolveActive("buyer-co", "payload-claims-seller-co"))
        assertNull(repository.verifyAndRecord("seller-co", "self-party", fixture.request(localBusinessId = "seller-co", partyId = "self-party"), 100))
        assertEquals("seller-co", repository.verifyAndRecord("buyer-co", "seller-party", fixture.request(), 100)?.counterpartyBusinessId)
        assertNull(repository.verifyAndRecord("buyer-co", "seller-party", fixture.request(businessId = "other-co", credentialId = "credential-2"), 101))
        repository.revoke("buyer-co", "seller-party")
        assertNull(repository.resolveActive("buyer-co", "seller-party"))
        assertNull(repository.verifyAndRecord("buyer-co", "seller-party", fixture.request(), 100))
    }

    @Test fun `authoritative API has no caller constructible authority context write path`() {
        val writes = AuthenticatedCounterpartyBindingRepository::class.java.methods.filter { it.name.contains("record", true) }
        assertEquals(listOf("verifyAndRecord"), writes.map { it.name }.distinct())
        assertFalse(writes.flatMap { it.parameterTypes.asList() }.any { it.simpleName == "TransportAuthorityContext" })
        assertEquals(1, writes.flatMap { it.parameterTypes.asList() }.count { it.simpleName == "CredentialVerificationRequest" })
    }

    private fun repository(dao: FakeBindingDao, fixture: VerifiedCredentialFixture) =
        AuthenticatedCounterpartyBindingRepositoryImpl(dao, dispatchers, fixture.verifier)
}

private class VerifiedCredentialFixture {
    private val issuerKeys = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    val verifier = CachedTransportCredentialVerifier(
        { _, _ -> CachedIssuerVerificationKey("issuer-1", "issuer-key-1", "P256-SHA256-v1", issuerKeys.public.encoded, false) },
        { _, _, _ -> 7 },
    )
    fun request(
        businessId: String = "seller-co", credentialId: String = "credential-1",
        scope: Set<String> = setOf("send_orders"), localBusinessId: String = "buyer-co",
        partyId: String = "seller-party",
    ): CredentialVerificationRequest {
        val unsigned = TrustedBusinessDeviceCredential(
            1, credentialId, businessId, "actor-s", "member-s", "device-s", "device-key-s", 1, "fingerprint-s",
            scope, 7, 10, 10, 1_000, "issuer-1", "issuer-key-1", "P256-SHA256-v1", byteArrayOf(),
        )
        val signed = unsigned.copy(signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(issuerKeys.private); update(unsigned.signingBytes())
        }.sign())
        val recipient = RecipientBinding(localBusinessId, partyId, "binding")
        return CredentialVerificationRequest(signed, businessId, "actor-s", "device-s", 1, recipient, recipient, 100)
    }
}

private class FakeBindingDao : AuthenticatedCounterpartyBindingDao {
    private val rows = mutableMapOf<Pair<String, String>, AuthenticatedCounterpartyBindingEntity>()
    override suspend fun insert(entity: AuthenticatedCounterpartyBindingEntity) {
        check(rows.putIfAbsent(entity.localBusinessId to entity.partyId, entity) == null)
    }
    override suspend fun find(localBusinessId: String, partyId: String) = rows[localBusinessId to partyId]
    override suspend fun revoke(localBusinessId: String, partyId: String) {
        val key = localBusinessId to partyId
        rows[key] = rows[key]?.copy(status = "REVOKED") ?: return
    }
}
