package com.jajusri.venture.feature.transaction.domain.port

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * Cross-stack contract proof, Android half (relay-authority-repair, 2026-08-29, Gate 5). Reads the
 * SAME golden fixture `backend/test/relay-authenticated-envelope-contract.test.ts` reads
 * (`shared/fixtures/relay/authenticated-envelope-golden-v1.json`) and proves Android's independent
 * [AuthenticatedTransportEnvelope.bindingSigningBytes]/[AuthenticatedRelayRequest.bindingSigningBytes]
 * reproduce the certified backend's own `pilotBindingSigningPayload()`/
 * `authenticatedRequestSigningPayload()` byte-for-byte, for real field values including pipe and
 * backslash characters that exercise the escaping rules.
 *
 * ECDSA signatures are not deterministic (a fresh random nonce per signature), so this file does not
 * -- and could not meaningfully -- assert byte-identical signatures across runtimes; the backend test
 * proves the crypto/verifier path (real keys, real ECDSA, real tamper rejection). This file proves
 * the canonicalization agreement that crypto path depends on: if these bytes ever silently diverge
 * between Kotlin and TypeScript, every signature becomes mutually unverifiable even though each side
 * privately signs/verifies its own bytes successfully -- exactly the class of bug that produced the
 * original transport wire contract gap (Android and Relay each built a shape the other could not
 * parse).
 */
class AuthenticatedEnvelopeGoldenFixtureTest {
    private val fixture = Json.parseToJsonElement(
        File("../../../shared/fixtures/relay/authenticated-envelope-golden-v1.json").readText(Charsets.UTF_8),
    ).jsonObject

    @Test fun `submit binding bytes match the certified backend's pilotBindingSigningPayload byte-for-byte`() {
        val b = fixture.getValue("submitBinding").jsonObject
        val envelope = EnvelopeSubmission(
            contractVersion = 1,
            envelopeId = b.getValue("envelopeId").jsonPrimitive.content,
            idempotencyKey = "unused-for-binding-bytes",
            objectType = b.getValue("objectType").jsonPrimitive.content,
            objectId = b.getValue("objectId").jsonPrimitive.content,
            objectVersion = b.getValue("objectVersion").jsonPrimitive.int,
            senderBusinessId = b.getValue("senderBusinessId").jsonPrimitive.content,
            senderDeviceId = b.getValue("senderDeviceId").jsonPrimitive.content,
            recipientPartyId = null,
            recipientBusinessId = b.getValue("recipientBusinessId").jsonPrimitive.content,
            createdAtEpochMillis = 0,
        )
        val recipient = RecipientBinding(
            businessId = b.getValue("recipientBusinessId").jsonPrimitive.content,
            partyId = null,
            mailboxReference = b.getValue("recipientMailboxId").jsonPrimitive.content,
        )
        val bytes = AuthenticatedTransportEnvelope.bindingSigningBytes(
            envelope, b.getValue("senderActorId").jsonPrimitive.content, recipient,
            b.getValue("commercialContent").jsonPrimitive.content,
            b.getValue("commercialContentType").jsonPrimitive.content,
            b.getValue("commercialContentVersion").jsonPrimitive.int,
        )
        assertEquals(
            fixture.getValue("submitBindingSigningBytesBase64").jsonPrimitive.content,
            Base64.getEncoder().encodeToString(bytes),
        )
    }

    @Test fun `mailbox fetch binding bytes match the certified backend's authenticatedRequestSigningPayload byte-for-byte`() {
        val b = fixture.getValue("fetchBinding").jsonObject
        val credential = credentialFromClaims()
        val parameters = b.getValue("parameters").jsonArray.map { it.jsonPrimitive.content }
        val bytes = AuthenticatedRelayRequest.bindingSigningBytes(
            action = b.getValue("action").jsonPrimitive.content,
            credential = credential,
            requestId = b.getValue("requestId").jsonPrimitive.content,
            timestampIso = b.getValue("timestamp").jsonPrimitive.content,
            target = b.getValue("target").jsonPrimitive.content,
            parameters = parameters,
        )
        assertEquals(
            fixture.getValue("fetchBindingSigningBytesBase64").jsonPrimitive.content,
            Base64.getEncoder().encodeToString(bytes),
        )
    }

    @Test fun `acknowledgement binding bytes match the certified backend's authenticatedRequestSigningPayload byte-for-byte`() {
        val b = fixture.getValue("ackBinding").jsonObject
        val credential = credentialFromClaims()
        val parameters = b.getValue("parameters").jsonArray.map { it.jsonPrimitive.content }
        val bytes = AuthenticatedRelayRequest.bindingSigningBytes(
            action = b.getValue("action").jsonPrimitive.content,
            credential = credential,
            requestId = b.getValue("requestId").jsonPrimitive.content,
            timestampIso = b.getValue("timestamp").jsonPrimitive.content,
            target = b.getValue("target").jsonPrimitive.content,
            parameters = parameters,
        )
        assertEquals(
            fixture.getValue("ackBindingSigningBytesBase64").jsonPrimitive.content,
            Base64.getEncoder().encodeToString(bytes),
        )
    }

    private fun credentialFromClaims(): TrustedBusinessDeviceCredential {
        val c = fixture.getValue("credentialClaims").jsonObject
        return TrustedBusinessDeviceCredential(
            credentialVersion = c.getValue("credentialVersion").jsonPrimitive.int,
            credentialId = c.getValue("credentialId").jsonPrimitive.content,
            businessId = c.getValue("businessId").jsonPrimitive.content,
            actorId = c.getValue("actorId").jsonPrimitive.content,
            membershipId = c.getValue("membershipId").jsonPrimitive.content,
            deviceId = c.getValue("deviceId").jsonPrimitive.content,
            deviceKeyId = c.getValue("deviceKeyId").jsonPrimitive.content,
            deviceKeyVersion = c.getValue("deviceKeyVersion").jsonPrimitive.int,
            devicePublicKeyFingerprint = c.getValue("devicePublicKeyFingerprint").jsonPrimitive.content,
            authorityScope = c.getValue("authorityScope").jsonArray.map { it.jsonPrimitive.content }.toSet(),
            authorityEpoch = c.getValue("authorityEpoch").jsonPrimitive.content.toLong(),
            issuedAtEpochMillis = 0, notBeforeEpochMillis = 0, expiresAtEpochMillis = 0,
            issuerId = c.getValue("issuerId").jsonPrimitive.content,
            issuerKeyId = c.getValue("issuerKeyId").jsonPrimitive.content,
            signatureProfile = "P256-SHA256-v1",
            signature = byteArrayOf(),
        )
    }
}
