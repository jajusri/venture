package com.budcom.android.feature.businessprofile.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budcom.android.R
import com.budcom.android.feature.masterdata.presentation.MasterDataErrorBlock
import com.budcom.android.feature.masterdata.presentation.MasterDataLoadingIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun BusinessProfileRoute(
    viewModel: BusinessProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickLogoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onEvent(BusinessProfileEvent.LogoPicked(uri))
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                BusinessProfileEffect.RequestLogoPick ->
                    pickLogoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }
    BusinessProfileScreen(state = state, onEvent = viewModel::onEvent)
}

/**
 * Business Profile (MVP-1.3-A) — reuses the established shared loading/error components and
 * honest-empty-state discipline (`MasterDataLoadingIndicator`/`MasterDataErrorBlock`, the same
 * pattern Connect/Dincharya already use). One inline view/edit toggle over the whole entity (see
 * [BusinessProfileUiState]'s own doc comment for why this differs from Party Detail's per-field
 * dialog pattern) — no new visual system introduced. Logo display/picker UI is MVP-1.3-B scope,
 * not built here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessProfileScreen(
    state: BusinessProfileUiState,
    onEvent: (BusinessProfileEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("business_profile_screen"),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.business_profile_title)) })
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoading && !state.hasSavedProfile -> {
                    MasterDataLoadingIndicator(testTag = "business_profile_loading")
                }
                state.error != null && !state.hasSavedProfile -> {
                    MasterDataErrorBlock(
                        error = state.error,
                        onRetry = { onEvent(BusinessProfileEvent.Retry) },
                        errorTestTag = "business_profile_error",
                        retryTestTag = "business_profile_retry",
                    )
                }
                state.isEditing -> {
                    BusinessProfileEditForm(state = state, onEvent = onEvent)
                }
                !state.hasSavedProfile -> {
                    BusinessProfileEmptyState(onEvent = onEvent)
                }
                else -> {
                    BusinessProfileViewContent(state = state, onEvent = onEvent)
                }
            }
        }
    }

    state.notice?.let { message ->
        AlertDialog(
            onDismissRequest = { onEvent(BusinessProfileEvent.DismissNotice) },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(BusinessProfileEvent.DismissNotice) },
                    modifier = Modifier.testTag("business_profile_notice_dismiss"),
                ) { Text("OK") }
            },
            text = { Text(message, modifier = Modifier.testTag("business_profile_notice_message")) },
            modifier = Modifier.testTag("business_profile_notice_dialog"),
        )
    }
}

@Composable
private fun BusinessProfileEmptyState(onEvent: (BusinessProfileEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag("business_profile_empty"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.business_profile_empty_message),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = { onEvent(BusinessProfileEvent.EditTapped) },
            modifier = Modifier.testTag("business_profile_setup"),
        ) {
            Text(stringResource(R.string.business_profile_setup_action))
        }
    }
}

@Composable
private fun BusinessProfileViewContent(state: BusinessProfileUiState, onEvent: (BusinessProfileEvent) -> Unit) {
    val form = state.form
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("business_profile_view"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BusinessProfileLogo(logoFile = state.logoFile, modifier = Modifier.testTag("business_profile_logo"))
        Text(
            text = form.tradingName,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("business_profile_trading_name_value"),
        )
        if (form.legalName.isNotBlank()) {
            ProfileViewRow(stringResource(R.string.business_profile_field_legal_name), form.legalName, "business_profile_legal_name_value")
        }
        val address = listOfNotNull(
            form.addressLine1.takeIf { it.isNotBlank() },
            form.addressCity.takeIf { it.isNotBlank() },
            form.addressState.takeIf { it.isNotBlank() },
            form.addressPincode.takeIf { it.isNotBlank() },
        ).joinToString(", ")
        if (address.isNotBlank()) {
            ProfileViewRow(stringResource(R.string.business_profile_field_address), address, "business_profile_address_value")
        }
        if (form.phone.isNotBlank()) {
            ProfileViewRow(stringResource(R.string.business_profile_field_phone), form.phone, "business_profile_phone_value")
        }
        if (form.email.isNotBlank()) {
            ProfileViewRow(stringResource(R.string.business_profile_field_email), form.email, "business_profile_email_value")
        }
        if (form.gstin.isNotBlank()) {
            ProfileViewRow(stringResource(R.string.business_profile_field_gstin), form.gstin, "business_profile_gstin_value")
        }
        if (form.website.isNotBlank()) {
            ProfileViewRow(stringResource(R.string.business_profile_field_website), form.website, "business_profile_website_value")
        }
        if (form.description.isNotBlank()) {
            ProfileViewRow(stringResource(R.string.business_profile_field_description), form.description, "business_profile_description_value")
        }

        Button(
            onClick = { onEvent(BusinessProfileEvent.EditTapped) },
            modifier = Modifier.testTag("business_profile_edit"),
        ) {
            Text(stringResource(R.string.business_profile_edit_action))
        }
    }
}

/**
 * Decodes [logoFile] off the main thread (`BitmapFactory.decodeFile`, the same pattern already
 * established by `PdfPreviewScreen`'s page rendering — no Coil/Glide dependency exists in this
 * codebase, and adding one for a single small logo image is disproportionate). A missing or
 * corrupt file (`decodeFile` returns `null`, never throws) falls back to a plain placeholder icon —
 * never a crash, never a blank gap.
 */
@Composable
private fun BusinessProfileLogo(logoFile: File?, modifier: Modifier = Modifier) {
    var bitmap by remember(logoFile) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(logoFile) {
        bitmap = logoFile?.let { file -> withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath) } }
    }
    val current = bitmap
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .semantics { contentDescription = "Business logo" },
        contentAlignment = Alignment.Center,
    ) {
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileViewRow(label: String, value: String, testTag: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(testTag))
    }
}

@Composable
private fun BusinessProfileEditForm(state: BusinessProfileUiState, onEvent: (BusinessProfileEvent) -> Unit) {
    val form = state.form
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("business_profile_edit_form"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BusinessProfileLogo(logoFile = state.logoFile, modifier = Modifier.testTag("business_profile_logo"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onEvent(BusinessProfileEvent.ChangeLogoTapped) },
                enabled = !state.isUpdatingLogo,
                modifier = Modifier.testTag("business_profile_change_logo"),
            ) {
                Text(stringResource(if (state.logoFile != null) R.string.business_profile_replace_logo_action else R.string.business_profile_add_logo_action))
            }
            if (state.logoFile != null) {
                OutlinedButton(
                    onClick = { onEvent(BusinessProfileEvent.ClearLogoTapped) },
                    enabled = !state.isUpdatingLogo,
                    modifier = Modifier.testTag("business_profile_remove_logo"),
                ) {
                    Text(stringResource(R.string.business_profile_remove_logo_action))
                }
            }
            if (state.isUpdatingLogo) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp).testTag("business_profile_logo_busy"))
            }
        }
        ProfileTextField(
            value = form.tradingName,
            onValueChange = { onEvent(BusinessProfileEvent.TradingNameChanged(it)) },
            label = stringResource(R.string.business_profile_field_trading_name),
            testTag = "business_profile_input_trading_name",
        )
        ProfileTextField(
            value = form.legalName,
            onValueChange = { onEvent(BusinessProfileEvent.LegalNameChanged(it)) },
            label = stringResource(R.string.business_profile_field_legal_name),
            testTag = "business_profile_input_legal_name",
        )
        ProfileTextField(
            value = form.addressLine1,
            onValueChange = { onEvent(BusinessProfileEvent.AddressLine1Changed(it)) },
            label = stringResource(R.string.business_profile_field_address_line1),
            testTag = "business_profile_input_address_line1",
        )
        ProfileTextField(
            value = form.addressCity,
            onValueChange = { onEvent(BusinessProfileEvent.AddressCityChanged(it)) },
            label = stringResource(R.string.business_profile_field_city),
            testTag = "business_profile_input_city",
        )
        ProfileTextField(
            value = form.addressState,
            onValueChange = { onEvent(BusinessProfileEvent.AddressStateChanged(it)) },
            label = stringResource(R.string.business_profile_field_state),
            testTag = "business_profile_input_state",
        )
        ProfileTextField(
            value = form.addressPincode,
            onValueChange = { onEvent(BusinessProfileEvent.AddressPincodeChanged(it)) },
            label = stringResource(R.string.business_profile_field_pincode),
            testTag = "business_profile_input_pincode",
        )
        ProfileTextField(
            value = form.phone,
            onValueChange = { onEvent(BusinessProfileEvent.PhoneChanged(it)) },
            label = stringResource(R.string.business_profile_field_phone),
            testTag = "business_profile_input_phone",
        )
        ProfileTextField(
            value = form.email,
            onValueChange = { onEvent(BusinessProfileEvent.EmailChanged(it)) },
            label = stringResource(R.string.business_profile_field_email),
            testTag = "business_profile_input_email",
        )
        ProfileTextField(
            value = form.gstin,
            onValueChange = { onEvent(BusinessProfileEvent.GstinChanged(it)) },
            label = stringResource(R.string.business_profile_field_gstin),
            testTag = "business_profile_input_gstin",
        )
        ProfileTextField(
            value = form.website,
            onValueChange = { onEvent(BusinessProfileEvent.WebsiteChanged(it)) },
            label = stringResource(R.string.business_profile_field_website),
            testTag = "business_profile_input_website",
        )
        ProfileTextField(
            value = form.description,
            onValueChange = { onEvent(BusinessProfileEvent.DescriptionChanged(it)) },
            label = stringResource(R.string.business_profile_field_description),
            testTag = "business_profile_input_description",
            singleLine = false,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onEvent(BusinessProfileEvent.SaveTapped) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().testTag("business_profile_save"),
            ) {
                Text(stringResource(if (state.isSaving) R.string.business_profile_saving else R.string.business_profile_save_action))
            }
            OutlinedButton(
                onClick = { onEvent(BusinessProfileEvent.CancelEditTapped) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().testTag("business_profile_cancel"),
            ) {
                Text(stringResource(R.string.business_profile_cancel_action))
            }
        }
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
}
