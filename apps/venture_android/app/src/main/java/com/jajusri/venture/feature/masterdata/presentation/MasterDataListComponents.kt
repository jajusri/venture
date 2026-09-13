package com.jajusri.venture.feature.masterdata.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jajusri.venture.R
import com.jajusri.venture.ui.components.FullScreenLoading

@Composable
fun MasterDataOfflineBanner(
    modifier: Modifier = Modifier,
    testTag: String = "master_data_offline_banner",
) {
    Text(
        text = stringResource(R.string.master_data_offline_banner),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.testTag(testTag),
    )
}

@Composable
fun MasterDataLoadingIndicator(
    modifier: Modifier = Modifier,
    testTag: String = "master_data_loading",
    contentDescription: String? = null,
) {
    FullScreenLoading(modifier = modifier.testTag(testTag), contentDescription = contentDescription)
}

@Composable
fun MasterDataEmptyMessage(
    message: String,
    modifier: Modifier = Modifier,
    testTag: String = "master_data_empty",
) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
    )
}

@Composable
fun MasterDataErrorBlock(
    error: MasterDataUiError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    errorTestTag: String = "master_data_error",
    retryTestTag: String = "master_data_retry",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(errorTestTag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = error.displayMessage(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag(retryTestTag),
        ) {
            Text(stringResource(R.string.master_data_retry))
        }
    }
}
