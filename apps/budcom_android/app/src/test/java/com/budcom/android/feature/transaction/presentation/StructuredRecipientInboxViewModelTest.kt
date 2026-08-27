package com.budcom.android.feature.transaction.presentation

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.transaction.domain.model.RecipientInboxTransportState
import com.budcom.android.feature.transaction.domain.model.StructuredRecipientInboxEntry
import com.budcom.android.feature.transaction.domain.model.TransactionTimestamp
import com.budcom.android.feature.transaction.domain.model.TransactionTimestampSource
import com.budcom.android.feature.transaction.domain.repository.StructuredRecipientInboxRepository
import com.budcom.android.navigation.ReceivedStructuredNavigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StructuredRecipientInboxViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `revision item opens revision route with preserved identifiers`() = runTest(dispatcher) {
        val inbox = FakeStructuredInboxRepository(
            listOf(
                inboxEntry("env-1", 1),
                inboxEntry("env-2", 2),
                inboxEntry("env-chat", 1, objectType = "CHAT_MESSAGE"),
            ),
        )
        val vm = StructuredRecipientInboxViewModel(inbox, FakeInboxCompanySession("buyer-co"))
        val routes = mutableListOf<StructuredRecipientInboxNavigation>()
        val job = launch { vm.navigation.collect { routes += it } }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.uiState.value.items.size)
        vm.onEvent(StructuredRecipientInboxEvent.OpenItem("env-2"))
        dispatcher.scheduler.advanceUntilIdle()
        val route = (routes.single() as StructuredRecipientInboxNavigation.OpenRoute).route
        assertTrue(route.startsWith("transaction/revision/"))
        assertTrue(route.contains("env-2"))
        assertTrue(route.contains("seller-co"))
        assertTrue(route.contains("order-1"))
        assertTrue(route.endsWith("/2"))
        job.cancel()
    }

    @Test
    fun `ordinary order opens received order route`() = runTest(dispatcher) {
        val inbox = FakeStructuredInboxRepository(listOf(inboxEntry("env-1", 1)))
        val vm = StructuredRecipientInboxViewModel(inbox, FakeInboxCompanySession("buyer-co"))
        val routes = mutableListOf<StructuredRecipientInboxNavigation>()
        val job = launch { vm.navigation.collect { routes += it } }
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(StructuredRecipientInboxEvent.OpenItem("env-1"))
        dispatcher.scheduler.advanceUntilIdle()
        val route = (routes.single() as StructuredRecipientInboxNavigation.OpenRoute).route
        assertEquals(ReceivedStructuredNavigation.routeFor(inboxEntry("env-1", 1)), route)
        job.cancel()
    }

    private fun inboxEntry(
        envelopeId: String,
        version: Int,
        objectType: String = ReceivedStructuredNavigation.OBJECT_TYPE_CANONICAL_ORDER,
    ) = StructuredRecipientInboxEntry(
        companyId = "buyer-co",
        envelopeId = envelopeId,
        idempotencyKey = "inbox-$envelopeId",
        objectType = objectType,
        objectId = "order-1",
        objectVersion = version,
        senderBusinessId = "seller-co",
        senderActorId = "actor-s",
        senderDeviceId = "device-s",
        mailboxSequence = version.toLong(),
        acceptanceId = "accept-$version",
        acceptedAt = TransactionTimestamp(1, TransactionTimestampSource.DeviceLocalProvisional),
        ingestedAt = TransactionTimestamp(2, TransactionTimestampSource.DeviceLocalProvisional),
        transportState = RecipientInboxTransportState.Received,
    )
}

private class FakeStructuredInboxRepository(
    private val entries: List<StructuredRecipientInboxEntry>,
) : StructuredRecipientInboxRepository {
    override suspend fun findByEnvelopeId(companyId: String, envelopeId: String) =
        entries.firstOrNull { it.companyId == companyId && it.envelopeId == envelopeId }

    override suspend fun findAll(companyId: String) = findPage(companyId, 50, 0)
    override suspend fun findPage(companyId: String, limit: Int, offset: Int) =
        entries.filter { it.companyId == companyId }.drop(offset).take(limit)

    override suspend fun loadMailboxCursor(companyId: String, mailboxId: String): String? = null

    override suspend fun saveMailboxCursor(companyId: String, mailboxId: String, cursor: String?) = Unit

    override suspend fun persistIfNew(
        companyId: String,
        item: com.budcom.android.feature.transaction.domain.port.RelayMailboxDeliveryItem,
        timestamp: TransactionTimestamp,
    ): StructuredRecipientInboxEntry? = null
}

private class FakeInboxCompanySession(initial: String) : CompanySessionPort {
    private val state = MutableStateFlow(initial)
    override fun observeSelectedCompanyId() = state
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(state.value, state.value))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> = error("not used")
}
