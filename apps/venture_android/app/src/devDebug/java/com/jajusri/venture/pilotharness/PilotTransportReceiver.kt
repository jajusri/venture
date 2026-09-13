package com.jajusri.venture.pilotharness

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jajusri.venture.core.trust.domain.TrustCredentialStore
import com.jajusri.venture.feature.party.domain.model.ProspectDraft
import com.jajusri.venture.feature.party.domain.usecase.CreateProspectUseCase
import com.jajusri.venture.feature.transaction.domain.model.AuthenticatedCounterpartyBindingRepository
import com.jajusri.venture.feature.transaction.domain.model.CanonicalOrderState
import com.jajusri.venture.feature.transaction.domain.model.CommercialAction
import com.jajusri.venture.feature.transaction.domain.model.CommercialActionAuthorityRequest
import com.jajusri.venture.feature.transaction.domain.model.CommercialTrustCredentialSource
import com.jajusri.venture.feature.transaction.domain.model.OrderRevisionLineChange
import com.jajusri.venture.feature.transaction.domain.model.OrderStructuredOpenEvent
import com.jajusri.venture.feature.transaction.domain.model.TransactionSubmissionType
import com.jajusri.venture.feature.transaction.domain.model.TransactionTimestamp
import com.jajusri.venture.feature.transaction.domain.model.TransactionTimestampSource
import com.jajusri.venture.feature.transaction.domain.model.TransactionDraft
import com.jajusri.venture.feature.transaction.domain.model.TransactionDraftLine
import com.jajusri.venture.feature.transaction.domain.model.TransactionDraftPriceState
import com.jajusri.venture.feature.transaction.domain.port.CredentialVerificationRequest
import com.jajusri.venture.feature.transaction.domain.port.RecipientBinding
import com.jajusri.venture.feature.transaction.domain.port.RelayOutboxDispatcher
import com.jajusri.venture.feature.transaction.domain.port.RelayRecipientInboxIngester
import com.jajusri.venture.feature.transaction.domain.port.TrustedBusinessDeviceCredential
import com.jajusri.venture.feature.transaction.domain.port.VartalapDeviceKeyStore
import com.jajusri.venture.feature.transaction.domain.repository.StructuredRecipientInboxRepository
import com.jajusri.venture.feature.transaction.domain.repository.TransactionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64
import java.util.UUID
import javax.inject.Inject

/**
 * DEV-FLAVOR + DEBUG-BUILD-TYPE ONLY (see this source set's own AndroidManifest.xml, and
 * PilotEnrollmentReceiver's doc comment for the shared design rationale -- same file-based
 * payload/result contract, same non-exported-is-unreachable-from-adb finding, same "no secret in
 * argv/log" discipline).
 *
 * Drives the REAL, UNMODIFIED production Relay transport and commercial-authority APIs for a
 * controlled-pilot two-phone proof -- it does not implement Submit/Fetch/Ack/authority semantics of
 * its own anywhere in this file. Every action below is a direct call to an existing, certified,
 * Hilt-injected production class:
 *  - `get-identity`: reads this device's own enrolled [TrustCredentialStore]/[VartalapDeviceKeyStore]
 *    state and its own [CommercialTrustCredentialSource]-issued [TrustedBusinessDeviceCredential] --
 *    the SAME credential object production code already treats as "public/verifiable material by
 *    design" (see `TrustCredentialStore`'s own doc comment) meant to be presented to a counterparty
 *    for real cryptographic verification. Never the private Keystore key, which never leaves
 *    [VartalapDeviceKeyStore].
 *  - `bind-counterparty`: creates one local, purely-informational [ProspectDraft]-backed Party (no
 *    network, no authority) via the existing [CreateProspectUseCase], then calls the existing,
 *    already-certified [AuthenticatedCounterpartyBindingRepository.verifyAndRecord] with the peer's
 *    real credential -- this performs REAL ECDSA verification against Trust's real, live issuer
 *    keys and a real authority-epoch freshness check (`CachedTransportCredentialVerifier`,
 *    unmodified). A bad/forged/stale peer credential is rejected by that existing code exactly as it
 *    would be in production; this harness cannot make it succeed.
 *  - `submit-order`: `TransactionRepository.createDraftOrder` + `enqueueOrderDelivery` +
 *    `RelayOutboxDispatcher.submitPending` -- the exact sequence `TransactionComposerViewModel`
 *    itself calls, ending in a real signed HTTP POST to the running Relay.
 *  - `ingest-inbox`: `RelayRecipientInboxIngester.ingestPending` -- the exact production fetch +
 *    materialize + real signed acknowledge sequence, unmodified.
 *  - `seen-order`/`confirm-order`/`propose-revision`/`accept-revision`: thin wrappers around
 *    `TransactionRepository.recordOrderSeenFromOpenEvent`/`recordOrderConfirmFromSellerAction`/
 *    `proposeOrderRevision`+`enqueueOrderDelivery`+`markRevisionSent`/
 *    `recordOrderRevisionAcceptFromBuyerAction` -- each act-and-send half of the Seen/Confirm and
 *    Revision/Accept commercial lifecycle. The receive-and-apply half of each is already fully
 *    automatic inside the existing `ingest-inbox` op's `RelayRecipientInboxIngester` ->
 *    `TransactionRepository.ingestReceivedCommercialEvent` path (unmodified production code), so no
 *    separate "apply" op is needed here.
 *
 * Only non-secret evidence is ever written to [RESULT_FILE] (ids, states) except where the whole
 * point of the operation is to carry a real credential to be handed to the OTHER device for real
 * verification (`get-identity`'s response) -- that value is still never logged (no `Log.*`/
 * `println` anywhere in this class) and is deleted from this app's own storage the moment it is
 * read back by the orchestration script.
 */
@AndroidEntryPoint
class PilotTransportReceiver : BroadcastReceiver() {

    @Inject lateinit var trustCredentialStore: TrustCredentialStore
    @Inject lateinit var keyStore: VartalapDeviceKeyStore
    @Inject lateinit var commercialCredentialSource: CommercialTrustCredentialSource
    @Inject lateinit var counterpartyBindings: AuthenticatedCounterpartyBindingRepository
    @Inject lateinit var createProspect: CreateProspectUseCase
    @Inject lateinit var transactionRepository: TransactionRepository
    @Inject lateinit var relayOutboxDispatcher: RelayOutboxDispatcher
    @Inject lateinit var inboxIngester: RelayRecipientInboxIngester
    @Inject lateinit var structuredInbox: StructuredRecipientInboxRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PILOT_TRANSPORT) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { runOp(context) } finally { pendingResult.finish() }
        }
    }

    private suspend fun runOp(context: Context) {
        val payloadFile = File(context.filesDir, PAYLOAD_FILE)
        val resultFile = File(context.filesDir, RESULT_FILE)
        val json = Json { ignoreUnknownKeys = true }
        if (!payloadFile.exists()) {
            resultFile.writeText(json.encodeToString(PilotTransportResult.serializer(), PilotTransportResult(outcome = "no_payload_present")))
            return
        }
        val raw = payloadFile.readText()
        payloadFile.delete()

        val request = try {
            json.decodeFromString(PilotTransportRequest.serializer(), raw)
        } catch (e: Exception) {
            resultFile.writeText(json.encodeToString(PilotTransportResult.serializer(), PilotTransportResult(outcome = "malformed_payload")))
            return
        }

        val result = try {
            when (request.op) {
                "get-identity" -> handleGetIdentity()
                "bind-counterparty" -> handleBindCounterparty(request)
                "submit-order" -> handleSubmitOrder(request)
                "ingest-inbox" -> handleIngestInbox(request)
                "seen-order" -> handleSeenOrder(request)
                "confirm-order" -> handleConfirmOrder(request)
                "propose-revision" -> handleProposeRevision(request)
                "accept-revision" -> handleAcceptRevision(request)
                else -> PilotTransportResult(outcome = "unknown_op:${request.op}")
            }
        } catch (e: Exception) {
            PilotTransportResult(outcome = "exception:${e::class.simpleName}:${e.message}")
        }
        resultFile.writeText(json.encodeToString(PilotTransportResult.serializer(), result))
    }

    private suspend fun handleGetIdentity(): PilotTransportResult {
        val stored = trustCredentialStore.current() ?: return PilotTransportResult(outcome = "not_enrolled")
        val identity = keyStore.getCurrentIdentity() ?: return PilotTransportResult(outcome = "no_keystore_identity")
        val credential = commercialCredentialSource.trustedCredentialFor(stored.businessId, identity.deviceId)
            ?: return PilotTransportResult(outcome = "no_verifiable_credential")
        return PilotTransportResult(
            outcome = "success", businessId = stored.businessId, actorId = stored.actorId, deviceId = stored.deviceId,
            deviceKeyVersion = stored.deviceKeyVersion, membershipId = stored.membershipId,
            credential = credential.toWire(),
        )
    }

    private suspend fun handleBindCounterparty(request: PilotTransportRequest): PilotTransportResult {
        val stored = trustCredentialStore.current() ?: return PilotTransportResult(outcome = "not_enrolled")
        val peerWire = request.peerCredential ?: return PilotTransportResult(outcome = "missing_peer_credential")
        val peerCredential = peerWire.toDomain()
        val displayName = request.peerDisplayName ?: peerCredential.businessId
        val party = createProspect(stored.businessId, ProspectDraft(displayName = displayName))
        val now = System.currentTimeMillis()
        val binding = RecipientBinding(businessId = stored.businessId, partyId = party.partyId, mailboxReference = null)
        val verificationRequest = CredentialVerificationRequest(
            credential = peerCredential, expectedBusinessId = peerCredential.businessId, expectedActorId = null,
            expectedDeviceId = peerCredential.deviceId, expectedDeviceKeyVersion = peerCredential.deviceKeyVersion,
            actualRecipient = binding, expectedRecipient = binding, nowEpochMillis = now,
        )
        val recorded = counterpartyBindings.verifyAndRecord(stored.businessId, party.partyId, verificationRequest, now)
            ?: return PilotTransportResult(outcome = "verification_failed", partyId = party.partyId)
        return PilotTransportResult(outcome = "success", partyId = party.partyId, bindingStatus = recorded.status.name)
    }

    private suspend fun handleSubmitOrder(request: PilotTransportRequest): PilotTransportResult {
        val stored = trustCredentialStore.current() ?: return PilotTransportResult(outcome = "not_enrolled")
        val identity = keyStore.getCurrentIdentity() ?: return PilotTransportResult(outcome = "no_keystore_identity")
        val buyerPartyId = request.buyerPartyId ?: return PilotTransportResult(outcome = "missing_buyer_party_id")
        val creationKey = request.creationKey ?: UUID.randomUUID().toString()
        val now = TransactionTimestamp(System.currentTimeMillis(), TransactionTimestampSource.DeviceLocalProvisional)
        val draft = TransactionDraft(
            companyId = stored.businessId, buyerPartyId = buyerPartyId, submissionType = TransactionSubmissionType.Estimate,
            lines = listOf(TransactionDraftLine(
                linkedProductId = "pilot-harness-test-product", snapshotProductName = request.productName ?: "Pilot Harness Test Product",
                snapshotUnit = "pcs", snapshotSku = null, quantity = request.quantity ?: "1",
                priceState = TransactionDraftPriceState.NoPriceSupplied,
            )),
        )
        val authorityRequest = CommercialActionAuthorityRequest(
            action = CommercialAction.BuyerCreateOrder, viewerBusinessId = stored.businessId, expectedActorId = null,
            expectedDeviceId = identity.deviceId, expectedDeviceKeyVersion = identity.keyVersion,
            orderId = "", orderVersion = 0, inboxOrderId = "", inboxOrderVersion = 0,
            sellerBusinessId = request.peerBusinessId ?: "", buyerBusinessId = stored.businessId, nowEpochMillis = now.epochMillis,
        )
        val order = transactionRepository.createDraftOrder(draft, creationKey, note = "controlled-pilot", timestamp = now, authorityRequest = authorityRequest)
        // createDraftOrder is idempotent on creationKey: an exact retry (same Business, same
        // authority, same immutable intent) correctly returns the SAME canonical order rather than
        // creating a duplicate -- but that order's state may have already progressed past
        // Draft/RevisionPending (e.g. a prior call with this same creationKey already reached
        // Relay). enqueueOrderDelivery() correctly refuses to re-enqueue delivery for a
        // non-deliverable state (Codex STOP: that invariant must not be weakened) -- an exact
        // retry of an already-delivered order must report the existing delivery outcome, not
        // attempt to re-deliver it.
        val envelopeId = if (order.state == CanonicalOrderState.Draft || order.state == CanonicalOrderState.RevisionPending) {
            val envelope = transactionRepository.enqueueOrderDelivery(order, now)
            relayOutboxDispatcher.submitPending(stored.businessId)
            envelope.envelopeId
        } else {
            null
        }
        val after = transactionRepository.findOrderDeliveryEnvelope(stored.businessId, order.orderId, order.version)
        return PilotTransportResult(
            outcome = "success", orderId = order.orderId, orderVersion = order.version,
            envelopeId = envelopeId ?: after?.envelopeId, transportState = after?.state?.name, transportError = after?.lastError,
        )
    }

    private suspend fun handleIngestInbox(request: PilotTransportRequest): PilotTransportResult {
        val stored = trustCredentialStore.current() ?: return PilotTransportResult(outcome = "not_enrolled")
        inboxIngester.ingestPending(stored.businessId)
        val entries = structuredInbox.findAll(stored.businessId)
        return PilotTransportResult(
            outcome = "success",
            receivedOrders = entries.map { PilotReceivedOrder(orderId = it.objectId, state = it.transportState.name, senderBusinessId = it.senderBusinessId) },
        )
    }

    /** Most recently ingested inbox delivery for [orderId] (or overall, if `orderId` is null) --
     * used to recover the inbound `envelopeId` an act-and-send op must reference (production's own
     * `recordOrderSeenFromOpenEvent`/`recordOrderConfirmFromSellerAction`/`proposeOrderRevision`/
     * `recordOrderRevisionAcceptFromBuyerAction` all require the envelope that carried the specific
     * order version being acted on, not a synthesized id). */
    private suspend fun findLatestReceivedEntry(companyId: String, orderId: String?) =
        structuredInbox.findAll(companyId)
            .let { entries -> if (orderId != null) entries.filter { it.objectId == orderId } else entries }
            .maxByOrNull { it.mailboxSequence }

    private suspend fun handleSeenOrder(request: PilotTransportRequest): PilotTransportResult {
        val stored = trustCredentialStore.current() ?: return PilotTransportResult(outcome = "not_enrolled")
        val identity = keyStore.getCurrentIdentity() ?: return PilotTransportResult(outcome = "no_keystore_identity")
        val entry = findLatestReceivedEntry(stored.businessId, request.orderId) ?: return PilotTransportResult(outcome = "no_received_order")
        val order = transactionRepository.findCanonicalOrderById(stored.businessId, entry.objectId)
            ?: return PilotTransportResult(outcome = "order_not_materialized")
        val sellerBusinessId = order.sellerBusinessId ?: return PilotTransportResult(outcome = "order_missing_seller_business")
        val buyerBusinessId = order.buyerBusinessId ?: return PilotTransportResult(outcome = "order_missing_buyer_business")
        val now = TransactionTimestamp(System.currentTimeMillis(), TransactionTimestampSource.DeviceLocalProvisional)
        val open = OrderStructuredOpenEvent(
            eventId = UUID.randomUUID().toString(),
            idempotencyKey = "pilot-seen:${entry.objectId}:v${entry.objectVersion}:${stored.businessId}",
            orderId = entry.objectId, orderVersion = entry.objectVersion, objectType = entry.objectType,
            viewerBusinessId = stored.businessId, viewerActorId = stored.actorId, viewerDeviceId = identity.deviceId,
            senderBusinessId = entry.senderBusinessId, openedAt = now,
        )
        val authorityRequest = CommercialActionAuthorityRequest(
            action = CommercialAction.ReturnSeen, viewerBusinessId = stored.businessId, expectedActorId = null,
            expectedDeviceId = identity.deviceId, expectedDeviceKeyVersion = identity.keyVersion,
            orderId = entry.objectId, orderVersion = entry.objectVersion,
            inboxOrderId = entry.objectId, inboxOrderVersion = entry.objectVersion,
            sellerBusinessId = sellerBusinessId, buyerBusinessId = buyerBusinessId, nowEpochMillis = now.epochMillis,
        )
        val event = transactionRepository.recordOrderSeenFromOpenEvent(stored.businessId, entry.envelopeId, open, authorityRequest)
            ?: return PilotTransportResult(outcome = "seen_rejected")
        relayOutboxDispatcher.submitPending(stored.businessId)
        return PilotTransportResult(outcome = "success", orderId = entry.objectId, orderVersion = entry.objectVersion, eventId = event.eventId)
    }

    private suspend fun handleConfirmOrder(request: PilotTransportRequest): PilotTransportResult {
        val stored = trustCredentialStore.current() ?: return PilotTransportResult(outcome = "not_enrolled")
        val identity = keyStore.getCurrentIdentity() ?: return PilotTransportResult(outcome = "no_keystore_identity")
        val entry = findLatestReceivedEntry(stored.businessId, request.orderId) ?: return PilotTransportResult(outcome = "no_received_order")
        val order = transactionRepository.findCanonicalOrderById(stored.businessId, entry.objectId)
            ?: return PilotTransportResult(outcome = "order_not_materialized")
        val sellerBusinessId = order.sellerBusinessId ?: return PilotTransportResult(outcome = "order_missing_seller_business")
        val buyerBusinessId = order.buyerBusinessId ?: return PilotTransportResult(outcome = "order_missing_buyer_business")
        val now = TransactionTimestamp(System.currentTimeMillis(), TransactionTimestampSource.DeviceLocalProvisional)
        val authorityRequest = CommercialActionAuthorityRequest(
            action = CommercialAction.SellerConfirm, viewerBusinessId = stored.businessId, expectedActorId = null,
            expectedDeviceId = identity.deviceId, expectedDeviceKeyVersion = identity.keyVersion,
            orderId = entry.objectId, orderVersion = entry.objectVersion,
            inboxOrderId = entry.objectId, inboxOrderVersion = entry.objectVersion,
            sellerBusinessId = sellerBusinessId, buyerBusinessId = buyerBusinessId, nowEpochMillis = now.epochMillis,
        )
        val event = transactionRepository.recordOrderConfirmFromSellerAction(
            stored.businessId, entry.envelopeId, authorityRequest, UUID.randomUUID().toString(),
            "pilot-confirm:${entry.objectId}:v${entry.objectVersion}:${stored.businessId}", now,
        ) ?: return PilotTransportResult(outcome = "confirm_rejected")
        relayOutboxDispatcher.submitPending(stored.businessId)
        return PilotTransportResult(outcome = "success", orderId = entry.objectId, orderVersion = entry.objectVersion, eventId = event.eventId)
    }

    private suspend fun handleProposeRevision(request: PilotTransportRequest): PilotTransportResult {
        val stored = trustCredentialStore.current() ?: return PilotTransportResult(outcome = "not_enrolled")
        val identity = keyStore.getCurrentIdentity() ?: return PilotTransportResult(outcome = "no_keystore_identity")
        val entry = findLatestReceivedEntry(stored.businessId, request.orderId) ?: return PilotTransportResult(outcome = "no_received_order")
        val baseline = transactionRepository.findCanonicalOrderById(stored.businessId, entry.objectId)
            ?: return PilotTransportResult(outcome = "order_not_materialized")
        val sellerBusinessId = baseline.sellerBusinessId ?: return PilotTransportResult(outcome = "order_missing_seller_business")
        val buyerBusinessId = baseline.buyerBusinessId ?: return PilotTransportResult(outcome = "order_missing_buyer_business")
        val now = TransactionTimestamp(System.currentTimeMillis(), TransactionTimestampSource.DeviceLocalProvisional)
        val proposedLines = baseline.lines.map { line ->
            OrderRevisionLineChange(
                lineId = line.lineId, linkedProductId = line.linkedProductId, snapshotProductName = line.snapshotProductName,
                snapshotUnit = line.snapshotUnit, snapshotSku = line.snapshotSku,
                quantity = request.quantity ?: line.quantity,
                unitPriceAmount = line.unitPriceAmount, unitPriceCurrencyCode = line.unitPriceCurrencyCode,
                priceState = line.priceState, lineTotalAmount = line.lineTotalAmount,
            )
        }
        val authorityRequest = CommercialActionAuthorityRequest(
            action = CommercialAction.SellerRevise, viewerBusinessId = stored.businessId, expectedActorId = null,
            expectedDeviceId = identity.deviceId, expectedDeviceKeyVersion = identity.keyVersion,
            orderId = baseline.orderId, orderVersion = baseline.version,
            inboxOrderId = entry.objectId, inboxOrderVersion = entry.objectVersion,
            sellerBusinessId = sellerBusinessId, buyerBusinessId = buyerBusinessId, nowEpochMillis = now.epochMillis,
        )
        val revised = transactionRepository.proposeOrderRevision(
            stored.businessId, entry.envelopeId, baseline, proposedLines, request.revisionReason, authorityRequest, now,
            "pilot-revise:${baseline.orderId}:v${baseline.version + 1}:${stored.businessId}",
        ) ?: return PilotTransportResult(outcome = "revision_rejected")
        val envelope = transactionRepository.enqueueOrderDelivery(revised, now)
        transactionRepository.markRevisionSent(stored.businessId, revised.orderId, envelope)
        relayOutboxDispatcher.submitPending(stored.businessId)
        return PilotTransportResult(outcome = "success", orderId = revised.orderId, orderVersion = revised.version, envelopeId = envelope.envelopeId)
    }

    private suspend fun handleAcceptRevision(request: PilotTransportRequest): PilotTransportResult {
        val stored = trustCredentialStore.current() ?: return PilotTransportResult(outcome = "not_enrolled")
        val identity = keyStore.getCurrentIdentity() ?: return PilotTransportResult(outcome = "no_keystore_identity")
        val entry = findLatestReceivedEntry(stored.businessId, request.orderId) ?: return PilotTransportResult(outcome = "no_received_order")
        val order = transactionRepository.findCanonicalOrderById(stored.businessId, entry.objectId)
            ?: return PilotTransportResult(outcome = "order_not_materialized")
        val sellerBusinessId = order.sellerBusinessId ?: return PilotTransportResult(outcome = "order_missing_seller_business")
        val buyerBusinessId = order.buyerBusinessId ?: return PilotTransportResult(outcome = "order_missing_buyer_business")
        val now = TransactionTimestamp(System.currentTimeMillis(), TransactionTimestampSource.DeviceLocalProvisional)
        val authorityRequest = CommercialActionAuthorityRequest(
            action = CommercialAction.BuyerAcceptRevision, viewerBusinessId = stored.businessId, expectedActorId = null,
            expectedDeviceId = identity.deviceId, expectedDeviceKeyVersion = identity.keyVersion,
            orderId = entry.objectId, orderVersion = entry.objectVersion,
            inboxOrderId = entry.objectId, inboxOrderVersion = entry.objectVersion,
            sellerBusinessId = sellerBusinessId, buyerBusinessId = buyerBusinessId, nowEpochMillis = now.epochMillis,
        )
        val event = transactionRepository.recordOrderRevisionAcceptFromBuyerAction(
            stored.businessId, entry.envelopeId, authorityRequest, UUID.randomUUID().toString(),
            "pilot-accept:${entry.objectId}:v${entry.objectVersion}:${stored.businessId}", now,
        ) ?: return PilotTransportResult(outcome = "accept_rejected")
        relayOutboxDispatcher.submitPending(stored.businessId)
        return PilotTransportResult(outcome = "success", orderId = entry.objectId, orderVersion = entry.objectVersion, eventId = event.eventId)
    }

    companion object {
        const val ACTION_PILOT_TRANSPORT = "com.jajusri.venture.dev.debug.action.PILOT_TRANSPORT"
        const val PAYLOAD_FILE = "pilot_transport.json"
        const val RESULT_FILE = "pilot_transport_result.json"
    }
}

private fun TrustedBusinessDeviceCredential.toWire() = PilotCredentialWire(
    credentialVersion = credentialVersion, credentialId = credentialId, businessId = businessId, actorId = actorId,
    membershipId = membershipId, deviceId = deviceId, deviceKeyId = deviceKeyId, deviceKeyVersion = deviceKeyVersion,
    devicePublicKeyFingerprint = devicePublicKeyFingerprint, authorityScope = authorityScope.toList(), authorityEpoch = authorityEpoch,
    issuedAtEpochMillis = issuedAtEpochMillis, notBeforeEpochMillis = notBeforeEpochMillis, expiresAtEpochMillis = expiresAtEpochMillis,
    issuerId = issuerId, issuerKeyId = issuerKeyId, signatureProfile = signatureProfile,
    signatureBase64 = Base64.getEncoder().encodeToString(signature),
)

private fun PilotCredentialWire.toDomain() = TrustedBusinessDeviceCredential(
    credentialVersion = credentialVersion, credentialId = credentialId, businessId = businessId, actorId = actorId,
    membershipId = membershipId, deviceId = deviceId, deviceKeyId = deviceKeyId, deviceKeyVersion = deviceKeyVersion,
    devicePublicKeyFingerprint = devicePublicKeyFingerprint, authorityScope = authorityScope.toSet(), authorityEpoch = authorityEpoch,
    issuedAtEpochMillis = issuedAtEpochMillis, notBeforeEpochMillis = notBeforeEpochMillis, expiresAtEpochMillis = expiresAtEpochMillis,
    issuerId = issuerId, issuerKeyId = issuerKeyId, signatureProfile = signatureProfile,
    signature = Base64.getDecoder().decode(signatureBase64),
)

@Serializable
data class PilotCredentialWire(
    val credentialVersion: Int, val credentialId: String, val businessId: String, val actorId: String,
    val membershipId: String, val deviceId: String, val deviceKeyId: String, val deviceKeyVersion: Int,
    val devicePublicKeyFingerprint: String, val authorityScope: List<String>, val authorityEpoch: Long,
    val issuedAtEpochMillis: Long, val notBeforeEpochMillis: Long, val expiresAtEpochMillis: Long,
    val issuerId: String, val issuerKeyId: String, val signatureProfile: String, val signatureBase64: String,
)

@Serializable
data class PilotTransportRequest(
    val op: String,
    val peerDisplayName: String? = null,
    val peerCredential: PilotCredentialWire? = null,
    val buyerPartyId: String? = null,
    val peerBusinessId: String? = null,
    val productName: String? = null,
    val quantity: String? = null,
    val creationKey: String? = null,
    val orderId: String? = null,
    val revisionReason: String? = null,
)

@Serializable
data class PilotReceivedOrder(val orderId: String, val state: String, val senderBusinessId: String)

@Serializable
data class PilotTransportResult(
    val outcome: String,
    val businessId: String? = null,
    val actorId: String? = null,
    val deviceId: String? = null,
    val deviceKeyVersion: Int? = null,
    val membershipId: String? = null,
    val credential: PilotCredentialWire? = null,
    val partyId: String? = null,
    val bindingStatus: String? = null,
    val orderId: String? = null,
    val orderVersion: Int? = null,
    val envelopeId: String? = null,
    val transportState: String? = null,
    val transportError: String? = null,
    val receivedOrders: List<PilotReceivedOrder>? = null,
    val eventId: String? = null,
)
