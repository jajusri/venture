package com.budcom.android.feature.transaction.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun StructuredRecipientInboxRoute(
    onOpenRoute: (String) -> Unit,
    viewModel: StructuredRecipientInboxViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { target ->
            when (target) {
                is StructuredRecipientInboxNavigation.OpenRoute -> onOpenRoute(target.route)
            }
        }
    }
    StructuredRecipientInboxScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructuredRecipientInboxScreen(
    state: StructuredRecipientInboxUiState,
    onEvent: (StructuredRecipientInboxEvent) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Received") }) },
    ) { padding ->
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("structured_inbox_loading"),
            )
        } else if (state.items.isEmpty()) {
            Text(
                "No received orders yet.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .testTag("structured_inbox_empty"),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("structured_inbox_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.envelopeId }) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEvent(StructuredRecipientInboxEvent.OpenItem(item.envelopeId)) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("structured_inbox_item_${item.envelopeId}"),
                    ) {
                        Text(item.label)
                        Text("Order ${item.orderId} Â· v${item.orderVersion}")
                        Text("From ${item.senderBusinessId}")
                    }
                }
            }
        }
    }
}
