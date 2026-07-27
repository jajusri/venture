package com.budcom.android.feature.masterdata.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.budcom.android.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterDataHubScreen(
    onOpenLedgers: () -> Unit,
    onOpenStockItems: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("master_data_hub"),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.master_data_title)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.master_data_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onOpenLedgers,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("master_data_open_ledgers")
                    .semantics { contentDescription = "Open ledgers" },
            ) {
                Text(stringResource(R.string.master_data_open_ledgers))
            }
            Button(
                onClick = onOpenStockItems,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("master_data_open_stock_items")
                    .semantics { contentDescription = "Open stock items" },
            ) {
                Text(stringResource(R.string.master_data_open_stock_items))
            }
        }
    }
}
