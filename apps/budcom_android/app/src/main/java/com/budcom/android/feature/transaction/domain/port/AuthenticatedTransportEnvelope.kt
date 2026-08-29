package com.budcom.android.feature.transaction.domain.port

/**
 * Authenticated Relay Submit envelope (relay-authority-repair, 2026-08-29 -- transport wire
 * contract gap fix). Carries the exact three-part proof the certified Relay verifier
 * (`PilotAuthorityVerifier.verifySubmission`, `backend/services/relay/src/application/
 * pilot-authority-verifier.ts`) requires: the full Trust-issued signed credential ([credential],
 * never a summarized/reconstructed one) plus a fresh Keystore [deviceSignature] over this
 * submission's own binding fields. [credential] is transmitted essentially as-is (see
 * `data/relay/HttpRelayClient.kt`'s wire encoding) -- Android never re-derives or re-signs the
 * credential's own claims/signature, only ever the device-possession signature below.
 */
data class AuthenticatedTransportEnvelope(
    val envelope: EnvelopeSubmission,
    val recipient: RecipientBinding,
    val credential: TrustedBusinessDeviceCredential,
    val deviceSignature: ByteArray,
    val commercialSnapshotCanonical: String = "",
    val commercialContentType: String = ORDER_SNAPSHOT_CONTENT_TYPE,
    val commercialContentVersion: Int = ORDER_SNAPSHOT_CONTENT_VERSION,
) {
    companion object {
        /**
         * Reproduces `pilotBindingSigningPayload()` (`backend/services/relay/src/devtools/
         * pilot-envelope.ts`) byte-for-byte: the same field order, the same backslash-then-pipe
         * escaping, applied to the same values a real Submit HTTP body carries. This is the ONLY
         * bytes the device signature below ever covers -- changing any bound field after signing
         * (Business, device, target mailbox, request id/version, or the commercial content/type/
         * version) invalidates the signature under the certified verifier.
         */
        fun bindingSigningBytes(
            envelope: EnvelopeSubmission,
            senderActorId: String,
            recipient: RecipientBinding,
            commercialSnapshotCanonical: String,
            commercialContentType: String,
            commercialContentVersion: Int,
        ): ByteArray = listOf(
            envelope.envelopeId, envelope.objectType, envelope.objectId, envelope.objectVersion,
            envelope.senderBusinessId, senderActorId, envelope.senderDeviceId,
            recipient.businessId.orEmpty(), recipient.mailboxReference.orEmpty(),
            commercialSnapshotCanonical, commercialContentType, commercialContentVersion,
        ).joinToString("|") { pilotWireEscape(it.toString()) }.toByteArray(Charsets.UTF_8)
    }
}

const val ORDER_SNAPSHOT_CONTENT_TYPE = "application/vnd.budcom.order-snapshot+json"
const val ORDER_SNAPSHOT_CONTENT_VERSION = 3

/** Matches `String(value).replaceAll('\\', '\\\\').replaceAll('|', '\\|')` in every one of the
 * certified backend's pipe-delimited signing-payload builders (`pilot-envelope.ts`). Backslash
 * MUST be escaped before pipe, else a value containing `\|` would double-escape differently on
 * each side and silently break cross-stack signature verification. */
internal fun pilotWireEscape(value: String): String = value.replace("\\", "\\\\").replace("|", "\\|")

/**
 * Authenticated Relay Fetch/Acknowledge request proof. Mirrors the certified backend's
 * `AuthenticatedRelayRequestWire` (`pilot-envelope.ts`) -- a Trust credential plus a device
 * signature over an action-specific canonical binding (see [bindingSigningBytes]), generalized
 * to cover both operations since neither has Submit's fixed top-level field set.
 */
data class AuthenticatedRelayRequest(
    val credential: TrustedBusinessDeviceCredential,
    val requestId: String,
    val timestampIso: String,
    val requestSignature: ByteArray,
) {
    companion object {
        private const val PROTOCOL_VERSION = 1

        /** Reproduces `authenticatedRequestSigningPayload()` (`pilot-envelope.ts`) byte-for-byte:
         * protocol version, action, the credential's OWN identity claims (never the raw local
         * Keystore identity -- see this function's callers), a request id (nonce), a timestamp, the
         * target resource, and ordered action-specific parameters. */
        fun bindingSigningBytes(
            action: String,
            credential: TrustedBusinessDeviceCredential,
            requestId: String,
            timestampIso: String,
            target: String,
            parameters: List<String>,
        ): ByteArray = (
            listOf(
                PROTOCOL_VERSION.toString(), action, credential.businessId, credential.actorId, credential.membershipId,
                credential.deviceId, credential.deviceKeyId, credential.deviceKeyVersion.toString(), requestId, timestampIso, target,
            ) + parameters
            ).joinToString("|") { pilotWireEscape(it) }.toByteArray(Charsets.UTF_8)
    }
}

class AuthenticatedEnvelopeBinder(private val keyStore: VartalapDeviceKeyStore) {
    suspend fun bind(
        envelope: EnvelopeSubmission,
        identity: DeviceSigningIdentity,
        credential: TrustedBusinessDeviceCredential,
        recipient: RecipientBinding,
        commercialSnapshotCanonical: String = "",
        commercialContentType: String = ORDER_SNAPSHOT_CONTENT_TYPE,
        commercialContentVersion: Int = ORDER_SNAPSHOT_CONTENT_VERSION,
    ): AuthenticatedTransportEnvelope? {
        if (identity.lifecycleStatus != DeviceKeyLifecycleStatus.Active || envelope.senderDeviceId != identity.deviceId ||
            credential.deviceId != identity.deviceId || credential.businessId != envelope.senderBusinessId ||
            envelope.recipientBusinessId != recipient.businessId || envelope.recipientPartyId != recipient.partyId
        ) {
            return null
        }
        val bytes = AuthenticatedTransportEnvelope.bindingSigningBytes(
            envelope, credential.actorId, recipient, commercialSnapshotCanonical, commercialContentType, commercialContentVersion,
        )
        val result = keyStore.sign(identity, bytes) as? DeviceSigningResult.Success ?: return null
        return AuthenticatedTransportEnvelope(
            envelope, recipient, credential, result.signature,
            commercialSnapshotCanonical, commercialContentType, commercialContentVersion,
        )
    }

    /** Shared by both Fetch and Acknowledge -- identical binding shape, only `action`/`target`/
     * `parameters` differ (see `AuthenticatedRequestBindingFields` on the certified verifier). Binds
     * to the CREDENTIAL's own identity claims, not the caller-supplied business/device id, so a
     * caller can never produce a request whose signed claims disagree with what it transmits. */
    suspend fun bindRequest(
        identity: DeviceSigningIdentity,
        credential: TrustedBusinessDeviceCredential,
        action: String,
        target: String,
        parameters: List<String>,
        requestId: String,
        timestampIso: String,
    ): AuthenticatedRelayRequest? {
        if (identity.lifecycleStatus != DeviceKeyLifecycleStatus.Active || credential.deviceId != identity.deviceId) return null
        val bytes = AuthenticatedRelayRequest.bindingSigningBytes(action, credential, requestId, timestampIso, target, parameters)
        val result = keyStore.sign(identity, bytes) as? DeviceSigningResult.Success ?: return null
        return AuthenticatedRelayRequest(credential, requestId, timestampIso, result.signature)
    }
}
