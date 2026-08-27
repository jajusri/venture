package com.budcom.android.feature.transaction.domain.model

import com.budcom.android.core.security.AuthorityEpochCache
import com.budcom.android.feature.transaction.domain.port.CredentialVerificationOutcome
import com.budcom.android.feature.transaction.domain.port.CredentialVerificationRequest
import com.budcom.android.feature.transaction.domain.port.RecipientBinding
import com.budcom.android.feature.transaction.domain.port.TransportCredentialVerifier
import com.budcom.android.feature.transaction.domain.port.TrustedBusinessDeviceCredential
import com.budcom.android.feature.transaction.domain.port.VartalapDeviceKeyStore

enum class CommercialAction {
    SellerConfirm,
    SellerRevise,
    RevisionSend,
    BuyerAcceptRevision,
    OpenReceived,
}

data class CommercialActionAuthorityContext(
    val businessId: String,
    val actorId: String,
    val deviceId: String,
    val credentialId: String,
    val credentialVersion: Int,
    val authorityEpoch: Long,
    val authorityScope: Set<String>,
    val orderId: String,
    val orderVersion: Int,
    val intendedAction: CommercialAction,
) {
    init {
        require(businessId.isNotBlank() && actorId.isNotBlank() && deviceId.isNotBlank())
        require(credentialId.isNotBlank())
        require(credentialVersion > 0)
        require(authorityEpoch >= 0)
        require(orderId.isNotBlank())
        require(orderVersion >= 1)
    }

    fun toConfirmAuthority(): OrderConfirmAuthority = OrderConfirmAuthority(
        businessId = businessId,
        actorId = actorId,
        deviceId = deviceId,
        authorityScope = authorityScope,
        authorityEpoch = authorityEpoch,
    )
}

data class CommercialActionAuthorityRequest(
    val action: CommercialAction,
    val viewerBusinessId: String,
    val expectedActorId: String?,
    val expectedDeviceId: String,
    val expectedDeviceKeyVersion: Int,
    val orderId: String,
    val orderVersion: Int,
    val inboxOrderId: String,
    val inboxOrderVersion: Int,
    val sellerBusinessId: String,
    val buyerBusinessId: String,
    val nowEpochMillis: Long,
)

sealed interface CommercialActionAuthorityOutcome {
    data class Verified(val context: CommercialActionAuthorityContext) : CommercialActionAuthorityOutcome
    data object Unavailable : CommercialActionAuthorityOutcome
    data object WrongBusiness : CommercialActionAuthorityOutcome
    data object WrongActor : CommercialActionAuthorityOutcome
    data object WrongDevice : CommercialActionAuthorityOutcome
    data object MissingScope : CommercialActionAuthorityOutcome
    data object Revoked : CommercialActionAuthorityOutcome
    data object Expired : CommercialActionAuthorityOutcome
    data object StaleEpoch : CommercialActionAuthorityOutcome
    data object WrongRole : CommercialActionAuthorityOutcome
    data object WrongOrderVersion : CommercialActionAuthorityOutcome
}

fun interface CommercialTrustCredentialSource {
    suspend fun trustedCredentialFor(businessId: String, deviceId: String): TrustedBusinessDeviceCredential?
}

fun interface CommercialActionAuthorityResolver {
    suspend fun resolve(request: CommercialActionAuthorityRequest): CommercialActionAuthorityOutcome
}

object CommercialActionAuthorityPolicy {
    fun requiredScope(action: CommercialAction): String? = when (action) {
        CommercialAction.SellerConfirm -> OrderConfirmAuthority.CONFIRM_ORDERS_CAPABILITY
        CommercialAction.SellerRevise, CommercialAction.RevisionSend -> OrderConfirmAuthority.REVISE_ORDERS_CAPABILITY
        CommercialAction.BuyerAcceptRevision -> OrderConfirmAuthority.ACCEPT_ORDER_REVISIONS_CAPABILITY
        CommercialAction.OpenReceived -> null
    }

    fun rolePermitted(request: CommercialActionAuthorityRequest): Boolean {
        if (request.orderId.isBlank() || request.orderVersion < 1) return false
        if (request.sellerBusinessId == request.buyerBusinessId) return false
        return when (request.action) {
            CommercialAction.SellerConfirm, CommercialAction.SellerRevise, CommercialAction.RevisionSend ->
                request.viewerBusinessId == request.sellerBusinessId && request.viewerBusinessId != request.buyerBusinessId
            CommercialAction.BuyerAcceptRevision ->
                request.viewerBusinessId == request.buyerBusinessId && request.viewerBusinessId != request.sellerBusinessId
            CommercialAction.OpenReceived ->
                request.viewerBusinessId == request.sellerBusinessId || request.viewerBusinessId == request.buyerBusinessId
        }
    }
}

object EmptyCommercialTrustCredentialSource : CommercialTrustCredentialSource {
    override suspend fun trustedCredentialFor(businessId: String, deviceId: String): TrustedBusinessDeviceCredential? = null
}

class TrustVerifiedCommercialActionAuthorityResolver(
    private val credentials: CommercialTrustCredentialSource,
    private val verifier: TransportCredentialVerifier,
    private val keyStore: VartalapDeviceKeyStore,
    private val epochs: AuthorityEpochCache,
) : CommercialActionAuthorityResolver {
    override suspend fun resolve(request: CommercialActionAuthorityRequest): CommercialActionAuthorityOutcome {
        if (request.orderId.isBlank() || request.orderVersion < 1) return CommercialActionAuthorityOutcome.WrongOrderVersion
        if (request.orderId != request.inboxOrderId || request.orderVersion != request.inboxOrderVersion) {
            return CommercialActionAuthorityOutcome.WrongOrderVersion
        }
        if (!CommercialActionAuthorityPolicy.rolePermitted(request)) return CommercialActionAuthorityOutcome.WrongRole
        val identity = keyStore.getCurrentIdentity() ?: return CommercialActionAuthorityOutcome.Unavailable
        if (identity.deviceId != request.expectedDeviceId || identity.keyVersion != request.expectedDeviceKeyVersion) {
            return CommercialActionAuthorityOutcome.WrongDevice
        }
        val credential = credentials.trustedCredentialFor(request.viewerBusinessId, identity.deviceId)
            ?: return CommercialActionAuthorityOutcome.Unavailable
        val currentEpoch = epochs.currentEpoch(credential.businessId, credential.membershipId, credential.deviceId)
            ?: return CommercialActionAuthorityOutcome.Unavailable
        if (currentEpoch != credential.authorityEpoch) return CommercialActionAuthorityOutcome.StaleEpoch
        val binding = RecipientBinding(
            businessId = request.viewerBusinessId,
            partyId = request.viewerBusinessId,
            mailboxReference = LOCAL_COMMERCIAL_ACTION_MAILBOX,
        )
        val verification = verifier.verify(
            CredentialVerificationRequest(
                credential = credential,
                expectedBusinessId = request.viewerBusinessId,
                expectedActorId = request.expectedActorId,
                expectedDeviceId = request.expectedDeviceId,
                expectedDeviceKeyVersion = request.expectedDeviceKeyVersion,
                actualRecipient = binding,
                expectedRecipient = binding,
                nowEpochMillis = request.nowEpochMillis,
            ),
        )
        return when (val mapped = mapVerification(verification, credential, request)) {
            is CommercialActionAuthorityOutcome.Verified -> {
                val required = CommercialActionAuthorityPolicy.requiredScope(request.action)
                if (required != null && required !in mapped.context.authorityScope) {
                    CommercialActionAuthorityOutcome.MissingScope
                } else {
                    mapped
                }
            }
            else -> mapped
        }
    }

    private fun mapVerification(
        outcome: CredentialVerificationOutcome,
        credential: TrustedBusinessDeviceCredential,
        request: CommercialActionAuthorityRequest,
    ): CommercialActionAuthorityOutcome = when (outcome) {
        is CredentialVerificationOutcome.Valid -> CommercialActionAuthorityOutcome.Verified(
            CommercialActionAuthorityContext(
                businessId = outcome.authorityContext.businessId,
                actorId = outcome.authorityContext.actorId,
                deviceId = outcome.authorityContext.deviceId,
                credentialId = credential.credentialId,
                credentialVersion = credential.credentialVersion,
                authorityEpoch = outcome.authorityContext.authorityEpoch,
                authorityScope = outcome.authorityContext.authorityScope,
                orderId = request.orderId,
                orderVersion = request.orderVersion,
                intendedAction = request.action,
            ),
        )
        CredentialVerificationOutcome.Expired -> CommercialActionAuthorityOutcome.Expired
        CredentialVerificationOutcome.Revoked -> CommercialActionAuthorityOutcome.Revoked
        CredentialVerificationOutcome.WrongBusiness -> CommercialActionAuthorityOutcome.WrongBusiness
        CredentialVerificationOutcome.WrongActor -> CommercialActionAuthorityOutcome.WrongActor
        CredentialVerificationOutcome.WrongDevice -> CommercialActionAuthorityOutcome.WrongDevice
        CredentialVerificationOutcome.WrongRecipient -> CommercialActionAuthorityOutcome.Unavailable
        CredentialVerificationOutcome.InvalidSignature -> CommercialActionAuthorityOutcome.Unavailable
        CredentialVerificationOutcome.UnsupportedVersion -> CommercialActionAuthorityOutcome.Unavailable
        CredentialVerificationOutcome.TemporarilyUnverifiable -> CommercialActionAuthorityOutcome.Unavailable
    }

    private companion object {
        const val LOCAL_COMMERCIAL_ACTION_MAILBOX = "local-commercial-action"
    }
}
