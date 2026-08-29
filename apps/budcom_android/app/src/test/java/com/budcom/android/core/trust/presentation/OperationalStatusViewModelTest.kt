package com.budcom.android.core.trust.presentation

import com.budcom.android.core.relay.data.remote.RelayRuntimeEndpointProvider
import com.budcom.android.core.trust.data.remote.TrustEndpointProvider
import com.budcom.android.core.trust.domain.StoredTrustCredential
import com.budcom.android.core.trust.domain.TrustCredentialReadOutcome
import com.budcom.android.core.trust.domain.TrustCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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

    @Test
    fun `never enrolled, both endpoints unconfigured -- reports Trust not configured first`() = runTest {
        val viewModel = OperationalStatusViewModel(FakeStatusCredentialStore(TrustCredentialReadOutcome.NoRecord), FakeStatusEndpointProvider(null), FakeStatusRelayProvider(null))
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(EnrollmentUiStatus.NotEnrolled, state.enrollment)
        assertEquals("Trust service is not configured.", state.statusMessage)
    }

    @Test
    fun `Trust configured but never enrolled -- reports not enrolled`() = runTest {
        val viewModel = OperationalStatusViewModel(FakeStatusCredentialStore(TrustCredentialReadOutcome.NoRecord), FakeStatusEndpointProvider("http://trust.example/"), FakeStatusRelayProvider(null))
        advanceUntilIdle()
        assertEquals("This device is not enrolled.", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun `enrolled with an active credential and both endpoints configured -- ready`() = runTest {
        val viewModel = OperationalStatusViewModel(
            FakeStatusCredentialStore(TrustCredentialReadOutcome.Present(credential())),
            FakeStatusEndpointProvider("http://trust.example/"), FakeStatusRelayProvider("http://relay.example/"),
        )
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(EnrollmentUiStatus.Enrolled("business-1"), state.enrollment)
        assertEquals("This device is enrolled and ready.", state.statusMessage)
    }

    @Test
    fun `enrolled but Relay endpoint unconfigured -- reports delivery service not configured, never a raw exception`() = runTest {
        val viewModel = OperationalStatusViewModel(
            FakeStatusCredentialStore(TrustCredentialReadOutcome.Present(credential())),
            FakeStatusEndpointProvider("http://trust.example/"), FakeStatusRelayProvider(null),
        )
        advanceUntilIdle()
        assertEquals("Message delivery service is not configured.", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun `an expired credential is reported as expired, never as simply enrolled`() = runTest {
        val viewModel = OperationalStatusViewModel(
            FakeStatusCredentialStore(TrustCredentialReadOutcome.Present(credential(expiresAtEpochMillis = System.currentTimeMillis() - 1_000))),
            FakeStatusEndpointProvider("http://trust.example/"), FakeStatusRelayProvider("http://relay.example/"),
        )
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(EnrollmentUiStatus.CredentialExpired, state.enrollment)
        assertTrue(state.statusMessage.contains("re-enroll", ignoreCase = true))
    }

    @Test
    fun `unreadable storage is treated as needing re-enrollment, never as a fresh unenrolled device`() = runTest {
        val viewModel = OperationalStatusViewModel(
            FakeStatusCredentialStore(TrustCredentialReadOutcome.Unreadable),
            FakeStatusEndpointProvider("http://trust.example/"), FakeStatusRelayProvider("http://relay.example/"),
        )
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(EnrollmentUiStatus.Unreadable, state.enrollment)
        assertTrue(state.statusMessage.contains("re-enroll", ignoreCase = true))
    }

    @Test
    fun `refreshNow re-checks enrollment on demand -- for example right after a successful enrollment completes`() = runTest {
        val credentialStore = FakeStatusCredentialStore(TrustCredentialReadOutcome.NoRecord)
        val viewModel = OperationalStatusViewModel(credentialStore, FakeStatusEndpointProvider("http://trust.example/"), FakeStatusRelayProvider("http://relay.example/"))
        advanceUntilIdle()
        assertEquals(EnrollmentUiStatus.NotEnrolled, viewModel.uiState.value.enrollment)
        credentialStore.store(credential())
        viewModel.refreshNow()
        advanceUntilIdle()
        assertEquals(EnrollmentUiStatus.Enrolled("business-1"), viewModel.uiState.value.enrollment)
    }

    @Test
    fun `status message never contains technical details -- no exception class names, key IDs, or db terms`() = runTest {
        val viewModel = OperationalStatusViewModel(
            FakeStatusCredentialStore(TrustCredentialReadOutcome.Unreadable),
            FakeStatusEndpointProvider(null), FakeStatusRelayProvider(null),
        )
        advanceUntilIdle()
        val message = viewModel.uiState.value.statusMessage.lowercase()
        assertTrue(listOf("exception", "sqlite", "authorityepoch", "fingerprint", "keyid", "http", "database").none { it in message })
    }
}
