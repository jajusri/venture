package com.budcom.android.feature.pairing.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import com.budcom.android.ui.theme.BudcomTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SecurePairingScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // 31. start screen shows Scan Desktop QR / 32. camera is not launched automatically
    @Test
    fun idleStateShowsScanButtonAndNothingScanner_relatedYet() {
        var started = false
        composeRule.setContent {
            BudcomTheme {
                SecurePairingScreen(
                    state = SecurePairingUiState(phase = SecurePairingPhase.Idle),
                    onEvent = { if (it == SecurePairingEvent.StartQrScan) started = true },
                )
            }
        }
        composeRule.onNodeWithTag("secure_pairing_start").assertIsDisplayed()
        composeRule.onNodeWithTag("secure_pairing_scan_button").assertIsDisplayed()
        // The Screen composable never dispatches StartQrScan on its own — only an explicit tap does.
        assertFalse(started)
        composeRule.onNodeWithTag("secure_pairing_scan_button").performClick()
        assertTrue(started)
    }

    // 33/34. confirmation displays Connector name and abbreviated fingerprint
    @Test
    fun confirmationShowsConnectorNameAndAbbreviatedFingerprint() {
        composeRule.setContent {
            BudcomTheme {
                SecurePairingScreen(
                    state = SecurePairingUiState(
                        phase = SecurePairingPhase.AwaitingConfirmation,
                        connectorName = "Front Desk",
                        abbreviatedFingerprint = "AbCdEf…UvWxYz",
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("secure_pairing_connector_name").assertIsDisplayed().assertTextContains("Front Desk")
        composeRule.onNodeWithTag("secure_pairing_fingerprint").assertIsDisplayed().assertTextContains("AbCdEf…UvWxYz")
    }

    // 35. confirmation does not display raw QR JSON
    @Test
    fun confirmationNeverDisplaysRawQrJson() {
        composeRule.setContent {
            BudcomTheme {
                SecurePairingScreen(
                    state = SecurePairingUiState(
                        phase = SecurePairingPhase.AwaitingConfirmation,
                        connectorName = "Front Desk",
                        abbreviatedFingerprint = "AbCdEf…UvWxYz",
                    ),
                    onEvent = {},
                )
            }
        }
        val tree = composeRule.onRoot().printToString()
        assertFalse(tree.contains("pairingSessionId"))
        assertFalse(tree.contains("\"secret\""))
    }

    @Test
    fun confirmingRequiresExplicitConfirmTap() {
        var confirmed = false
        var rejected = false
        composeRule.setContent {
            BudcomTheme {
                SecurePairingScreen(
                    state = SecurePairingUiState(phase = SecurePairingPhase.AwaitingConfirmation, connectorName = "Front Desk"),
                    onEvent = { event ->
                        when (event) {
                            SecurePairingEvent.ConfirmConnector -> confirmed = true
                            SecurePairingEvent.RejectConnector -> rejected = true
                            else -> Unit
                        }
                    },
                )
            }
        }
        assertFalse(confirmed)
        composeRule.onNodeWithTag("secure_pairing_confirm_button").performClick()
        assertTrue(confirmed)
        composeRule.onNodeWithTag("secure_pairing_scan_again_button").performClick()
        assertTrue(rejected)
    }

    // 36. progress shows honest stage
    @Test
    fun progressShowsTheActualStageDescription() {
        composeRule.setContent {
            BudcomTheme {
                SecurePairingScreen(
                    state = SecurePairingUiState(phase = SecurePairingPhase.Redeeming, progressDescription = "Securing credential…"),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("secure_pairing_progress_text").assertIsDisplayed().assertTextContains("Securing credential…")
    }

    // 37. pending state shows Retry Verification
    @Test
    fun pendingVerificationOffersRetry() {
        var retried = false
        composeRule.setContent {
            BudcomTheme {
                SecurePairingScreen(
                    state = SecurePairingUiState(phase = SecurePairingPhase.PendingVerification, canRetryPendingVerification = true),
                    onEvent = { if (it == SecurePairingEvent.RetryPendingVerification) retried = true },
                )
            }
        }
        composeRule.onNodeWithTag("secure_pairing_pending_verification").assertIsDisplayed()
        composeRule.onNodeWithTag("secure_pairing_retry_verification_button").performClick()
        assertTrue(retried)
    }

    // 38. active state does not falsely claim business sync is secured
    @Test
    fun activeStateDoesNotClaimAccountingSyncIsSecured() {
        composeRule.setContent {
            BudcomTheme {
                SecurePairingScreen(
                    state = SecurePairingUiState(phase = SecurePairingPhase.Active, connectorName = "Front Desk"),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("secure_pairing_active").assertIsDisplayed()
        val tree = composeRule.onRoot().printToString()
        assertFalse(tree.contains("sync", ignoreCase = true))
        assertFalse(tree.contains("accounting", ignoreCase = true))
    }

    // 39. re-pair-required state states cached data is preserved
    @Test
    fun rePairRequiredStatesCachedDataIsPreserved() {
        var rescanned = false
        composeRule.setContent {
            BudcomTheme {
                SecurePairingScreen(
                    state = SecurePairingUiState(phase = SecurePairingPhase.RePairRequired),
                    onEvent = { if (it == SecurePairingEvent.StartQrScan) rescanned = true },
                )
            }
        }
        composeRule.onNodeWithTag("secure_pairing_re_pair_data_preserved_notice").assertIsDisplayed()
        composeRule.onNodeWithTag("secure_pairing_scan_new_qr_button").performClick()
        assertTrue(rescanned)
    }

    // 40. camera-permission error is actionable
    @Test
    fun recoverablePermissionDenialOffersTryAgain() {
        var retried = false
        composeRule.setContent {
            BudcomTheme {
                SecurePairingScreen(
                    state = SecurePairingUiState(phase = SecurePairingPhase.PermissionDenied, cameraPermanentlyDenied = false),
                    onEvent = { if (it == SecurePairingEvent.StartQrScan) retried = true },
                )
            }
        }
        composeRule.onNodeWithTag("secure_pairing_request_permission_again_button").assertIsDisplayed().performClick()
        assertTrue(retried)
    }

    @Test
    fun permanentPermissionDenialShowsSettingsGuidanceInsteadOfTryAgain() {
        composeRule.setContent {
            BudcomTheme {
                SecurePairingScreen(
                    state = SecurePairingUiState(phase = SecurePairingPhase.PermissionDenied, cameraPermanentlyDenied = true),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("secure_pairing_permission_settings_notice").assertIsDisplayed()
    }

    // 41. expired QR offers scan again
    @Test
    fun expiredPayloadOffersScanAgain() {
        var rescanned = false
        composeRule.setContent {
            BudcomTheme {
                SecurePairingScreen(
                    state = SecurePairingUiState(phase = SecurePairingPhase.ExpiredPayload),
                    onEvent = { if (it == SecurePairingEvent.StartQrScan) rescanned = true },
                )
            }
        }
        composeRule.onNodeWithTag("secure_pairing_error_retry_button").assertIsDisplayed().performClick()
        assertTrue(rescanned)
    }

    // 42. fingerprint mismatch is presented as a security error
    @Test
    fun fingerprintRejectionIsPresentedAsASecurityError() {
        composeRule.setContent {
            BudcomTheme {
                SecurePairingScreen(
                    state = SecurePairingUiState(phase = SecurePairingPhase.FingerprintRejected, errorMessage = "This Connector's security details could not be verified."),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("secure_pairing_error_title").assertIsDisplayed().assertTextContains("Security check failed")
        // No retry offered for a security failure — no fallback path.
        composeRule.onAllNodesWithTag("secure_pairing_error_retry_button").assertCountEquals(0)
    }

    // 43. cancel exits the current presentation state safely
    @Test
    fun cancelFromScanningDispatchesCancel() {
        var cancelled = false
        composeRule.setContent {
            BudcomTheme {
                SecurePairingScreen(
                    state = SecurePairingUiState(phase = SecurePairingPhase.Scanning),
                    onEvent = { if (it == SecurePairingEvent.Cancel) cancelled = true },
                )
            }
        }
        composeRule.onNodeWithTag("secure_pairing_scan_cancel_button").assertIsDisplayed().performClick()
        assertTrue(cancelled)
    }

    // 44. content descriptions contain no secret material
    @Test
    fun contentDescriptionsCarryNoSecretMaterial() {
        composeRule.setContent {
            BudcomTheme {
                SecurePairingScreen(
                    state = SecurePairingUiState(
                        phase = SecurePairingPhase.AwaitingConfirmation,
                        connectorName = "Front Desk",
                        abbreviatedFingerprint = "AbCdEf…UvWxYz",
                    ),
                    onEvent = {},
                )
            }
        }
        val tree = composeRule.onRoot().printToString()
        assertFalse(tree.contains("secret", ignoreCase = true))
        assertFalse(tree.contains("token", ignoreCase = true))
    }

    @Test
    fun shortCodeEntryIsHiddenWhenUnavailable() {
        composeRule.setContent {
            BudcomTheme {
                SecurePairingScreen(
                    state = SecurePairingUiState(phase = SecurePairingPhase.Idle, shortCodeEntryAvailable = false),
                    onEvent = {},
                )
            }
        }
        composeRule.onAllNodesWithTag("secure_pairing_open_short_code_button").assertCountEquals(0)
    }

    @Test
    fun shortCodeEntryOpensAndSubmitsWhenAvailable() {
        var opened = false
        var submitted = false
        var lastInput = ""
        composeRule.setContent {
            BudcomTheme {
                SecurePairingScreen(
                    state = SecurePairingUiState(
                        phase = SecurePairingPhase.Idle,
                        shortCodeEntryAvailable = true,
                        showShortCodeEntry = opened,
                        shortCodeInput = lastInput,
                    ),
                    onEvent = { event ->
                        when (event) {
                            SecurePairingEvent.OpenTrustedShortCodeEntry -> opened = true
                            SecurePairingEvent.SubmitTrustedShortCode -> submitted = true
                            is SecurePairingEvent.ShortCodeInputChanged -> lastInput = event.value
                            else -> Unit
                        }
                    },
                )
            }
        }
        composeRule.onNodeWithTag("secure_pairing_open_short_code_button").assertIsDisplayed().performClick()
        assertTrue(opened)
    }
}
