package com.budcom.android.core.pairing.domain.model

/**
 * App-scoped logical device identity sent to the Connector on pairing redemption.
 *
 * [logicalDeviceId] is a locally-generated, cryptographically random identifier — never IMEI,
 * hardware serial, Android ID, or advertising ID, and never itself a credential. Loss/reset of
 * this value (app data cleared, reinstall) always requires re-pairing, since it lives in the
 * same app-private storage as the secure credential vault (see `SecureCredentialVault`) and is
 * wiped alongside it.
 *
 * [deviceLabel] is presentation-only — it may default to the device model but carries no
 * security meaning and is never used to authenticate or identify the device to the Connector.
 */
data class PairingDeviceIdentity(
    val logicalDeviceId: String,
    val deviceLabel: String,
)
