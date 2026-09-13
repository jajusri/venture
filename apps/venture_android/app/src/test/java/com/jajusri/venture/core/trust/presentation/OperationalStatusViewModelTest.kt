package com.jajusri.venture.core.trust.presentation

import com.jajusri.venture.core.pairing.data.local.FakeSecureCredentialVault
import com.jajusri.venture.core.pairing.data.local.InMemoryVaultBackingStore
import com.jajusri.venture.core.pairing.domain.model.SecurePairingCredentialState
import com.jajusri.venture.core.relay.data.remote.RelayRuntimeEndpointProvider
import com.jajusri.venture.core.trust.data.remote.TrustEndpointProvider
import com.jajusri.venture.core.trust.domain.StoredTrustCredential
import com.jajusri.venture.core.trust.domain.TrustCredentialReadOutcome
import com.jajusri.venture.core.trust.domain.TrustCredentialStore
import com.jajusri.venture.feature.company.data.repository.SelectedCompanyStore
import com.jajusri.venture.feature.company.domain.model.SessionSelectedCompany
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeStatusCredentialStore(private var outcome: TrustCredentialReadOutcome) : TrustCredentialStore {
    override suspend fun current(): StoredTrustCredential? = (outcome as? TrustCredentialReadOutcome.Present)?.credential
    override suspend fun readOutcome(): TrustCredentialReadOutcome = outcome
    override suspend fun store(credential: StoredTrustCredential) { outcome = TrustCredentialReadOutcome.Present(credential) }
    override suspend fun clear() { outcome = TrustCredentialReadOutcome.NoRecord }
}

private class FakeStatusEndpointProvider(initial: String?) : TrustEndpointProvider {
    private val flow = MutableStateFlow(initial)
    override fun snapshot(): String? = flow.value
    override fun observe(): Flow<String?> = flow
    override fun updateInMemory(normalizedBaseUrl: String?) { flow.value = normalizedBaseUrl }
}

private class FakeStatusRelayProvider(initial: String?) : RelayRuntimeEndpointProvider {
    private val flow = MutableStateFlow(initial)
    override fun snapshot(): String? = flow.value
    override fun observe(): Flow<String?> = flow
    override fun updateInMemory(normalizedBaseUrl: String?) { flow.value = normalizedBaseUrl }
}

private class FakeStatusSelectedCompanyStore(initial: SessionSelectedCompany? = null) : SelectedCompanyStore {
    private val flow = MutableStateFlow(initial)
    override fun observeSelectedCompany(): Flow<SessionSelectedCompany?> = flow
    override fun observeSelectedCompanyId(): Flow<String?> = flow.map { it?.id }
    override suspend fun getSelectedCompanyId(): String? = flow.value?.id
    override suspend fun saveSelectedCompanyId(companyId: String) { /* not needed by these tests */ }
    override suspend fun clearSelectedCompanyId() { flow.value = null }
}

private fun credential(businessId: String = "business-1", expiresAtEpochMillis: Long = System.currentTimeMillis() + 3_600_000) = StoredTrustCredential(
    credentialVersion = 1, credentialId = "cred-1", businessId = businessId, actorId = "actor-1", membershipId = "membership-1",
    deviceId = "device-1", deviceKeyId = "device-1-key-1", deviceKeyVersion = 1, devicePublicKeyFingerprint = "fingerprint-1",
    authorityScope = listOf("send_orders"), authorityEpoch = 1L, issuedAtEpochMillis = expiresAtEpochMillis - 7_200_000,
    notBeforeEpochMillis = expiresAtEpochMillis - 7_200_000, expiresAtEpochMillis = expiresAtEpochMillis,
    issuerId = "issuer-1", issuerKeyId = "issuer-key-1", signatureBase64 = "c2ln",
)

@OptIn(ExperimentalCoroutinesApi::class)
class OperationalStatusViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel(
        credentialStore: TrustCredentialStore = FakeStatusCredentialStore(TrustCredentialReadOutcome.NoRecord),
        trustEndpointProvider: TrustEndpointProvider = FakeStatusEndpointProvider(null),
        relayEndpointProvider: RelayRuntimeEndpointProvider = FakeStatusRelayProvider(null),
        secureCredentialVault: FakeSecureCredentialVault = FakeSecureCredentialVault(),
        selectedCompanyStore: SelectedCompanyStore = FakeStatusSelectedCompanyStore(),
    ) = OperationalStatusViewModel(credentialStore, trustEndpointProvider, relayEndpointProvider, secureCredentialVault, selectedCompanyStore)

    @Test
    fun `never enrolled, both endpoints unconfigured -- reports Trust not configured first`() = runTest {
        val vm = viewModel(trustEndpointProvider = FakeStatusEndpointProvider(null), relayEndpointProvider = FakeStatusRelayProvider(null))
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(EnrollmentUiStatus.NotEnrolled, state.enrollment)
        assertEquals("Trust service is not configured.", state.statusMessage)
    }

    @Test
    fun `Trust configured but never enrolled -- reports not enrolled`() = runTest {
        val vm = viewModel(trustEndpointProvider = FakeStatusEndpointProvider("http://trust.example/"), relayEndpointProvider = FakeStatusRelayProvider(null))
        advanceUntilIdle()
        assertEquals("This device is not enrolled.", vm.uiState.value.statusMessage)
    }

    @Test
    fun `enrolled with an active credential and both endpoints configured -- ready`() = runTest {
        val vm = viewModel(
            credentialStore = FakeStatusCredentialStore(TrustCredentialReadOutcome.Present(credential())),
            trustEndpointProvider = FakeStatusEndpointProvider("http://trust.example/"), relayEndpointProvider = FakeStatusRelayProvider("http://relay.example/"),
        )
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(EnrollmentUiStatus.Enrolled("business-1"), state.enrollment)
        assertEquals("This device is enrolled and ready.", state.statusMessage)
    }

    @Test
    fun `enrolled but Relay endpoint unconfigured -- reports delivery service not configured, never a raw exception`() = runTest {
        val vm = viewModel(
            credentialStore = FakeStatusCredentialStore(TrustCredentialReadOutcome.Present(credential())),
            trustEndpointProvider = FakeStatusEndpointProvider("http://trust.example/"), relayEndpointProvider = FakeStatusRelayProvider(null),
        )
        advanceUntilIdle()
        assertEquals("Message delivery service is not configured.", vm.uiState.value.statusMessage)
    }

    @Test
    fun `an expired credential is reported as expired, never as simply enrolled`() = runTest {
        val vm = viewModel(
            credentialStore = FakeStatusCredentialStore(TrustCredentialReadOutcome.Present(credential(expiresAtEpochMillis = System.currentTimeMillis() - 1_000))),
            trustEndpointProvider = FakeStatusEndpointProvider("http://trust.example/"), relayEndpointProvider = FakeStatusRelayProvider("http://relay.example/"),
        )
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(EnrollmentUiStatus.CredentialExpired, state.enrollment)
        assertTrue(state.statusMessage.contains("re-enroll", ignoreCase = true))
    }

    @Test
    fun `unreadable storage is treated as needing re-enrollment, never as a fresh unenrolled device`() = runTest {
        val vm = viewModel(
            credentialStore = FakeStatusCredentialStore(TrustCredentialReadOutcome.Unreadable),
            trustEndpointProvider = FakeStatusEndpointProvider("http://trust.example/"), relayEndpointProvider = FakeStatusRelayProvider("http://relay.example/"),
        )
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(EnrollmentUiStatus.Unreadable, state.enrollment)
        assertTrue(state.statusMessage.contains("re-enroll", ignoreCase = true))
    }

    @Test
    fun `refreshNow re-checks enrollment on demand -- for example right after a successful enrollment completes`() = runTest {
        val credentialStore = FakeStatusCredentialStore(TrustCredentialReadOutcome.NoRecord)
        val vm = viewModel(credentialStore = credentialStore, trustEndpointProvider = FakeStatusEndpointProvider("http://trust.example/"), relayEndpointProvider = FakeStatusRelayProvider("http://relay.example/"))
        advanceUntilIdle()
        assertEquals(EnrollmentUiStatus.NotEnrolled, vm.uiState.value.enrollment)
        credentialStore.store(credential())
        vm.refreshNow()
        advanceUntilIdle()
        assertEquals(EnrollmentUiStatus.Enrolled("business-1"), vm.uiState.value.enrollment)
    }

    @Test
    fun `status message never contains technical details -- no exception class names, key IDs, or db terms`() = runTest {
        val vm = viewModel(
            credentialStore = FakeStatusCredentialStore(TrustCredentialReadOutcome.Unreadable),
            trustEndpointProvider = FakeStatusEndpointProvider(null), relayEndpointProvider = FakeStatusRelayProvider(null),
        )
        advanceUntilIdle()
        val message = vm.uiState.value.statusMessage.lowercase()
        assertTrue(listOf("exception", "sqlite", "authorityepoch", "fingerprint", "keyid", "http", "database").none { it in message })
    }

    // -- Connector pairing is a SEPARATE capability from Trust enrollment: neither may be inferred
    // from the other (the round's own "post-certification navigation gap" directive). --

    @Test
    fun `never paired -- reports not paired, independent of Trust enrollment state`() = runTest {
        val vm = viewModel(
            credentialStore = FakeStatusCredentialStore(TrustCredentialReadOutcome.Present(credential())),
            trustEndpointProvider = FakeStatusEndpointProvider("http://trust.example/"), relayEndpointProvider = FakeStatusRelayProvider("http://relay.example/"),
        )
        advanceUntilIdle()
        assertEquals(ConnectorPairingUiStatus.NotPaired, vm.uiState.value.connectorPairing)
        // Enrolled-and-ready remains true regardless -- pairing state never contaminates it.
        assertEquals("This device is enrolled and ready.", vm.uiState.value.statusMessage)
    }

    @Test
    fun `an active paired connector reports Paired`() = runTest {
        val vault = FakeSecureCredentialVault(backingStore = InMemoryVaultBackingStore().apply { record = pairedRecord(SecurePairingCredentialState.ACTIVE) })
        val vm = viewModel(secureCredentialVault = vault)
        advanceUntilIdle()
        assertEquals(ConnectorPairingUiStatus.Paired, vm.uiState.value.connectorPairing)
    }

    @Test
    fun `a re-pair-required connector is reported distinctly, never as simply paired or unpaired`() = runTest {
        val vault = FakeSecureCredentialVault(backingStore = InMemoryVaultBackingStore().apply { record = pairedRecord(SecurePairingCredentialState.RE_PAIR_REQUIRED) })
        val vm = viewModel(secureCredentialVault = vault)
        advanceUntilIdle()
        assertEquals(ConnectorPairingUiStatus.RePairRequired, vm.uiState.value.connectorPairing)
    }

    @Test
    fun `unreadable pairing storage is reported distinctly, never as a fresh unpaired device`() = runTest {
        val vault = FakeSecureCredentialVault(backingStore = InMemoryVaultBackingStore().apply { unreadable = true })
        val vm = viewModel(secureCredentialVault = vault)
        advanceUntilIdle()
        assertEquals(ConnectorPairingUiStatus.Unreadable, vm.uiState.value.connectorPairing)
    }

    @Test
    fun `selected company name is surfaced when a company is selected, null otherwise`() = runTest {
        val noCompany = viewModel(selectedCompanyStore = FakeStatusSelectedCompanyStore(null))
        advanceUntilIdle()
        assertNull(noCompany.uiState.value.selectedCompanyName)

        val withCompany = viewModel(selectedCompanyStore = FakeStatusSelectedCompanyStore(SessionSelectedCompany("company-1", "Acme Traders")))
        advanceUntilIdle()
        assertEquals("Acme Traders", withCompany.uiState.value.selectedCompanyName)
    }

    @Test
    fun `connector pairing status message is human-safe -- never a raw record state name`() {
        val message = connectorPairingMessageFor(ConnectorPairingUiStatus.RePairRequired).lowercase()
        assertTrue(listOf("re_pair_required", "exception", "record", "vault").none { it in message })
    }
}

private fun pairedRecord(state: SecurePairingCredentialState) =
    com.jajusri.venture.core.pairing.domain.model.SecurePairingCredentialRecord(
        credentialId = "cred-1",
        deviceId = "device-1",
        encryptedCredential = com.jajusri.venture.core.security.EncryptedPayload(ciphertext = byteArrayOf(1, 2, 3), iv = byteArrayOf(4, 5, 6), formatVersion = 1),
        endpoint = com.jajusri.venture.core.pairing.domain.model.TrustedConnectorEndpoint(
            connectorId = "connector-1", connectorName = "Test Connector", host = "192.168.1.10", securePort = 8443,
            transportFingerprint = "sha256/AAAA", fingerprintAlgorithm = "sha256", transportIdentityVersion = 1,
        ),
        createdAtEpochMillis = 1_000L,
        lastVerifiedAtEpochMillis = if (state == SecurePairingCredentialState.ACTIVE) 2_000L else null,
        state = state,
    )
