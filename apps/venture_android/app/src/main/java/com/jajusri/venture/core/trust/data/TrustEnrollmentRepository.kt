package com.jajusri.venture.core.trust.data

import com.jajusri.venture.core.trust.data.remote.TrustApi
import com.jajusri.venture.core.trust.data.remote.TrustEnrollmentRequestDto
import com.jajusri.venture.core.trust.data.remote.TrustEnrollmentResponseDto
import com.jajusri.venture.core.trust.data.remote.TrustErrorResponseDto
import com.jajusri.venture.core.trust.domain.EnrollmentGrantProof
import com.jajusri.venture.core.trust.domain.StoredTrustCredential
import com.jajusri.venture.core.trust.domain.TrustCredentialStore
import com.jajusri.venture.core.trust.domain.TrustEnrollmentOutcome
import com.jajusri.venture.feature.transaction.domain.port.VartalapDeviceKeyStore
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates one enrollment attempt end to end: obtain/create this installation's
 * [VartalapDeviceKeyStore] identity (imported read-only from `feature/transaction/domain/port` --
 * this module never modifies anything under `feature/transaction`, see the round's STRICT
 * DO-NOT-TOUCH boundary), call Trust's real enrollment endpoint, and persist the resulting
 * credential via [TrustCredentialStore]. Never accepts or asserts a business/actor/membership --
 * the enrollment grant is the only proof of identity presented (mirrors
 * `backend/services/trust/src/application/consume-enrollment-grant.ts`'s own design).
 */
interface TrustEnrollmentRepository {
    suspend fun enroll(grant: EnrollmentGrantProof): TrustEnrollmentOutcome
}

@Singleton
class DefaultTrustEnrollmentRepository @Inject constructor(
    private val api: TrustApi,
    private val keyStore: VartalapDeviceKeyStore,
    private val credentialStore: TrustCredentialStore,
) : TrustEnrollmentRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun enroll(grant: EnrollmentGrantProof): TrustEnrollmentOutcome {
        val identity = keyStore.getCurrentIdentity() ?: keyStore.getOrCreateIdentity(UUID.randomUUID().toString())
        val body = TrustEnrollmentRequestDto(
            grantId = grant.grantId,
            grantSecret = grant.grantSecret,
            deviceId = identity.deviceId,
            deviceKeyId = identity.keyId,
            deviceKeyVersion = identity.keyVersion,
            publicKey = Base64.getEncoder().encodeToString(identity.publicKey),
            publicKeyFingerprint = identity.publicKeyFingerprint,
        )
        val response = try {
            api.consumeEnrollmentGrant(body)
        } catch (e: IOException) {
            // Covers both real transport failure AND `TrustDynamicBaseUrlInterceptor`'s own
            // "Trust endpoint is not configured." IOException -- both mean the same thing to a
            // caller: enrollment cannot proceed against Trust right now. `Response<T>` return type
            // (rather than a bare suspend body) means Retrofit never throws `HttpException` for a
            // non-2xx status -- that surfaces below via `response.isSuccessful` instead.
            return TrustEnrollmentOutcome.TrustUnavailable
        }
        if (!response.isSuccessful) return mapErrorBody(response.errorBody()?.string())
        val parsed = try {
            toStoredCredential(response.bodyOrThrow())
        } catch (e: SerializationException) {
            return TrustEnrollmentOutcome.MalformedResponse
        } catch (e: DateTimeParseException) {
            return TrustEnrollmentOutcome.MalformedResponse
        } catch (e: IllegalArgumentException) {
            return TrustEnrollmentOutcome.MalformedResponse
        }
        credentialStore.store(parsed)
        return TrustEnrollmentOutcome.Success(parsed)
    }

    private fun Response<TrustEnrollmentResponseDto>.bodyOrThrow(): TrustEnrollmentResponseDto =
        body() ?: throw SerializationException("Trust enrollment response had no body")

    private fun mapErrorBody(raw: String?): TrustEnrollmentOutcome {
        if (raw.isNullOrBlank()) return TrustEnrollmentOutcome.Unexpected("Trust enrollment failed with no error detail")
        return try {
            TrustEnrollmentOutcome.Rejected(json.decodeFromString(TrustErrorResponseDto.serializer(), raw).error.code)
        } catch (e: SerializationException) {
            TrustEnrollmentOutcome.MalformedResponse
        }
    }

    private fun toStoredCredential(dto: TrustEnrollmentResponseDto): StoredTrustCredential {
        val claims = dto.credential.claims
        return StoredTrustCredential(
            credentialVersion = claims.credentialVersion, credentialId = claims.credentialId, businessId = claims.businessId,
            actorId = claims.actorId, membershipId = claims.membershipId, deviceId = claims.deviceId, deviceKeyId = claims.deviceKeyId,
            deviceKeyVersion = claims.deviceKeyVersion, devicePublicKeyFingerprint = claims.devicePublicKeyFingerprint,
            authorityScope = claims.authorityScope, authorityEpoch = claims.authorityEpoch,
            issuedAtEpochMillis = Instant.parse(claims.issuedAt).toEpochMilli(), notBeforeEpochMillis = Instant.parse(claims.notBefore).toEpochMilli(),
            expiresAtEpochMillis = Instant.parse(claims.expiresAt).toEpochMilli(), issuerId = claims.issuerId, issuerKeyId = claims.issuerKeyId,
            signatureBase64 = dto.credential.signature,
        )
    }
}
