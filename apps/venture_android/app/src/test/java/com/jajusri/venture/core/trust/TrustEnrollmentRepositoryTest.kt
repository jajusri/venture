package com.jajusri.venture.core.trust

import com.jajusri.venture.core.trust.data.DefaultTrustEnrollmentRepository
import com.jajusri.venture.core.trust.data.remote.TrustApi
import com.jajusri.venture.core.trust.data.remote.TrustCredentialClaimsDto
import com.jajusri.venture.core.trust.data.remote.TrustEnrolledDeviceDto
import com.jajusri.venture.core.trust.data.remote.TrustEnrollmentRequestDto
import com.jajusri.venture.core.trust.data.remote.TrustEnrollmentResponseDto
import com.jajusri.venture.core.trust.data.remote.TrustAuthorityEpochResponseDto
import com.jajusri.venture.core.trust.data.remote.TrustIssuedCredentialDto
import com.jajusri.venture.core.trust.data.remote.TrustVerificationKeysResponseDto
import com.jajusri.venture.core.trust.domain.EnrollmentGrantProof
import com.jajusri.venture.core.trust.domain.StoredTrustCredential
import com.jajusri.venture.core.trust.domain.TrustCredentialStore
import com.jajusri.venture.core.trust.domain.TrustCredentialReadOutcome
import com.jajusri.venture.core.trust.domain.TrustEnrollmentOutcome
import com.jajusri.venture.feature.transaction.domain.port.DeviceKeySecurityLevel
import com.jajusri.venture.feature.transaction.domain.port.DeviceSigningIdentity
import com.jajusri.venture.feature.transaction.domain.port.DeviceSigningResult
import com.jajusri.venture.feature.transaction.domain.port.VartalapDeviceKeyStore
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.Instant

private class FakeTrustApi(
    private val result: () -> Response<TrustEnrollmentResponseDto>,
) : TrustApi {
    var lastRequest: TrustEnrollmentRequestDto? = null
    override suspend fun consumeEnrollmentGrant(body: TrustEnrollmentRequestDto): Response<TrustEnrollmentResponseDto> {
        lastRequest = body
        return result()
    }
    override suspend fun getVerificationKeys(issuerId: String): Response<TrustVerificationKeysResponseDto> = error("not used")
    override suspend fun getCurrentAuthorityEpoch(businessId: String, membershipId: String, deviceId: String): Response<TrustAuthorityEpochResponseDto> = error("not used")
}

private class ThrowingTrustApi(private val error: Throwable) : TrustApi {
    override suspend fun consumeEnrollmentGrant(body: TrustEnrollmentRequestDto): Response<TrustEnrollmentResponseDto> = throw error
    override suspend fun getVerificationKeys(issuerId: String): Response<TrustVerificationKeysResponseDto> = error("not used")
    override suspend fun getCurrentAuthorityEpoch(businessId: String, membershipId: String, deviceId: String): Response<TrustAuthorityEpochResponseDto> = error("not used")
}

private class FakeDeviceKeyStore : VartalapDeviceKeyStore {
    private var identity: DeviceSigningIdentity? = null
    override suspend fun getCurrentIdentity(): DeviceSigningIdentity? = identity
    override suspend fun getOrCreateIdentity(deviceId: String): DeviceSigningIdentity {
        val created = DeviceSigningIdentity(
            deviceId = deviceId, keyId = "$deviceId-key-1", keyVersion = 1,
            publicKey = ByteArray(65) { 9 }, publicKeyFingerprint = "fingerprint-1",
            createdAtEpochMillis = 1_000L, securityLevel = DeviceKeySecurityLevel.HardwareBacked,
        )
        identity = created
        return created
    }
    override suspend fun rotate(deviceId: String): DeviceSigningIdentity = error("not used")
    override suspend fun inspect(deviceId: String, keyVersion: Int): DeviceSigningIdentity? = identity
    override suspend fun sign(identity: DeviceSigningIdentity, boundedBytes: ByteArray): DeviceSigningResult = error("not used")
    override suspend fun remove(deviceId: String, keyVersion: Int): Boolean = false
}

private class FakeEnrollmentCredentialStore : TrustCredentialStore {
    var stored: StoredTrustCredential? = null
    override suspend fun current(): StoredTrustCredential? = stored
    override suspend fun readOutcome(): TrustCredentialReadOutcome =
        stored?.let { TrustCredentialReadOutcome.Present(it) } ?: TrustCredentialReadOutcome.NoRecord
    override suspend fun store(credential: StoredTrustCredential) { stored = credential }
    override suspend fun clear() { stored = null }
}

private fun successResponse(): Response<TrustEnrollmentResponseDto> = Response.success(
    TrustEnrollmentResponseDto(
        device = TrustEnrolledDeviceDto("business-1", "device-1", 1, "fingerprint-1"),
        credential = TrustIssuedCredentialDto(
            claims = TrustCredentialClaimsDto(
                credentialVersion = 1, credentialId = "cred-1", businessId = "business-1", actorId = "actor-1", membershipId = "membership-1",
                deviceId = "device-1", deviceKeyId = "device-1-key-1", deviceKeyVersion = 1, devicePublicKeyFingerprint = "fingerprint-1",
                authorityScope = listOf("send_orders"), authorityEpoch = 1L,
                issuedAt = "2026-01-01T00:00:00.000Z", notBefore = "2026-01-01T00:00:00.000Z", expiresAt = "2026-01-01T01:00:00.000Z",
                issuerId = "issuer-1", issuerKeyId = "issuer-key-1",
            ),
            signature = "c2lnbmF0dXJl",
        ),
    ),
)

private fun errorResponse(httpStatus: Int, code: String): Response<TrustEnrollmentResponseDto> {
    val json = """{"error":{"code":"$code","message":"rejected","requestId":"req-1"}}"""
    return Response.error(httpStatus, json.toResponseBody("application/json".toMediaType()))
}

class TrustEnrollmentRepositoryTest {
    private val grant = EnrollmentGrantProof(grantId = "grant-1", grantSecret = "a-genuinely-long-enough-secret")

    @Test
    fun `successful enrollment stores the credential and returns Success`() = runTest {
        val credentialStore = FakeEnrollmentCredentialStore()
        val repository = DefaultTrustEnrollmentRepository(FakeTrustApi { successResponse() }, FakeDeviceKeyStore(), credentialStore)
        val outcome = repository.enroll(grant)
        assertTrue(outcome is TrustEnrollmentOutcome.Success)
        val stored = credentialStore.stored
        assertEquals("business-1", stored?.businessId)
        assertEquals("device-1", stored?.deviceId)
        assertEquals(Instant.parse("2026-01-01T01:00:00.000Z").toEpochMilli(), stored?.expiresAtEpochMillis)
    }

    @Test
    fun `an unknown or invalid grant is rejected with the server's own code`() = runTest {
        val repository = DefaultTrustEnrollmentRepository(FakeTrustApi { errorResponse(404, "grant_not_found") }, FakeDeviceKeyStore(), FakeEnrollmentCredentialStore())
        val outcome = repository.enroll(grant)
        assertEquals(TrustEnrollmentOutcome.Rejected("grant_not_found"), outcome)
    }

    @Test
    fun `an expired grant is rejected`() = runTest {
        val repository = DefaultTrustEnrollmentRepository(FakeTrustApi { errorResponse(410, "grant_expired") }, FakeDeviceKeyStore(), FakeEnrollmentCredentialStore())
        assertEquals(TrustEnrollmentOutcome.Rejected("grant_expired"), repository.enroll(grant))
    }

    @Test
    fun `an already-consumed grant is rejected`() = runTest {
        val repository = DefaultTrustEnrollmentRepository(FakeTrustApi { errorResponse(409, "grant_already_consumed") }, FakeDeviceKeyStore(), FakeEnrollmentCredentialStore())
        assertEquals(TrustEnrollmentOutcome.Rejected("grant_already_consumed"), repository.enroll(grant))
    }

    @Test
    fun `Trust being unreachable (IOException, including the unconfigured-endpoint interceptor failure) surfaces as TrustUnavailable`() = runTest {
        val repository = DefaultTrustEnrollmentRepository(ThrowingTrustApi(IOException("Trust endpoint is not configured.")), FakeDeviceKeyStore(), FakeEnrollmentCredentialStore())
        assertEquals(TrustEnrollmentOutcome.TrustUnavailable, repository.enroll(grant))
    }

    @Test
    fun `a response with an unparseable expiry timestamp is treated as malformed, not silently stored`() = runTest {
        val malformed = successResponse().let { response ->
            Response.success(response.body()!!.copy(credential = response.body()!!.credential.copy(claims = response.body()!!.credential.claims.copy(expiresAt = "not-a-date"))))
        }
        val credentialStore = FakeEnrollmentCredentialStore()
        val repository = DefaultTrustEnrollmentRepository(FakeTrustApi { malformed }, FakeDeviceKeyStore(), credentialStore)
        assertEquals(TrustEnrollmentOutcome.MalformedResponse, repository.enroll(grant))
        assertNull(credentialStore.stored)
    }

    @Test
    fun `enrollment reuses an existing device identity rather than generating a new one on a second attempt`() = runTest {
        val deviceKeyStore = FakeDeviceKeyStore()
        val api = FakeTrustApi { successResponse() }
        val repository = DefaultTrustEnrollmentRepository(api, deviceKeyStore, FakeEnrollmentCredentialStore())
        repository.enroll(grant)
        val firstDeviceId = api.lastRequest?.deviceId
        repository.enroll(EnrollmentGrantProof("grant-2", "another-long-enough-secret"))
        assertEquals(firstDeviceId, api.lastRequest?.deviceId)
    }
}
