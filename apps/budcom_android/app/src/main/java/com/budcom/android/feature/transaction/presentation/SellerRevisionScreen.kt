package com.budcom.android.feature.transaction.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
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
fun SellerRevisionRoute(viewModel: SellerRevisionViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SellerRevisionScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerRevisionScreen(state: SellerRevisionUiState, onEvent: (SellerRevisionEvent) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Revise order") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Status: ${state.statusLabel.orEmpty()}", modifier = Modifier.testTag("seller_revision_status"))
            OutlinedTextField(
                value = state.quantity,
                onValueChange = { onEvent(SellerRevisionEvent.QuantityChanged(it)) },
                label = { Text("Quantity") },
                modifier = Modifier.fillMaxWidth().testTag("seller_revision_quantity"),
            )
            Button(
                onClick = { onEvent(SellerRevisionEvent.SendRevision) },
                modifier = Modifier.fillMaxWidth().testTag("seller_revision_send"),
            ) { Text("Send Revision") }
            state.message?.let { Text(it, modifier = Modifier.testTag("seller_revision_message")) }
        }
    }
}
