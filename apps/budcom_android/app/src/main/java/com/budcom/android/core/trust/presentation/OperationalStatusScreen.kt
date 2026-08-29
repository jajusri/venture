package com.budcom.android.core.trust.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Minimal Gate 11 status surface -- ONLY the human-safe fields [OperationalStatusUiState] exposes.
 * Not yet wired into the app's navigation graph (no enrollment-entry screen exists yet to link to);
 * see the final report's Gate 11 scoping note.
 */
@Composable
fun OperationalStatusScreen(viewModel: OperationalStatusViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val ready = state.enrollment is EnrollmentUiStatus.Enrolled && state.relayEndpointConfigured
            Icon(
                imageVector = if (ready) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
            )
            Text(text = state.statusMessage, style = MaterialTheme.typography.bodyLarge)
            val enrollment = state.enrollment
            if (enrollment is EnrollmentUiStatus.Enrolled) {
                Text(text = "Business: ${enrollment.businessId}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
