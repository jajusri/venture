package com.budcom.android.feature.connect.presentation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.masterdata.ledger.domain.port.LedgerLiveDetailPort
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.masterdata.presentation.displayMessage
import com.budcom.android.feature.masterdata.presentation.toMasterDataUiError
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.PartyFieldNames
import com.budcom.android.feature.party.domain.usecase.GetExportCandidatesUseCase
import com.budcom.android.feature.party.domain.usecase.GetExportHistoryUseCase
import com.budcom.android.feature.party.domain.usecase.GetPartyByIdUseCase
import com.budcom.android.feature.party.domain.usecase.GetSourceLinkForPartyUseCase
import com.budcom.android.feature.party.domain.usecase.ReconcileExportedFieldFromTallyUseCase
import com.budcom.android.feature.party.domain.usecase.RecordExportUseCase
import com.budcom.android.feature.party.domain.xml.TallyLedgerXmlGenerator
import com.budcom.android.feature.party.sharing.PartyXmlExportCoordinator
import com.budcom.android.feature.party.sharing.PartyXmlExportResult
import com.budcom.android.feature.party.sharing.PreparedPartyXmlExport
import com.budcom.android.feature.party.sharing.sanitizedPartyXmlExportFilename
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tally XML enrichment change-review + export + re-sync screen (MVP-1.1-D). Never eligible for a
 * Prospect (no [com.budcom.android.feature.party.domain.model.PartySourceLink] exists) — Party
 * Detail only offers the entry point when [PartyDetailUiState.hasAccountingLink] is true, and this
 * ViewModel re-checks the same condition defensively at load time.
 */
@HiltViewModel
class PartyXmlExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPartyById: GetPartyByIdUseCase,
    private val getSourceLinkForParty: GetSourceLinkForPartyUseCase,
    private val getExportCandidates: GetExportCandidatesUseCase,
    private val recordExport: RecordExportUseCase,
    private val reconcileExportedFieldFromTally: ReconcileExportedFieldFromTallyUseCase,
    private val getExportHistory: GetExportHistoryUseCase,
    private val ledgerLiveDetailPort: LedgerLiveDetailPort,
    private val xmlExportCoordinator: PartyXmlExportCoordinator,
    private val companySession: CompanySessionPort,
) : ViewModel() {

    private val partyId: String = requireNotNull(savedStateHandle.get<String>(PARTY_ID_ARG)) { "partyId is required" }

    companion object {
        const val PARTY_ID_ARG = "partyId"
    }

    private val _uiState = MutableStateFlow(PartyXmlExportUiState(partyId = partyId))
    val uiState: StateFlow<PartyXmlExportUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<PartyXmlExportEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<PartyXmlExportEffect> = _effects.asSharedFlow()

    private var pendingXml: PreparedPartyXmlExport? = null

    init {
        onEvent(PartyXmlExportEvent.Load)
    }

    fun onEvent(event: PartyXmlExportEvent) {
        when (event) {
            PartyXmlExportEvent.Load, PartyXmlExportEvent.Retry -> load()
            is PartyXmlExportEvent.FieldSelectionToggled -> _uiState.update { state ->
                val selected = state.selectedFieldNames
                state.copy(selectedFieldNames = if (event.fieldName in selected) selected - event.fieldName else selected + event.fieldName)
            }
            PartyXmlExportEvent.GenerateTapped -> generate()
            is PartyXmlExportEvent.SaveDestinationSelected -> saveTo(event.uri)
            PartyXmlExportEvent.CheckTallyTapped -> checkTally()
            PartyXmlExportEvent.DismissNotice -> _uiState.update { it.copy(notice = null) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val companyId = companySession.observeSelectedCompanyId().first()
            if (companyId.isNullOrBlank()) {
                _uiState.update { it.copy(isLoading = false, error = MasterDataUiError.Message("Select a company before exporting to Tally.")) }
                return@launch
            }
            val party = getPartyById(companyId, partyId)
            if (party == null) {
                _uiState.update { it.copy(isLoading = false, error = MasterDataUiError.Message("This party could not be found.")) }
                return@launch
            }
            val sourceLink = getSourceLinkForParty(companyId, partyId)
            if (sourceLink == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        companyId = companyId,
                        partyName = party.displayName,
                        hasSourceLink = false,
                        error = MasterDataUiError.Message("This party has no linked Tally ledger, so there is nothing to export."),
                    )
                }
                return@launch
            }

            val candidates = getExportCandidates(companyId, partyId)
            val history = getExportHistory(companyId, partyId)
            val defaultSelection = candidates
                .filter { it.state == FieldProvenanceState.BudcomOnlyPending || it.state == FieldProvenanceState.Conflict }
                .map { it.fieldName }
                .toSet()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    companyId = companyId,
                    partyName = party.displayName,
                    ledgerName = sourceLink.externalDisplayName,
                    hasSourceLink = true,
                    candidates = candidates,
                    selectedFieldNames = defaultSelection,
                    history = history,
                    error = null,
                )
            }
        }
    }

    private fun generate() {
        val state = _uiState.value
        val companyId = state.companyId ?: return
        val ledgerName = state.ledgerName ?: return
        if (!state.canGenerate) return

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null) }
            val sourceLink = getSourceLinkForParty(companyId, partyId)
            val companyName = companySession.observeSelectedCompany().first()?.name

            if (sourceLink == null || companyName.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        notice = "Could not resolve the company or ledger identity for export. Please try again.",
                    )
                }
                return@launch
            }

            val fields = state.candidates
                .filter { it.fieldName in state.selectedFieldNames }
                .mapNotNull { candidate -> candidate.budcomValue?.let { candidate.fieldName to it } }
                .toMap()

            if (fields.isEmpty()) {
                _uiState.update { it.copy(isGenerating = false, notice = "The selected field(s) have no BUDCOM value to export.") }
                return@launch
            }

            val xml = runCatching {
                TallyLedgerXmlGenerator.generate(
                    TallyLedgerXmlGenerator.Request(
                        companyName = companyName,
                        ledgerName = ledgerName,
                        ledgerGuid = sourceLink.externalEntityId,
                        fields = fields,
                    ),
                )
            }.getOrElse {
                _uiState.update { s -> s.copy(isGenerating = false, notice = "The export XML could not be generated: ${it.message}") }
                return@launch
            }

            val suggestedFilename = sanitizedPartyXmlExportFilename(ledgerName, System.currentTimeMillis())
            when (val prepared = xmlExportCoordinator.prepareXml(xml, suggestedFilename)) {
                is PartyXmlExportResult.Success -> {
                    pendingXml = prepared.value
                    _uiState.update { it.copy(isGenerating = false) }
                    _effects.tryEmit(PartyXmlExportEffect.CreateXmlDocument(suggestedFilename))
                }
                is PartyXmlExportResult.Failure -> {
                    _uiState.update { it.copy(isGenerating = false, notice = prepared.message) }
                }
            }
        }
    }

    private fun saveTo(uri: Uri?) {
        val prepared = pendingXml ?: return
        if (uri == null) {
            xmlExportCoordinator.releaseXml(prepared)
            pendingXml = null
            _uiState.update { it.copy(notice = "Export cancelled.") }
            return
        }
        val companyId = _uiState.value.companyId ?: return
        val selectedFields = _uiState.value.selectedFieldNames.toList()

        viewModelScope.launch {
            when (val result = xmlExportCoordinator.saveXml(prepared, uri)) {
                is PartyXmlExportResult.Success -> {
                    recordExport(companyId, partyId, prepared.suggestedFilename, selectedFields)
                    pendingXml = null
                    _uiState.update { it.copy(notice = "Tally export XML saved. Import it into Tally, then use \"Check Tally\" to confirm.") }
                    load()
                }
                is PartyXmlExportResult.Failure -> {
                    xmlExportCoordinator.releaseXml(prepared)
                    pendingXml = null
                    _uiState.update { it.copy(notice = result.message) }
                }
            }
        }
    }

    private fun checkTally() {
        val state = _uiState.value
        val companyId = state.companyId ?: return
        val exportedFields = state.candidates.filter { it.state == FieldProvenanceState.Exported }
        if (exportedFields.isEmpty()) {
            _uiState.update { it.copy(notice = "No exported fields are waiting on a Tally confirmation.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            val sourceLink = getSourceLinkForParty(companyId, partyId)
            if (sourceLink == null) {
                _uiState.update { it.copy(isSyncing = false, notice = "This party's Tally ledger link is no longer available.") }
                return@launch
            }

            when (val result = ledgerLiveDetailPort.fetchContactDetails(sourceLink.externalEntityId)) {
                is AppResult.Success -> {
                    val snapshot = result.value
                    val rawByField = mapOf(
                        PartyFieldNames.PRIMARY_PHONE to snapshot.mobile,
                        PartyFieldNames.PRIMARY_EMAIL to snapshot.email,
                        PartyFieldNames.ADDRESS_LINE1 to snapshot.address,
                        PartyFieldNames.ADDRESS_STATE to snapshot.state,
                        PartyFieldNames.ADDRESS_PINCODE to snapshot.pincode,
                        PartyFieldNames.GSTIN to snapshot.gstin,
                    )
                    var confirmed = 0
                    var conflicted = 0
                    exportedFields.forEach { candidate ->
                        val newState = reconcileExportedFieldFromTally(companyId, partyId, candidate.fieldName, rawByField[candidate.fieldName])
                        when (newState) {
                            FieldProvenanceState.ConfirmedFromTally -> confirmed++
                            FieldProvenanceState.Conflict -> conflicted++
                            else -> Unit
                        }
                    }
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            notice = "Checked Tally: $confirmed confirmed, $conflicted needing review.",
                        )
                    }
                    load()
                }
                is AppResult.Failure -> {
                    val uiError = result.error.toMasterDataUiError()
                    _uiState.update { it.copy(isSyncing = false, notice = "Could not reach the Connector: ${uiError.displayMessage()}") }
                }
            }
        }
    }
}
