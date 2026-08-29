package com.budcom.android.core.trust.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Gate 11/round-6-Gate-9 operational status entry point -- reached from Settings ("Trust &
 * Enrollment Status"). Exposes ONLY the human-safe fields [OperationalStatusUiState] carries; see
 * [statusMessageFor]'s own doc comment for why no technical detail ever reaches this composable. */
@Composable
fun OperationalStatusRoute(onBack: () -> Unit, viewModel: OperationalStatusViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OperationalStatusScreen(state = state, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationalStatusScreen(state: OperationalStatusUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("trust_status_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Trust & Enrollment Status") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("trust_status_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val ready = state.enrollment is EnrollmentUiStatus.Enrolled && state.relayEndpointConfigured
            Card(modifier = Modifier.fillMaxWidth().testTag("trust_status_summary")) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = if (ready) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Text(text = state.statusMessage, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("trust_status_message"))
                    val enrollment = state.enrollment
                    if (enrollment is EnrollmentUiStatus.Enrolled) {
                        Text(text = "Business: ${enrollment.businessId}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("trust_status_business"))
                    }
                }
            }
        }
    }
}
