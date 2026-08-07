package com.budcom.android.navigation

/**
 * The one authoritative classification of "what should this app show right now," resolved once
 * per process start by [ResolveStartupRoutingState] and consumed by [AppRootViewModel] to pick a
 * start destination. Every other screen that needs to know the current secure-connection status
 * (e.g. Settings) re-resolves this same state rather than deriving its own competing check —
 * credential and legacy-eligibility checks must not be scattered across composables.
 */
sealed class StartupRoutingState {

    /** No secure-pairing record exists, but a source-proven legacy installation signal does. */
    data object LegacyEligible : StartupRoutingState()

    /** No secure-pairing record exists and no legacy installation signal exists either. */
    data object PairingRequired : StartupRoutingState()

    /** A credential was redeemed but not yet proven — must not be treated as legacy-eligible. */
    data object PairingPending : StartupRoutingState()

    /** An ACTIVE, decryptable credential exists. */
    data object SecureActive : StartupRoutingState()

    /** The credential is known unusable (revoked / rejected) — cached data remains reachable. */
    data object RePairRequired : StartupRoutingState()

    /** A record exists (ACTIVE metadata, or unreadable/corrupted storage) but no usable credential
     * can currently be recovered — never collapsed into [PairingRequired] or [LegacyEligible]. */
    data object SecureCredentialUnavailable : StartupRoutingState()
}
