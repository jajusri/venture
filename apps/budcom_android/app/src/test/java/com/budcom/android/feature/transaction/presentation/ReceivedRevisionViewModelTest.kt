package com.budcom.android.feature.transaction.presentation

import androidx.lifecycle.SavedStateHandle
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.transaction.domain.model.CanonicalOrder
import com.budcom.android.feature.transaction.domain.model.CanonicalOrderState
import com.budcom.android.feature.transaction.domain.model.CommercialAction
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityContext
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityOutcome
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityRequest
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityResolver
import com.budcom.android.feature.transaction.domain.model.OrderCommercialEvent
import com.budcom.android.feature.transaction.domain.model.OrderCommercialEventType
import com.budcom.android.feature.transaction.domain.model.OrderConfirmAuthority
import com.budcom.android.feature.transaction.domain.model.OrderRevisionAcceptEvidence
import com.budcom.android.feature.transaction.domain.model.OrderSeenEvidence
import com.budcom.android.feature.transaction.domain.model.OrderStructuredOpenEvent
import com.budcom.android.feature.transaction.domain.model.TransactionClock
import com.budcom.android.feature.transaction.domain.model.TransactionEntryPointType
import com.budcom.android.feature.transaction.domain.model.TransactionSubmissionType
import com.budcom.android.feature.transaction.domain.model.TransactionTimestamp
import com.budcom.android.feature.transaction.domain.model.TransactionTimestampSource
import com.budcom.android.feature.transaction.domain.port.DeviceKeySecurityLevel
import com.budcom.android.feature.transaction.domain.port.DeviceSigningIdentity
import com.budcom.android.feature.transaction.domain.port.DeviceSigningResult
import com.budcom.android.feature.transaction.domain.port.VartalapDeviceKeyStore
import com.budcom.android.feature.transaction.domain.model.CatalogueAccessGrant
import com.budcom.android.feature.transaction.domain.model.CommercialTransaction
import com.budcom.android.feature.transaction.domain.model.EstimatePo
import com.budcom.android.feature.transaction.domain.model.LedgerGroupChoice
import com.budcom.android.feature.transaction.domain.model.PaymentClaimStatus
import com.budcom.android.feature.transaction.domain.model.PaymentEvent
import com.budcom.android.feature.transaction.domain.model.SellerInboxEntry
import com.budcom.android.feature.transaction.domain.model.TermsAcknowledgment
import com.budcom.android.feature.transaction.domain.model.TransactionDeliveryChannel
import com.budcom.android.feature.transaction.domain.model.TransactionLineItem
import com.budcom.android.feature.transaction.domain.repository.AcceptSellerInboxEntryResult
import com.budcom.android.feature.transaction.domain.repository.NewLineItem
import com.budcom.android.feature.transaction.domain.repository.ProposedTerms
import com.budcom.android.feature.transaction.domain.repository.SellerInboxActionResult
import com.budcom.android.feature.transaction.domain.repository.TransactionSnapshot
import com.budcom.android.feature.transaction.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReceivedRevisionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `opening revision records seen and enables explicit accept`() = runTest(dispatcher) {
        val repository = RevisionAcceptFakeRepository()
        val vm = ReceivedRevisionViewModel(
            SavedStateHandle(
                mapOf(
                    ReceivedRevisionViewModel.ENVELOPE_ID_ARG to "env-2",
                    ReceivedRevisionViewModel.SENDER_BUSINESS_ID_ARG to "seller-co",
                    ReceivedRevisionViewModel.ORDER_ID_ARG to "order-1",
                    ReceivedRevisionViewModel.ORDER_VERSION_ARG to 2,
                ),
            ),
            repository,
            FakeRevisionCompanySessionPort("buyer-co"),
            FakeRevisionKeyStore(),
            FakeRevisionClock(),
            FakeVerifiedAuthorityResolver(),
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.canAcceptChanges)
        vm.onEvent(ReceivedRevisionEvent.AcceptChanges)
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.canAcceptChanges)
        assertEquals("Changes accepted.", vm.uiState.value.message)
        assertEquals(1, repository.acceptCalls)
    }
}

private class RevisionAcceptFakeRepository : TransactionRepository {
    var acceptCalls = 0

    private val revisionOrder = CanonicalOrder(
        companyId = "buyer-co", orderId = "order-1", creationKey = "k", sellerCompanyId = "seller-co", buyerPartyId = "buyer-co",
        state = CanonicalOrderState.RevisionSent, source = TransactionEntryPointType.Catalogue,
        submissionType = TransactionSubmissionType.Estimate, note = null,
        createdAt = TransactionTimestamp(1, TransactionTimestampSource.DeviceLocalProvisional), version = 2, lines = emptyList(),
    )

    override suspend fun recordOrderSeenFromOpenEvent(
        viewerCompanyId: String,
        envelopeId: String,
        open: OrderStructuredOpenEvent,
    ): OrderCommercialEvent = OrderCommercialEvent(
        companyId = viewerCompanyId,
        eventId = "seen-2", idempotencyKey = open.idempotencyKey, orderId = open.orderId, orderVersion = open.orderVersion,
        eventType = OrderCommercialEventType.Seen, actorBusinessId = viewerCompanyId, actorId = open.viewerActorId,
        actorDeviceId = open.viewerDeviceId, counterpartyBusinessId = open.senderBusinessId, occurredAt = open.openedAt,
    )

    override suspend fun findOrderSeenEvidence(companyId: String, orderId: String, orderVersion: Int): OrderSeenEvidence =
        OrderSeenEvidence(
            eventId = "seen-2", orderId = orderId, orderVersion = orderVersion, viewerBusinessId = companyId,
            viewerActorId = "actor-b", viewerDeviceId = "device-b", senderBusinessId = "seller-co",
            seenAt = TransactionTimestamp(2, TransactionTimestampSource.DeviceLocalProvisional),
        )

    override suspend fun applyOrderSeenEvidence(companyId: String, evidence: OrderSeenEvidence): CanonicalOrder =
        revisionOrder.copy(state = CanonicalOrderState.RevisionSeen)

    override suspend fun findCanonicalOrderById(companyId: String, orderId: String): CanonicalOrder? =
        revisionOrder.copy(state = CanonicalOrderState.RevisionSeen)

    override suspend fun recordOrderRevisionAcceptFromBuyerAction(
        buyerCompanyId: String,
        envelopeId: String,
        authority: OrderConfirmAuthority,
        eventId: String,
        idempotencyKey: String,
        timestamp: TransactionTimestamp,
    ): OrderCommercialEvent {
        acceptCalls += 1
        return OrderCommercialEvent(
            companyId = buyerCompanyId,
            eventId = eventId, idempotencyKey = idempotencyKey, orderId = "order-1", orderVersion = 2,
            eventType = OrderCommercialEventType.RevisionAccepted, actorBusinessId = buyerCompanyId, actorId = authority.actorId,
            actorDeviceId = authority.deviceId, counterpartyBusinessId = "seller-co", occurredAt = timestamp,
        )
    }

    override suspend fun applyOrderRevisionAcceptEvidence(companyId: String, evidence: OrderRevisionAcceptEvidence): CanonicalOrder =
        revisionOrder.copy(state = CanonicalOrderState.Confirmed)

    override suspend fun createEstimatePo(
        companyId: String,
        entryPointType: TransactionEntryPointType,
        submissionType: TransactionSubmissionType,
        deliveryChannel: TransactionDeliveryChannel,
        buyerPartyId: String?,
        lineItems: List<NewLineItem>,
        timestamp: TransactionTimestamp,
    ): EstimatePo = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("not used by ReceivedRevisionViewModelTest")

    override suspend fun findEstimatePoById(companyId: String, estimatePoId: String): EstimatePo? = unsupported()
    override suspend fun findSellerInboxEntry(companyId: String, inboxEntryId: String): SellerInboxEntry? = unsupported()
    override suspend fun findAllSellerInboxEntries(companyId: String): List<SellerInboxEntry> = unsupported()
    override suspend fun acknowledgeSellerInboxEntry(companyId: String, inboxEntryId: String, timestamp: TransactionTimestamp): SellerInboxActionResult = unsupported()
    override suspend fun requestChangesOnSellerInboxEntry(companyId: String, inboxEntryId: String, note: String, timestamp: TransactionTimestamp): SellerInboxActionResult = unsupported()
    override suspend fun acceptSellerInboxEntry(companyId: String, inboxEntryId: String, ledgerGroupChoice: LedgerGroupChoice, proposedTerms: ProposedTerms, timestamp: TransactionTimestamp): AcceptSellerInboxEntryResult = unsupported()
    override suspend fun findTransactionById(companyId: String, transactionId: String): CommercialTransaction? = unsupported()
    override suspend fun findTermsForTransaction(companyId: String, transactionId: String): TermsAcknowledgment? = unsupported()
    override suspend fun confirmTermsAsBuyer(companyId: String, transactionId: String, timestamp: TransactionTimestamp): TermsAcknowledgment? = unsupported()
    override suspend fun confirmTermsAsSeller(companyId: String, transactionId: String, timestamp: TransactionTimestamp): TermsAcknowledgment? = unsupported()
    override suspend fun findPaymentEventsForTransaction(companyId: String, transactionId: String): List<PaymentEvent> = unsupported()
    override suspend fun recordPaymentClaim(companyId: String, transactionId: String, claimedAmount: String, currencyCode: String?, isFinalOrPartial: PaymentClaimStatus, timestamp: TransactionTimestamp): PaymentEvent? = unsupported()
    override suspend fun confirmPaymentReceived(companyId: String, transactionId: String, paymentEventId: String, timestamp: TransactionTimestamp, discrepancyNote: String?): CommercialTransaction? = unsupported()
    override suspend fun findTransactionsForCounterparty(companyId: String, buyerPartyId: String): List<CommercialTransaction> = unsupported()
    override suspend fun findAllTransactionsForCompany(companyId: String): List<CommercialTransaction> = unsupported()
    override suspend fun findTransactionSnapshot(companyId: String, transactionId: String): TransactionSnapshot? = unsupported()
    override suspend fun findCompletedPurchaseHistory(companyId: String, buyerPartyId: String): List<TransactionLineItem> = unsupported()
    override suspend fun grantCatalogueAccess(companyId: String, buyerPartyId: String, expiresAt: TransactionTimestamp?, timestamp: TransactionTimestamp): CatalogueAccessGrant = unsupported()
    override suspend fun revokeCatalogueAccess(companyId: String, grantId: String, timestamp: TransactionTimestamp) = unsupported()
    override suspend fun findActiveCatalogueAccessGrant(companyId: String, buyerPartyId: String, nowEpochMillis: Long): CatalogueAccessGrant? = unsupported()
}

private class FakeRevisionCompanySessionPort(initial: String) : CompanySessionPort {
    private val state = MutableStateFlow(initial)
    override fun observeSelectedCompanyId() = state
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(state.value, state.value))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> = error("not used")
}

private class FakeRevisionKeyStore : VartalapDeviceKeyStore {
    private val identity = DeviceSigningIdentity(
        deviceId = "device-b", keyId = "key-b", keyVersion = 1, publicKey = byteArrayOf(1),
        publicKeyFingerprint = "fp", createdAtEpochMillis = 0, securityLevel = DeviceKeySecurityLevel.SecureKeystore,
    )
    override suspend fun getCurrentIdentity() = identity
    override suspend fun getOrCreateIdentity(deviceId: String) = identity
    override suspend fun rotate(deviceId: String) = identity
    override suspend fun inspect(deviceId: String, keyVersion: Int) = identity
    override suspend fun sign(identity: DeviceSigningIdentity, boundedBytes: ByteArray) =
        DeviceSigningResult.Success(boundedBytes)
    override suspend fun remove(deviceId: String, keyVersion: Int) = true
}

private class FakeRevisionClock : TransactionClock {
    override suspend fun now() = TransactionTimestamp(100, TransactionTimestampSource.DeviceLocalProvisional)
}

private class FakeVerifiedAuthorityResolver : CommercialActionAuthorityResolver {
    override suspend fun resolve(request: CommercialActionAuthorityRequest): CommercialActionAuthorityOutcome {
        val actor = if (request.action == CommercialAction.BuyerAcceptRevision || request.viewerBusinessId == "buyer-co") "actor-b" else "actor-s"
        return CommercialActionAuthorityOutcome.Verified(
            CommercialActionAuthorityContext(
                businessId = request.viewerBusinessId,
                actorId = actor,
                deviceId = request.expectedDeviceId,
                credentialId = "cred-test",
                credentialVersion = 1,
                authorityEpoch = 1,
                authorityScope = setOf(
                    com.budcom.android.feature.transaction.domain.model.OrderConfirmAuthority.CONFIRM_ORDERS_CAPABILITY,
                    com.budcom.android.feature.transaction.domain.model.OrderConfirmAuthority.REVISE_ORDERS_CAPABILITY,
                    com.budcom.android.feature.transaction.domain.model.OrderConfirmAuthority.ACCEPT_ORDER_REVISIONS_CAPABILITY,
                ),
                orderId = request.orderId,
                orderVersion = request.orderVersion,
                intendedAction = request.action,
            ),
        )
    }
}
