package com.budcom.android.core.trust.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.relay.data.remote.RelayRuntimeEndpointProvider
import com.budcom.android.core.trust.data.remote.TrustEndpointProvider
import com.budcom.android.core.trust.domain.TrustCredentialReadOutcome
import com.budcom.android.core.trust.domain.TrustCredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Gate 11 -- the minimal operational status a user needs, in human-safe language only. Never
 * exposes authorityEpoch, key IDs/fingerprints, raw HTTP exceptions, or database terminology (see
 * [statusMessageFor] -- every branch is a canned, pre-written sentence, never a passed-through
 * technical message).
 */
sealed interface EnrollmentUiStatus {
    data object NotEnrolled : EnrollmentUiStatus
    data class Enrolled(val businessId: String) : EnrollmentUiStatus
    data object CredentialExpired : EnrollmentUiStatus
    /** Storage could not be read back (corruption) -- treated the same as "must re-enroll", never
     * silently treated as [NotEnrolled] (that would be a fail-open regression: see
     * `TrustCredentialReadOutcome`'s own doc comment for why the distinction exists at all). */
    data object Unreadable : EnrollmentUiStatus
}

data class OperationalStatusUiState(
    val enrollment: EnrollmentUiStatus = EnrollmentUiStatus.NotEnrolled,
    val trustEndpointConfigured: Boolean = false,
    val relayEndpointConfigured: Boolean = false,
    val statusMessage: String = INITIAL_MESSAGE,
) {
    companion object { const val INITIAL_MESSAGE = "Checking enrollment status..." }
}

fun statusMessageFor(enrollment: EnrollmentUiStatus, trustEndpointConfigured: Boolean, relayEndpointConfigured: Boolean): String = when {
    !trustEndpointConfigured -> "Trust service is not configured."
    enrollment is EnrollmentUiStatus.NotEnrolled -> "This device is not enrolled."
    enrollment is EnrollmentUiStatus.CredentialExpired -> "Enrollment has expired. Please re-enroll this device."
    enrollment is EnrollmentUiStatus.Unreadable -> "Enrollment could not be read. Please re-enroll this device."
    !relayEndpointConfigured -> "Message delivery service is not configured."
    else -> "This device is enrolled and ready."
}

@HiltViewModel
class OperationalStatusViewModel @Inject constructor(
    private val credentialStore: TrustCredentialStore,
    private val trustEndpointProvider: TrustEndpointProvider,
    private val relayEndpointProvider: RelayRuntimeEndpointProvider,
) : ViewModel() {
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() }
    private val _uiState = MutableStateFlow(OperationalStatusUiState())
    val uiState: StateFlow<OperationalStatusUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(trustEndpointProvider.observe(), relayEndpointProvider.observe()) { trustUrl, relayUrl -> (trustUrl != null) to (relayUrl != null) }
                .collectLatest { (trustConfigured, relayConfigured) -> refresh(trustConfigured, relayConfigured) }
        }
    }

    /** Re-checks enrollment status without waiting for an endpoint-configuration change -- callers
     * (e.g. an enrollment screen, once built) should call this after a successful enrollment so the
     * status view reflects it immediately, since [TrustCredentialStore] itself has no observable
     * stream of its own for this ViewModel to react to automatically. */
    fun refreshNow() {
        viewModelScope.launch { refresh(trustEndpointProvider.snapshot() != null, relayEndpointProvider.snapshot() != null) }
    }

    private suspend fun refresh(trustConfigured: Boolean, relayConfigured: Boolean) {
        val enrollment = when (val outcome = credentialStore.readOutcome()) {
            TrustCredentialReadOutcome.NoRecord -> EnrollmentUiStatus.NotEnrolled
            TrustCredentialReadOutcome.Unreadable -> EnrollmentUiStatus.Unreadable
            is TrustCredentialReadOutcome.Present -> if (outcome.credential.isExpired(nowEpochMillis())) {
                EnrollmentUiStatus.CredentialExpired
            } else {
                EnrollmentUiStatus.Enrolled(outcome.credential.businessId)
            }
        }
        _uiState.value = OperationalStatusUiState(
            enrollment = enrollment, trustEndpointConfigured = trustConfigured, relayEndpointConfigured = relayConfigured,
            statusMessage = statusMessageFor(enrollment, trustConfigured, relayConfigured),
        )
    }
}
