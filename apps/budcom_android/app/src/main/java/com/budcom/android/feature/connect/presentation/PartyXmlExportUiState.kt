package com.budcom.android.feature.connect.presentation

import android.net.Uri
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.party.domain.model.PartyExportEvent
import com.budcom.android.feature.party.domain.model.TallyFieldExportCandidate

data class PartyXmlExportUiState(
    val companyId: String? = null,
    val partyId: String? = null,
    val isLoading: Boolean = true,
    val partyName: String? = null,
    val ledgerName: String? = null,
    val hasSourceLink: Boolean = true,
    val candidates: List<TallyFieldExportCandidate> = emptyList(),
    val selectedFieldNames: Set<String> = emptySet(),
    val isGenerating: Boolean = false,
    val isSyncing: Boolean = false,
    val history: List<PartyExportEvent> = emptyList(),
    val error: MasterDataUiError? = null,
    val notice: String? = null,
) {
    val canGenerate: Boolean get() = selectedFieldNames.isNotEmpty() && !isGenerating && !isLoading
}

sealed interface PartyXmlExportEffect {
    data class CreateXmlDocument(val suggestedFilename: String) : PartyXmlExportEffect
}

sealed interface PartyXmlExportEvent {
    data object Load : PartyXmlExportEvent
    data object Retry : PartyXmlExportEvent
    data class FieldSelectionToggled(val fieldName: String) : PartyXmlExportEvent
    data object GenerateTapped : PartyXmlExportEvent
    data class SaveDestinationSelected(val uri: Uri?) : PartyXmlExportEvent
    data object CheckTallyTapped : PartyXmlExportEvent
    data object DismissNotice : PartyXmlExportEvent
}
