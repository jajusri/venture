package com.jajusri.venture.feature.pairing.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanAttemptGuardTest {

    // 1. one valid QR result is forwarded once
    @Test
    fun `a captured payload is forwarded exactly once`() {
        val guard = ScanAttemptGuard()
        guard.begin()

        val first = guard.onRawResult("secure-pairing-payload")

        assertTrue(first is SecurePairingScanResult.PayloadCaptured)
        assertEquals("secure-pairing-payload", (first as SecurePairingScanResult.PayloadCaptured).rawPayload)
    }

    // 2. duplicate frames are ignored
    @Test
    fun `a second raw result for the same attempt is ignored`() {
        val guard = ScanAttemptGuard()
        guard.begin()
        guard.onRawResult("first-payload")

        val second = guard.onRawResult("second-payload")

        assertNull(second)
    }

    @Test
    fun `beginning a new attempt allows exactly one more result`() {
        val guard = ScanAttemptGuard()
        guard.begin()
        guard.onRawResult("first-payload")

        guard.begin()
        val afterNewAttempt = guard.onRawResult("second-payload")

        assertTrue(afterNewAttempt is SecurePairingScanResult.PayloadCaptured)
    }

    // 3. oversized payload rejected before parser invocation
    @Test
    fun `an oversized payload is rejected as Failed, not forwarded as PayloadCaptured`() {
        val guard = ScanAttemptGuard()
        guard.begin()
        val oversized = "x".repeat(5_000)

        val result = guard.onRawResult(oversized)

        assertTrue(result is SecurePairingScanResult.Failed)
    }

    @Test
    fun `a payload at exactly the size bound is still accepted`() {
        val guard = ScanAttemptGuard()
        guard.begin()
        val atBound = "x".repeat(ScanAttemptGuard.MAX_RAW_PAYLOAD_BYTES)

        val result = guard.onRawResult(atBound)

        assertTrue(result is SecurePairingScanResult.PayloadCaptured)
    }

    // 4. cancellation produces Cancelled
    @Test
    fun `a null raw result produces Cancelled`() {
        val guard = ScanAttemptGuard()
        guard.begin()

        val result = guard.onRawResult(null)

        assertEquals(SecurePairingScanResult.Cancelled, result)
    }

    // 5. permission denial produces PermissionDenied
    @Test
    fun `a permission-denied outcome is forwarded once`() {
        val guard = ScanAttemptGuard()
        guard.begin()

        val result = guard.onOutcome(SecurePairingScanResult.PermissionDenied(permanentlyDenied = false))

        assertEquals(SecurePairingScanResult.PermissionDenied(false), result)
    }

    // 6. camera unavailable produces CameraUnavailable
    @Test
    fun `a camera-unavailable outcome is forwarded once`() {
        val guard = ScanAttemptGuard()
        guard.begin()

        val result = guard.onOutcome(SecurePairingScanResult.CameraUnavailable)

        assertEquals(SecurePairingScanResult.CameraUnavailable, result)
    }

    @Test
    fun `a duplicate outcome callback for an already-resolved attempt is ignored`() {
        val guard = ScanAttemptGuard()
        guard.begin()
        guard.onOutcome(SecurePairingScanResult.CameraUnavailable)

        val second = guard.onOutcome(SecurePairingScanResult.PermissionDenied(false))

        assertNull(second)
    }

    // 7. scanner failure exposes only sanitized reason
    @Test
    fun `an oversized-payload failure carries only a sanitized reason, never the raw content`() {
        val guard = ScanAttemptGuard()
        guard.begin()
        val secretLookingPayload = "SECRET-" + "x".repeat(5_000)

        val result = guard.onRawResult(secretLookingPayload) as SecurePairingScanResult.Failed

        assertTrue(result.sanitizedReason.isNotBlank())
        assertTrue(!result.sanitizedReason.contains("SECRET"))
    }

    // 8. no payload appears in logs — verified via toString() redaction, the only place a payload
    // could accidentally reach a log line through this result type.
    @Test
    fun `PayloadCaptured toString never exposes the raw payload`() {
        val result: SecurePairingScanResult = SecurePairingScanResult.PayloadCaptured("super-secret-qr-json")

        assertEquals("SecurePairingScanResult.PayloadCaptured(redacted)", result.toString())
    }

    @Test
    fun `a fresh guard before any begin() ignores results (nothing armed yet)`() {
        val guard = ScanAttemptGuard()

        val result = guard.onRawResult("should-be-ignored")

        assertNull(result)
    }
}
