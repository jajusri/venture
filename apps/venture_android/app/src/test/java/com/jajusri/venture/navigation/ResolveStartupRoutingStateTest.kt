package com.jajusri.venture.navigation

import com.jajusri.venture.core.connection.ConnectorEnrolmentGate
import com.jajusri.venture.core.pairing.data.local.FakeSecureCredentialVault
import com.jajusri.venture.core.pairing.data.local.InMemoryVaultBackingStore
import com.jajusri.venture.core.pairing.domain.model.TrustedConnectorEndpoint
import com.jajusri.venture.core.security.FakeCredentialCipher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveStartupRoutingStateTest {

    private val endpoint = TrustedConnectorEndpoint.fromTrustedPublicMetadata(
        connectorId = "connector-abc",
        connectorName = "Front Desk",
        host = "10.0.0.5",
        securePort = 8443,
        transportFingerprint = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        fingerprintAlgorithm = "sha256",
        transportIdentityVersion = 1,
    )

    // 2. no secure record plus no usable legacy state selects PairingRequired
    @Test
    fun `no vault record and no legacy signal selects PairingRequired`() = runTest {
        val resolve = resolverOf(vault = FakeSecureCredentialVault(), needsEnrolment = true)

        assertEquals(StartupRoutingState.PairingRequired, resolve())
    }

    // 3. no secure record plus valid legacy configuration selects LegacyEligible
    @Test
    fun `no vault record and an existing legacy signal selects LegacyEligible`() = runTest {
        val resolve = resolverOf(vault = FakeSecureCredentialVault(), needsEnrolment = false)

        assertEquals(StartupRoutingState.LegacyEligible, resolve())
    }

    // 4/5. legacy eligibility is entirely delegated to the existing, already-proven gate — this
    // resolver adds no independent default-URL heuristic of its own.
    @Test
    fun `legacy eligibility is exactly whatever the existing enrolment gate decides`() = runTest {
        val vault = FakeSecureCredentialVault()
        val resolveNeedsEnrolment = resolverOf(vault = vault, needsEnrolment = true)
        val resolveDoesNot = resolverOf(vault = vault, needsEnrolment = false)

        assertEquals(StartupRoutingState.PairingRequired, resolveNeedsEnrolment())
        assertEquals(StartupRoutingState.LegacyEligible, resolveDoesNot())
    }

    // 6. ACTIVE selects SecureActive
    @Test
    fun `an ACTIVE decryptable record selects SecureActive regardless of legacy signal`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", endpoint, 1_000L)
        vault.markActive("cred-1", 2_000L)
        val resolve = resolverOf(vault = vault, needsEnrolment = true)

        assertEquals(StartupRoutingState.SecureActive, resolve())
    }

    // 7. PENDING_VERIFICATION selects PairingPending
    @Test
    fun `a PENDING_VERIFICATION record selects PairingPending, never LegacyEligible`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", endpoint, 1_000L)
        val resolve = resolverOf(vault = vault, needsEnrolment = false)

        assertEquals(StartupRoutingState.PairingPending, resolve())
    }

    // 8. RE_PAIR_REQUIRED selects RePairRequired
    @Test
    fun `a RE_PAIR_REQUIRED record selects RePairRequired, never LegacyEligible`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", endpoint, 1_000L)
        vault.markRePairRequired("cred-1")
        val resolve = resolverOf(vault = vault, needsEnrolment = false)

        assertEquals(StartupRoutingState.RePairRequired, resolve())
    }

    // 9. credential-unavailable after prior enrollment does not select LegacyEligible
    @Test
    fun `an ACTIVE record whose Keystore key is missing selects SecureCredentialUnavailable`() = runTest {
        val cipher = FakeCredentialCipher()
        val vault = FakeSecureCredentialVault(cipher = cipher)
        vault.storePendingVerification("cred-1", "device-1", "raw-token", endpoint, 1_000L)
        vault.markActive("cred-1", 2_000L)
        cipher.dropKey()
        val resolve = resolverOf(vault = vault, needsEnrolment = false)

        assertEquals(StartupRoutingState.SecureCredentialUnavailable, resolve())
    }

    // 10. corrupted credential does not select LegacyEligible
    @Test
    fun `an unreadable, corrupted vault record selects SecureCredentialUnavailable, never LegacyEligible or PairingRequired`() = runTest {
        val backing = InMemoryVaultBackingStore()
        val vault = FakeSecureCredentialVault(backingStore = backing)
        vault.storePendingVerification("cred-1", "device-1", "raw-token", endpoint, 1_000L)
        backing.unreadable = true

        val legacyEligibleCase = resolverOf(vault = vault, needsEnrolment = false)
        val pairingRequiredCase = resolverOf(vault = vault, needsEnrolment = true)

        assertEquals(StartupRoutingState.SecureCredentialUnavailable, legacyEligibleCase())
        assertEquals(StartupRoutingState.SecureCredentialUnavailable, pairingRequiredCase())
    }

    // 11/12. classification is a pure function of currently-persisted state — re-invoking after a
    // simulated process recreation (new resolver instance, same backing store) is stable.
    @Test
    fun `classification is stable across a simulated process recreation`() = runTest {
        val backing = InMemoryVaultBackingStore()
        val cipher = FakeCredentialCipher()
        val firstVault = FakeSecureCredentialVault(cipher = cipher, backingStore = backing)
        firstVault.storePendingVerification("cred-1", "device-1", "raw-token", endpoint, 1_000L)
        firstVault.markActive("cred-1", 2_000L)

        val recreatedVault = FakeSecureCredentialVault(cipher = cipher, backingStore = backing)
        val resolve = resolverOf(vault = recreatedVault, needsEnrolment = false)

        assertEquals(StartupRoutingState.SecureActive, resolve())
        assertEquals(StartupRoutingState.SecureActive, resolve())
    }

    private fun resolverOf(
        vault: FakeSecureCredentialVault,
        needsEnrolment: Boolean,
    ): ResolveStartupRoutingState = DefaultResolveStartupRoutingState(
        vault = vault,
        enrolmentGate = object : ConnectorEnrolmentGate {
            override suspend fun needsEnrolment(): Boolean = needsEnrolment
        },
    )
}
