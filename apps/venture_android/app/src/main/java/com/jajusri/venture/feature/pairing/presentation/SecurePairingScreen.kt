package com.jajusri.venture.feature.pairing.presentation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jajusri.venture.feature.pairing.data.scanner.SecurePairingScanResult
import com.jajusri.venture.ui.theme.VentureTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun SecurePairingRoute(
    onPairingCompleted: () -> Unit = {},
    onOpenStatus: () -> Unit = {},
    viewModel: SecurePairingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        viewModel.interpretRawScanResult(result.contents)?.let {
            viewModel.onEvent(SecurePairingEvent.ScannerResultReceived(it))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scanLauncher.launch(qrScanOptions())
        } else {
            val permanentlyDenied = activity == null ||
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            viewModel.interpretScanOutcome(SecurePairingScanResult.PermissionDenied(permanentlyDenied))?.let {
                viewModel.onEvent(SecurePairingEvent.ScannerResultReceived(it))
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SecurePairingUiEffect.LaunchScanner -> permissionLauncher.launch(Manifest.permission.CAMERA)
                SecurePairingUiEffect.PairingCompleted -> onPairingCompleted()
            }
        }
    }

    SecurePairingScreen(state = state, onEvent = viewModel::onEvent, onOpenStatus = onOpenStatus)
}

private fun qrScanOptions(): ScanOptions = ScanOptions()
    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    .setBeepEnabled(false)
    .setOrientationLocked(true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurePairingScreen(
    state: SecurePairingUiState,
    onEvent: (SecurePairingEvent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenStatus: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("secure_pairing_screen"),
        topBar = {
            TopAppBar(
                // "Pair with Desktop" mirrors Desktop's own "Pair your phone" heading -- both name
                // the action from that platform's point of view, for cross-platform symmetry.
                title = { Text("Pair with Desktop") },
                actions = {
                    // Connector pairing is one capability among many -- it must not block access to
                    // unrelated VENTURE functionality (Trust/Relay status, commercial areas whose own
                    // prerequisites are already satisfied). Shown only while idle (the "stuck, no
                    // escape" state a never-paired device lands on) so it never interrupts an
                    // in-progress scan/confirm/redeem/verify step.
                    if (state.phase == SecurePairingPhase.Idle) {
                        TextButton(onClick = onOpenStatus, modifier = Modifier.testTag("secure_pairing_view_status_button")) {
                            Text("View App Status")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.phase) {
                SecurePairingPhase.Idle -> StartContent(state = state, onEvent = onEvent)
                SecurePairingPhase.ScannerLaunching, SecurePairingPhase.Scanning -> ScanningContent(onEvent = onEvent)
                SecurePairingPhase.ValidatingPayload -> ProgressContent("Validating…")
                SecurePairingPhase.AwaitingConfirmation -> ConfirmationContent(state = state, onEvent = onEvent)
                SecurePairingPhase.Redeeming -> ProgressContent(state.progressDescription ?: "Securing credential…")
                SecurePairingPhase.StoringPendingCredential -> ProgressContent("Securing credential…")
                SecurePairingPhase.VerifyingCredential -> ProgressContent(state.progressDescription ?: "Verifying Connector…")
                SecurePairingPhase.PendingVerification -> PendingVerificationContent(onEvent = onEvent)
                SecurePairingPhase.Active -> ActiveContent(state = state, onEvent = onEvent)
                SecurePairingPhase.PermissionDenied -> PermissionDeniedContent(state = state, onEvent = onEvent)
                SecurePairingPhase.CameraUnavailable -> ErrorContent(
                    title = "Camera unavailable",
                    message = "This device's camera could not be opened for scanning.",
                    onDismiss = { onEvent(SecurePairingEvent.Cancel) },
                )
                SecurePairingPhase.InvalidPayload -> ErrorContent(
                    title = "Invalid QR code",
                    message = state.errorMessage ?: "This QR code is not a valid VENTURE pairing code.",
                    onDismiss = { onEvent(SecurePairingEvent.Cancel) },
                    onRetry = { onEvent(SecurePairingEvent.StartQrScan) },
                )
                SecurePairingPhase.ExpiredPayload -> ErrorContent(
                    title = "QR code expired",
                    message = state.errorMessage ?: "This QR code has expired. Ask Desktop to show a new one.",
                    onDismiss = { onEvent(SecurePairingEvent.Cancel) },
                    onRetry = { onEvent(SecurePairingEvent.StartQrScan) },
                )
                SecurePairingPhase.FingerprintRejected -> ErrorContent(
                    title = "Security check failed",
                    message = state.errorMessage ?: "This Connector's security details could not be verified.",
                    onDismiss = { onEvent(SecurePairingEvent.Cancel) },
                    isSecurityError = true,
                )
                SecurePairingPhase.NetworkFailure -> ErrorContent(
                    title = "Network unavailable",
                    message = state.errorMessage ?: "Could not reach the Connector. Check the network and try again.",
                    onDismiss = { onEvent(SecurePairingEvent.Cancel) },
                    onRetry = if (state.canRetryPendingVerification) {
                        { onEvent(SecurePairingEvent.RetryPendingVerification) }
                    } else {
                        { onEvent(SecurePairingEvent.StartQrScan) }
                    },
                )
                SecurePairingPhase.Unauthorized, SecurePairingPhase.RePairRequired -> RePairRequiredContent(onEvent = onEvent)
                SecurePairingPhase.Failed -> ErrorContent(
                    title = "Something went wrong",
                    message = state.errorMessage ?: "Pairing could not be completed.",
                    onDismiss = { onEvent(SecurePairingEvent.Cancel) },
                )
            }
        }
    }
}

@Composable
private fun StartContent(state: SecurePairingUiState, onEvent: (SecurePairingEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().testTag("secure_pairing_start"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "Pair this device securely with your VENTURE Connector by scanning the QR code shown on the Desktop screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { onEvent(SecurePairingEvent.StartQrScan) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("secure_pairing_scan_button")
                .semantics { contentDescription = "Scan Desktop QR" },
        ) {
            Text("Scan Desktop QR")
        }

        if (state.shortCodeEntryAvailable) {
            if (state.showShortCodeEntry) {
                ShortCodeEntryContent(state = state, onEvent = onEvent)
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { onEvent(SecurePairingEvent.OpenTrustedShortCodeEntry) },
                    modifier = Modifier.fillMaxWidth().testTag("secure_pairing_open_short_code_button"),
                ) {
                    Text("Enter code instead")
                }
            }
        }
    }
}

@Composable
private fun ShortCodeEntryContent(state: SecurePairingUiState, onEvent: (SecurePairingEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("secure_pairing_short_code_entry"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Code entry works only for a Connector this device already trusts. For a new Connector, scan the Desktop QR.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.shortCodeInput,
            onValueChange = { onEvent(SecurePairingEvent.ShortCodeInputChanged(it)) },
            label = { Text("8-character code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("secure_pairing_short_code_field"),
        )
        state.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("secure_pairing_error"))
        }
        Button(
            onClick = { onEvent(SecurePairingEvent.SubmitTrustedShortCode) },
            modifier = Modifier.fillMaxWidth().testTag("secure_pairing_submit_short_code_button"),
        ) {
            Text("Submit code")
        }
    }
}

@Composable
private fun ScanningContent(onEvent: (SecurePairingEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().testTag("secure_pairing_scanning"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text("Opening camera…")
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { onEvent(SecurePairingEvent.Cancel) },
            modifier = Modifier.testTag("secure_pairing_scan_cancel_button").semantics { contentDescription = "Cancel scanning" },
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ConfirmationContent(state: SecurePairingUiState, onEvent: (SecurePairingEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().testTag("secure_pairing_confirmation"),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Confirm this Connector", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.connectorName ?: "Unknown Connector",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("secure_pairing_connector_name"),
        )
        Text(
            text = "Fingerprint: ${state.abbreviatedFingerprint ?: "—"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("secure_pairing_fingerprint"),
        )
        Text(
            text = "This fingerprint came from the Desktop QR code you just scanned.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onEvent(SecurePairingEvent.ConfirmConnector) },
            modifier = Modifier.fillMaxWidth().testTag("secure_pairing_confirm_button"),
        ) {
            Text("Confirm Secure Pairing")
        }
        OutlinedButton(
            onClick = { onEvent(SecurePairingEvent.RejectConnector) },
            modifier = Modifier.fillMaxWidth().testTag("secure_pairing_scan_again_button"),
        ) {
            Text("Scan Again")
        }
    }
}

@Composable
private fun ProgressContent(description: String) {
    Column(
        modifier = Modifier.fillMaxSize().testTag("secure_pairing_progress"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = description, modifier = Modifier.testTag("secure_pairing_progress_text"))
    }
}

@Composable
private fun PendingVerificationContent(onEvent: (SecurePairingEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().testTag("secure_pairing_pending_verification"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "Your credential was securely saved, but we couldn't confirm it with the Connector yet.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "This is usually a temporary connection issue — try again rather than scanning a new code.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { onEvent(SecurePairingEvent.RetryPendingVerification) },
            modifier = Modifier.fillMaxWidth().testTag("secure_pairing_retry_verification_button"),
        ) {
            Text("Retry Verification")
        }
    }
}

@Composable
private fun ActiveContent(state: SecurePairingUiState, onEvent: (SecurePairingEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().testTag("secure_pairing_active"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(text = "Secure pairing complete", style = MaterialTheme.typography.titleMedium)
        Text(text = state.connectorName ?: "", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "This device now has a secure credential for this Connector.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("secure_pairing_active_description"),
        )
        Text(
            text = "To replace this trust, scan a fresh QR from Desktop and explicitly confirm its Connector and fingerprint.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("secure_pairing_active_replace_notice"),
        )
        OutlinedButton(
            onClick = { onEvent(SecurePairingEvent.StartQrScan) },
            modifier = Modifier.fillMaxWidth().testTag("secure_pairing_replace_button"),
        ) {
            Text("Replace / Re-pair Connector")
        }
    }
}

@Composable
private fun RePairRequiredContent(onEvent: (SecurePairingEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().testTag("secure_pairing_re_pair_required"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(text = "Secure pairing needs to be redone", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "The secure credential for this Connector could not be recovered or verified.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Your cached company and accounting data on this device is unaffected.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("secure_pairing_re_pair_data_preserved_notice"),
        )
        Button(
            onClick = { onEvent(SecurePairingEvent.StartQrScan) },
            modifier = Modifier.fillMaxWidth().testTag("secure_pairing_scan_new_qr_button"),
        ) {
            Text("Scan New QR")
        }
    }
}

@Composable
private fun PermissionDeniedContent(state: SecurePairingUiState, onEvent: (SecurePairingEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().testTag("secure_pairing_permission_denied"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(text = "Camera permission needed", style = MaterialTheme.typography.titleMedium)
        if (state.cameraPermanentlyDenied) {
            Text(
                text = "Camera access was denied. Enable it for VENTURE from your device Settings to scan a QR code.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("secure_pairing_permission_settings_notice"),
            )
        } else {
            Text(
                text = "VENTURE needs camera access to scan the Desktop QR code.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { onEvent(SecurePairingEvent.StartQrScan) },
                modifier = Modifier.fillMaxWidth().testTag("secure_pairing_request_permission_again_button"),
            ) {
                Text("Try Again")
            }
        }
        OutlinedButton(
            onClick = { onEvent(SecurePairingEvent.Cancel) },
            modifier = Modifier.fillMaxWidth().testTag("secure_pairing_permission_cancel_button"),
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ErrorContent(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    isSecurityError: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxSize().testTag("secure_pairing_error_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (isSecurityError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("secure_pairing_error_title"),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("secure_pairing_error_message"),
        )
        if (onRetry != null) {
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().testTag("secure_pairing_error_retry_button"),
            ) {
                Text("Try Again")
            }
        }
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().testTag("secure_pairing_error_dismiss_button"),
        ) {
            Text("Cancel")
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun SecurePairingConfirmationPreview() {
    VentureTheme {
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
