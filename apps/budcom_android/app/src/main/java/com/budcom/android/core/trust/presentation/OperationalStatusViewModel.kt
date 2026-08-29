package com.budcom.android.core.trust.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.pairing.data.local.SecureCredentialVault
import com.budcom.android.core.pairing.data.local.SecureCredentialVaultReadOutcome
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialState
import com.budcom.android.core.relay.data.remote.RelayRuntimeEndpointProvider
import com.budcom.android.core.trust.data.remote.TrustEndpointProvider
import com.budcom.android.core.trust.domain.TrustCredentialReadOutcome
import com.budcom.android.core.trust.domain.TrustCredentialStore
import com.budcom.android.feature.company.data.repository.SelectedCompanyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

/** Connector pairing is a distinct capability from Trust enrollment (see the round's own "post-
 * certification navigation gap" directive) -- a user must be able to see one without it implying
 * anything about the other. Mirrors [com.budcom.android.core.pairing.domain.model.SecurePairingCredentialState]
 * one for one, plus the vault's own [SecureCredentialVaultReadOutcome.NoRecord]/[SecureCredentialVaultReadOutcome.Unreadable]. */
sealed interface ConnectorPairingUiStatus {
    data object NotPaired : ConnectorPairingUiStatus
    data object PendingVerification : ConnectorPairingUiStatus
    data object Paired : ConnectorPairingUiStatus
    data object RePairRequired : ConnectorPairingUiStatus
    data object Unreadable : ConnectorPairingUiStatus
}

data class OperationalStatusUiState(
    val enrollment: EnrollmentUiStatus = EnrollmentUiStatus.NotEnrolled,
    val trustEndpointConfigured: Boolean = false,
    val relayEndpointConfigured: Boolean = false,
    val statusMessage: String = INITIAL_MESSAGE,
    val connectorPairing: ConnectorPairingUiStatus = ConnectorPairingUiStatus.NotPaired,
    val selectedCompanyName: String? = null,
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

/** Human-safe, in the same style as [statusMessageFor] -- never a raw record/state name. */
fun connectorPairingMessageFor(status: ConnectorPairingUiStatus): String = when (status) {
    ConnectorPairingUiStatus.NotPaired -> "Not paired with a Desktop Connector."
    ConnectorPairingUiStatus.PendingVerification -> "Pairing started but not yet verified."
    ConnectorPairingUiStatus.Paired -> "Paired with a Desktop Connector."
    ConnectorPairingUiStatus.RePairRequired -> "Connector pairing needs to be redone."
    ConnectorPairingUiStatus.Unreadable -> "Pairing status could not be read. Please re-pair."
}

@HiltViewModel
class OperationalStatusViewModel @Inject constructor(
    private val credentialStore: TrustCredentialStore,
    private val trustEndpointProvider: TrustEndpointProvider,
    private val relayEndpointProvider: RelayRuntimeEndpointProvider,
    private val secureCredentialVault: SecureCredentialVault,
    private val selectedCompanyStore: SelectedCompanyStore,
) : ViewModel() {
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() }
    private val _uiState = MutableStateFlow(OperationalStatusUiState())
    val uiState: StateFlow<OperationalStatusUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                trustEndpointProvider.observe(),
                relayEndpointProvider.observe(),
                selectedCompanyStore.observeSelectedCompany(),
            ) { trustUrl, relayUrl, company -> Triple(trustUrl != null, relayUrl != null, company?.name) }
                .collectLatest { (trustConfigured, relayConfigured, companyName) -> refresh(trustConfigured, relayConfigured, companyName) }
        }
    }

    /** Re-checks enrollment status without waiting for an endpoint-configuration change -- callers
     * (e.g. an enrollment screen, once built) should call this after a successful enrollment so the
     * status view reflects it immediately, since [TrustCredentialStore] itself has no observable
     * stream of its own for this ViewModel to react to automatically. */
    fun refreshNow() {
        viewModelScope.launch {
            refresh(
                trustEndpointProvider.snapshot() != null,
                relayEndpointProvider.snapshot() != null,
                selectedCompanyStore.observeSelectedCompany().first()?.name,
            )
        }
    }

    private suspend fun refresh(trustConfigured: Boolean, relayConfigured: Boolean, selectedCompanyName: String?) {
        val enrollment = when (val outcome = credentialStore.readOutcome()) {
            TrustCredentialReadOutcome.NoRecord -> EnrollmentUiStatus.NotEnrolled
            TrustCredentialReadOutcome.Unreadable -> EnrollmentUiStatus.Unreadable
            is TrustCredentialReadOutcome.Present -> if (outcome.credential.isExpired(nowEpochMillis())) {
                EnrollmentUiStatus.CredentialExpired
            } else {
                EnrollmentUiStatus.Enrolled(outcome.credential.businessId)
            }
        }
        val connectorPairing = when (val outcome = secureCredentialVault.readOutcome()) {
            SecureCredentialVaultReadOutcome.NoRecord -> ConnectorPairingUiStatus.NotPaired
            SecureCredentialVaultReadOutcome.Unreadable -> ConnectorPairingUiStatus.Unreadable
            is SecureCredentialVaultReadOutcome.Present -> when (outcome.record.state) {
                SecurePairingCredentialState.PENDING_VERIFICATION -> ConnectorPairingUiStatus.PendingVerification
                SecurePairingCredentialState.ACTIVE -> ConnectorPairingUiStatus.Paired
                SecurePairingCredentialState.RE_PAIR_REQUIRED -> ConnectorPairingUiStatus.RePairRequired
            }
        }
        _uiState.value = OperationalStatusUiState(
            enrollment = enrollment, trustEndpointConfigured = trustConfigured, relayEndpointConfigured = relayConfigured,
            statusMessage = statusMessageFor(enrollment, trustConfigured, relayConfigured),
            connectorPairing = connectorPairing, selectedCompanyName = selectedCompanyName,
        )
    }
}
