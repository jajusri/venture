package com.budcom.android.feature.transaction.domain.model

import com.budcom.android.core.security.CachedIssuerVerificationKey
import com.budcom.android.core.security.CachedTransportCredentialVerifier
import com.budcom.android.feature.transaction.domain.port.DeviceKeySecurityLevel
import com.budcom.android.feature.transaction.domain.port.DeviceSigningIdentity
import com.budcom.android.feature.transaction.domain.port.DeviceSigningResult
import com.budcom.android.feature.transaction.domain.port.TrustedBusinessDeviceCredential
import com.budcom.android.feature.transaction.domain.port.VartalapDeviceKeyStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature

class TrustVerifiedCommercialActionAuthorityResolverTest {
    @Test
    fun `signed buyer credential establishes creation authority and rejects wrong business scope forgery and revocation`() = runTest {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val buyerCredential = signed(keys, "buyer-co", "actor-b", "device-b", setOf("send_orders"))
        val valid = resolver(keys, buyerCredential, 1, "device-b").resolve(buyerCreation())
        assertTrue(valid is CommercialActionAuthorityOutcome.Verified)
        assertEquals("buyer-co", (valid as CommercialActionAuthorityOutcome.Verified).context.businessId)
        assertEquals(CommercialAction.BuyerCreateOrder, valid.context.intendedAction)

        val thirdCredential = signed(keys, "third-co", "actor-b", "device-b", setOf("send_orders"))
        assertEquals(CommercialActionAuthorityOutcome.WrongBusiness, resolver(keys, thirdCredential, 1, "device-b").resolve(buyerCreation()))
        val wrongScope = signed(keys, "buyer-co", "actor-b", "device-b", setOf("confirm_orders"))
        assertEquals(CommercialActionAuthorityOutcome.MissingScope, resolver(keys, wrongScope, 1, "device-b").resolve(buyerCreation()))
        assertEquals(CommercialActionAuthorityOutcome.Unavailable, resolver(keys, null, 1, "device-b").resolve(buyerCreation()))
        assertEquals(CommercialActionAuthorityOutcome.Revoked, resolver(keys, buyerCredential, 1, "device-b", keyRevoked = true).resolve(buyerCreation()))
        val forged = buyerCredential.copy(signature = byteArrayOf(1, 2, 3))
        assertEquals(CommercialActionAuthorityOutcome.Unavailable, resolver(keys, forged, 1, "device-b").resolve(buyerCreation()))
        assertEquals(CommercialActionAuthorityOutcome.WrongDevice, resolver(keys, buyerCredential, 1, "other-device").resolve(buyerCreation()))
        val seenRequest = buyerCreation().copy(
            action = CommercialAction.ReturnSeen, orderId = "order-1", orderVersion = 2,
            inboxOrderId = "order-1", inboxOrderVersion = 2,
            sellerBusinessId = "seller-co", buyerBusinessId = "buyer-co",
        )
        assertTrue(resolver(keys, buyerCredential, 1, "device-b").resolve(seenRequest) is CommercialActionAuthorityOutcome.Verified)
    }

    @Test
    fun `valid seller credential can confirm and rejects every unauthorized variant`() = runTest {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val sellerCred = signed(
            keys,
            businessId = "seller-co",
            actorId = "actor-s",
            deviceId = "device-s",
            scope = setOf(OrderConfirmAuthority.CONFIRM_ORDERS_CAPABILITY),
        )
        val resolver = resolver(keys, sellerCred, epoch = 1, deviceId = "device-s")
        val confirmed = resolver.resolve(sellerConfirm())
        assertTrue(confirmed is CommercialActionAuthorityOutcome.Verified)
        val context = (confirmed as CommercialActionAuthorityOutcome.Verified).context
        assertEquals("seller-co", context.businessId)
        assertEquals("actor-s", context.actorId)
        assertEquals("device-s", context.deviceId)
        assertEquals("cred-1", context.credentialId)
        assertEquals(CommercialAction.SellerConfirm, context.intendedAction)
        assertEquals("order-1", context.orderId)
        assertEquals(1, context.orderVersion)

        val wrongBusinessCred = signed(keys, "other-co", "actor-s", "device-s", setOf(OrderConfirmAuthority.CONFIRM_ORDERS_CAPABILITY))
        assertEquals(
            CommercialActionAuthorityOutcome.WrongBusiness,
            resolver(keys, wrongBusinessCred, 1, "device-s").resolve(sellerConfirm()),
        )
        assertEquals(CommercialActionAuthorityOutcome.WrongActor, resolver.resolve(sellerConfirm(actor = "other-actor")))
        assertEquals(CommercialActionAuthorityOutcome.WrongDevice, resolver.resolve(sellerConfirm(device = "other-device")))
        val sendOnly = signed(keys, "seller-co", "actor-s", "device-s", setOf("send_orders"))
        assertEquals(
            CommercialActionAuthorityOutcome.MissingScope,
            resolver(keys, sendOnly, 1, "device-s").resolve(sellerConfirm()),
        )
        assertEquals(
            CommercialActionAuthorityOutcome.WrongRole,
            resolver.resolve(sellerConfirm().copy(action = CommercialAction.BuyerAcceptRevision)),
        )
        assertEquals(
            CommercialActionAuthorityOutcome.WrongOrderVersion,
            resolver.resolve(sellerConfirm().copy(inboxOrderVersion = 2)),
        )
        assertEquals(CommercialActionAuthorityOutcome.Unavailable, resolver(keys, null, 1, "device-s").resolve(sellerConfirm()))
    }

    @Test
    fun `revoked expired and stale epoch credentials are rejected`() = runTest {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val credential = signed(keys, "seller-co", "actor-s", "device-s", setOf(OrderConfirmAuthority.CONFIRM_ORDERS_CAPABILITY))
        val revoked = resolver(keys, credential, epoch = 1, deviceId = "device-s", keyRevoked = true)
        assertEquals(CommercialActionAuthorityOutcome.Revoked, revoked.resolve(sellerConfirm()))
        val expired = resolver(keys, credential, epoch = 1, deviceId = "device-s")
        assertEquals(CommercialActionAuthorityOutcome.Expired, expired.resolve(sellerConfirm(now = 90_000)))
        val stale = resolver(keys, credential, epoch = 9, deviceId = "device-s")
        assertEquals(CommercialActionAuthorityOutcome.StaleEpoch, stale.resolve(sellerConfirm()))
    }

    @Test
    fun `buyer cannot seller-confirm and seller cannot buyer-accept`() = runTest {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val buyerCred = signed(
            keys, "buyer-co", "actor-b", "device-b",
            setOf(OrderConfirmAuthority.ACCEPT_ORDER_REVISIONS_CAPABILITY),
        )
        val sellerCred = signed(
            keys, "seller-co", "actor-s", "device-s",
            setOf(OrderConfirmAuthority.CONFIRM_ORDERS_CAPABILITY, OrderConfirmAuthority.REVISE_ORDERS_CAPABILITY),
        )
        val buyerResolver = resolver(keys, buyerCred, 1, "device-b")
        val sellerResolver = resolver(keys, sellerCred, 1, "device-s")
        assertEquals(
            CommercialActionAuthorityOutcome.WrongRole,
            buyerResolver.resolve(
                sellerConfirm().copy(
                    viewerBusinessId = "buyer-co",
                    expectedActorId = "actor-b",
                    expectedDeviceId = "device-b",
                ),
            ),
        )
        assertEquals(
            CommercialActionAuthorityOutcome.WrongRole,
            sellerResolver.resolve(
                sellerConfirm().copy(
                    action = CommercialAction.BuyerAcceptRevision,
                    viewerBusinessId = "seller-co",
                ),
            ),
        )
        val accepted = buyerResolver.resolve(
            sellerConfirm().copy(
                action = CommercialAction.BuyerAcceptRevision,
                viewerBusinessId = "buyer-co",
                expectedActorId = "actor-b",
                expectedDeviceId = "device-b",
            ),
        )
        assertTrue(accepted is CommercialActionAuthorityOutcome.Verified)
    }

    @Test
    fun `authority resolution does not create accounting records`() = runTest {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val credential = signed(keys, "seller-co", "actor-s", "device-s", setOf(OrderConfirmAuthority.CONFIRM_ORDERS_CAPABILITY))
        val accountingMutations = mutableListOf<String>()
        resolver(keys, credential, 1, "device-s").resolve(sellerConfirm())
        assertTrue(accountingMutations.isEmpty())
    }

    private fun sellerConfirm(
        viewer: String = "seller-co",
        actor: String = "actor-s",
        device: String = "device-s",
        now: Long = 2_000,
    ) = CommercialActionAuthorityRequest(
        action = CommercialAction.SellerConfirm,
        viewerBusinessId = viewer,
        expectedActorId = actor,
        expectedDeviceId = device,
        expectedDeviceKeyVersion = 1,
        orderId = "order-1",
        orderVersion = 1,
        inboxOrderId = "order-1",
        inboxOrderVersion = 1,
        sellerBusinessId = "seller-co",
        buyerBusinessId = "buyer-co",
        nowEpochMillis = now,
    )

    private fun buyerCreation() = CommercialActionAuthorityRequest(
        action = CommercialAction.BuyerCreateOrder,
        viewerBusinessId = "buyer-co",
        expectedActorId = "actor-b",
        expectedDeviceId = "device-b",
        expectedDeviceKeyVersion = 1,
        orderId = "",
        orderVersion = 0,
        inboxOrderId = "",
        inboxOrderVersion = 0,
        sellerBusinessId = "",
        buyerBusinessId = "buyer-co",
        nowEpochMillis = 2_000,
    )

    private fun signed(
        keys: KeyPair,
        businessId: String,
        actorId: String,
        deviceId: String,
        scope: Set<String>,
    ): TrustedBusinessDeviceCredential {
        val unsigned = TrustedBusinessDeviceCredential(
            1, "cred-1", businessId, actorId, "member-1", deviceId, "device-key-1", 1,
            "fp", scope, 1, 1_000, 1_000, 61_000,
            "issuer-1", "issuer-key-1", "P256-SHA256-v1", byteArrayOf(),
        )
        return unsigned.copy(
            signature = Signature.getInstance("SHA256withECDSA").apply {
                initSign(keys.private)
                update(unsigned.signingBytes())
            }.sign(),
        )
    }

    private fun resolver(
        keys: KeyPair,
        credential: TrustedBusinessDeviceCredential?,
        epoch: Long,
        deviceId: String,
        keyRevoked: Boolean = false,
    ) = TrustVerifiedCommercialActionAuthorityResolver(
        credentials = { _, id -> credential?.takeIf { it.deviceId == id } },
        verifier = CachedTransportCredentialVerifier(
            { _, _ -> CachedIssuerVerificationKey("issuer-1", "issuer-key-1", "P256-SHA256-v1", keys.public.encoded, keyRevoked) },
            { _, _, _ -> epoch },
        ),
        keyStore = object : VartalapDeviceKeyStore {
            private val identity = DeviceSigningIdentity(
                deviceId, "device-key-1", 1, byteArrayOf(1), "fp", 0, DeviceKeySecurityLevel.SecureKeystore,
            )
            override suspend fun getCurrentIdentity() = identity
            override suspend fun getOrCreateIdentity(deviceId: String) = identity
            override suspend fun rotate(deviceId: String) = identity
            override suspend fun inspect(deviceId: String, keyVersion: Int) = identity
            override suspend fun sign(identity: DeviceSigningIdentity, boundedBytes: ByteArray) = DeviceSigningResult.Success(boundedBytes)
            override suspend fun remove(deviceId: String, keyVersion: Int) = true
        },
        epochs = { _, _, _ -> epoch },
    )
}
