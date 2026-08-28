package com.budcom.android.feature.transaction.data.repository

import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.transaction.data.local.AuthenticatedCounterpartyBindingDao
import com.budcom.android.feature.transaction.data.local.AuthenticatedCounterpartyBindingEntity
import com.budcom.android.feature.transaction.domain.port.TransportAuthorityContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthenticatedCounterpartyBindingRepositoryImplTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }

    @Test
    fun `verified binding is namespace scoped persistent and revoked fail closed`() = runTest(dispatcher) {
        val dao = FakeBindingDao()
        val first = AuthenticatedCounterpartyBindingRepositoryImpl(dao, dispatchers)
        val verified = TransportAuthorityContext("seller-co", "actor-s", "device-s", setOf("send_orders"), 7)
        assertEquals("seller-co", first.recordVerified("buyer-co", "seller-party", verified, "trust:credential-1", 100)?.counterpartyBusinessId)
        assertEquals("seller-co", AuthenticatedCounterpartyBindingRepositoryImpl(dao, dispatchers)
            .resolveActive("buyer-co", "seller-party")?.counterpartyBusinessId)
        assertNull(first.resolveActive("third-co", "seller-party"))
        first.revoke("buyer-co", "seller-party")
        assertNull(first.resolveActive("buyer-co", "seller-party"))
    }

    @Test
    fun `unbound metadata and conflicting identity cannot grant business authority`() = runTest(dispatcher) {
        val repository = AuthenticatedCounterpartyBindingRepositoryImpl(FakeBindingDao(), dispatchers)
        assertNull(repository.resolveActive("buyer-co", "party-named-seller-co"))
        val verified = TransportAuthorityContext("seller-co", "actor-s", "device-s", emptySet(), 1)
        repository.recordVerified("buyer-co", "seller-party", verified, "trust:credential-1", 100)
        assertNull(repository.recordVerified(
            "buyer-co", "seller-party", verified.copy(businessId = "attacker-co"), "trust:credential-2", 101,
        ))
        assertNull(repository.recordVerified("buyer-co", "self-party", verified.copy(businessId = "buyer-co"), "trust:self", 100))
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
