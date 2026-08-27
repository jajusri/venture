package com.budcom.android.feature.transaction.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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

@Composable
fun ReceivedRevisionRoute(viewModel: ReceivedRevisionViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReceivedRevisionScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceivedRevisionScreen(state: ReceivedRevisionUiState, onEvent: (ReceivedRevisionEvent) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Revised order") }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.testTag("received_revision_loading"))
            } else {
                Text("Order ${state.orderId.orEmpty()}", modifier = Modifier.testTag("received_revision_id"))
                Text("Version ${state.orderVersion ?: 1}", modifier = Modifier.testTag("received_revision_version"))
                Text(
                    "Status: ${state.statusLabel.orEmpty()}",
                    modifier = Modifier.testTag("received_revision_status"),
                )
                if (state.canAcceptChanges) {
                    Button(
                        onClick = { onEvent(ReceivedRevisionEvent.AcceptChanges) },
                        modifier = Modifier.fillMaxWidth().testTag("received_revision_accept"),
                    ) { Text("Accept Changes") }
                }
            }
            state.message?.let { Text(it, modifier = Modifier.testTag("received_revision_message")) }
        }
    }
}
