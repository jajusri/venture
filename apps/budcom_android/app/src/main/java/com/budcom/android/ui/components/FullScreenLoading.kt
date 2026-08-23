package com.budcom.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.budcom.android.ui.theme.BudcomTheme

/**
 * Full-screen centered loading indicator for feature screens. [contentDescription] is optional
 * and defaults to none (unchanged behavior for existing callers) -- screens that want to announce
 * what's loading to TalkBack can pass one, e.g. "Loading customers".
 */
@Composable
fun FullScreenLoading(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .let { m -> contentDescription?.let { m.semantics { this.contentDescription = it } } ?: m },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Preview(showBackground = true)
@Composable
private fun FullScreenLoadingPreview() {
    BudcomTheme {
        FullScreenLoading()
    }
}
