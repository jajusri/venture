package com.jajusri.venture.feature.connect.presentation

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies exact Intent construction only (Part 32 — "never place a real call or send a real
 * WhatsApp message"). Requires the real Android framework ([Uri.parse]/[Intent] are unittest
 * stubs otherwise), matching this project's existing convention for anything touching
 * `android.*` types (no Robolectric in this codebase).
 */
@RunWith(AndroidJUnit4::class)
class ConnectContactActionsTest {

    @Test
    fun callIntent_isActionDialWithTelUri_neverActionCall() {
        val intent = ConnectContactActions.callIntent("+919876543210")
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals(Uri.parse("tel:+919876543210"), intent.data)
    }

    @Test
    fun whatsAppChatIntent_isActionViewOnWaMeWithoutLeadingPlus() {
        val intent = ConnectContactActions.whatsAppChatIntent("+919876543210")
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse("https://wa.me/919876543210"), intent.data)
    }

    @Test
    fun neither_action_places_a_call_or_sends_a_message_by_itself() {
        // ACTION_DIAL and a plain https ACTION_VIEW both require a further explicit user action
        // inside the resolved app (dialer / WhatsApp) — constructing the Intent alone can never
        // place a call or send a message, which is exactly what this test proves by never calling
        // startActivity at all.
        val call = ConnectContactActions.callIntent("+919876543210")
        val whatsApp = ConnectContactActions.whatsAppChatIntent("+919876543210")
        assertEquals(Intent.ACTION_DIAL, call.action)
        assertEquals(Intent.ACTION_VIEW, whatsApp.action)
    }
}
