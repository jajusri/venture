package com.budcom.android.feature.connect.presentation

data class ProspectCreateUiState(
    val displayName: String = "",
    val phone: String = "",
    val email: String = "",
    val addressLine1: String = "",
    val addressCity: String = "",
    val addressState: String = "",
    val addressPincode: String = "",
    val note: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = displayName.isNotBlank() && !isSaving
}

sealed interface ProspectCreateEffect {
    data class Created(val partyId: String) : ProspectCreateEffect
}

sealed interface ProspectCreateEvent {
    data class DisplayNameChanged(val value: String) : ProspectCreateEvent
    data class PhoneChanged(val value: String) : ProspectCreateEvent
    data class EmailChanged(val value: String) : ProspectCreateEvent
    data class AddressLine1Changed(val value: String) : ProspectCreateEvent
    data class AddressCityChanged(val value: String) : ProspectCreateEvent
    data class AddressStateChanged(val value: String) : ProspectCreateEvent
    data class AddressPincodeChanged(val value: String) : ProspectCreateEvent
    data class NoteChanged(val value: String) : ProspectCreateEvent
    data object Save : ProspectCreateEvent
}
