package com.budcom.android.core.trust.domain

/**
 * Secure, single-record-per-device storage for the current Trust-issued device credential. Never
 * exposes the private device key (that stays in Android Keystore, owned by
 * `VartalapDeviceKeyStore`) -- only the ISSUED credential's own claims and signature, which are
 * public/verifiable material by design (any recipient must be able to verify them against Trust's
 * published verification keys), but are still encrypted at rest here because a stolen credential
 * blob remains a bounded-lifetime bearer proof of this device's authority until it expires.
 */
interface TrustCredentialStore {
    /** The current credential, or null if this device has never enrolled or was cleared. Never
     * throws for "not present" -- callers distinguish "no credential" from a real I/O failure via
     * [TrustCredentialReadOutcome] where that distinction matters (see [readOutcome]). */
    suspend fun current(): StoredTrustCredential?

    /** Safe, non-throwing variant of [current] -- see [TrustCredentialReadOutcome]. */
    suspend fun readOutcome(): TrustCredentialReadOutcome

    /** Atomically replaces any existing credential. */
    suspend fun store(credential: StoredTrustCredential)

    /** Removes the current credential entirely (e.g. re-enrollment, business switch, revocation). */
    suspend fun clear()
}

/** Mirrors `SecureCredentialVaultReadOutcome`'s own reasoning: a corrupted/unreadable record must
 * never be silently treated as "never enrolled" -- that would let fail-closed enrollment-gated
 * behavior (Gate 10: "no fallback may weaken authority") be defeated by storage corruption. */
sealed interface TrustCredentialReadOutcome {
    data object NoRecord : TrustCredentialReadOutcome
    data class Present(val credential: StoredTrustCredential) : TrustCredentialReadOutcome
    data object Unreadable : TrustCredentialReadOutcome
}
