package com.jajusri.venture.feature.connect.presentation

import android.content.Intent
import android.net.Uri

/**
 * Pure Intent builders for Connect's Call/WhatsApp row actions. Deliberately Context-free (no
 * PackageManager resolution here) — the caller (a Compose Route with `LocalContext.current`)
 * decides whether/how to launch and handles the no-resolving-app case, matching this app's
 * existing convention of keeping ViewModels free of Android framework types (see
 * [com.jajusri.venture.feature.connect.presentation.ConnectEffect], which carries plain phone
 * strings, not Intents).
 */
object ConnectContactActions {

    /**
     * [Intent.ACTION_DIAL] (not `ACTION_CALL`) — opens the system dialer pre-filled with the
     * number but never places the call itself, satisfying "no silent calls" without needing the
     * `CALL_PHONE` permission this app does not declare.
     */
    fun callIntent(phoneE164: String): Intent =
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneE164"))

    /**
     * The standard `wa.me` deep link: opens a WhatsApp chat with the resolved number directly if
     * WhatsApp is installed (or a browser fallback otherwise), but still requires an explicit
     * further action inside WhatsApp itself to actually send anything — satisfies "no silent
     * sending" without needing a second phone-number resolver (reuses the same
     * [com.jajusri.venture.core.util.PhoneNumberNormalizer]-validated E.164 number already used
     * for [callIntent]).
     */
    fun whatsAppChatIntent(phoneE164: String): Intent {
        val digits = phoneE164.removePrefix("+")
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))
    }
}
