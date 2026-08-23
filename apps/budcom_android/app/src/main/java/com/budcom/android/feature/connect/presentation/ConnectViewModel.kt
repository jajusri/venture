package com.budcom.android.feature.connect.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.util.PhoneNumberNormalizer
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.masterdata.domain.MasterDataBrowserDefaults
import com.budcom.android.feature.masterdata.ledger.domain.model.Ledger
import com.budcom.android.feature.masterdata.ledger.domain.port.LedgerBulkContactDetailPort
import com.budcom.android.feature.masterdata.ledger.domain.port.LedgerSnapshotPort
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.masterdata.presentation.displayMessage
import com.budcom.android.feature.masterdata.presentation.toMasterDataUiError
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyPage
import com.budcom.android.feature.party.domain.model.PartySourceLink
import com.budcom.android.feature.party.domain.model.Tag
import com.budcom.android.feature.party.domain.usecase.ApplyLedgerContactDetailsBulkUseCase
import com.budcom.android.feature.party.domain.usecase.GetPartySourceLinksForCompanyUseCase
import com.budcom.android.feature.party.domain.usecase.GetPartyTagsForCompanyUseCase
import com.budcom.android.feature.party.domain.usecase.ListPartiesByClassificationUseCase
import com.budcom.android.feature.party.domain.usecase.SearchPartiesUseCase
import com.budcom.android.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** TD-041 mitigation: how long to wait before retrying a classification-listing read that came
 * back empty for a company/tab known to have data -- long enough that the observed self-healing
 * (previously seen recovering within ~1.75s) has room to resolve, short enough not to read as a
 * stall. */
private const val EMPTY_READ_RETRY_DELAY_MS = 500L

/**
 * Connect Browser (MVP-1.1-B) — local-first, company-scoped Customers/Prospects list with
 * accounting deep links. Never performs a live Tally/Connector call: Party data comes from the
 * MVP-1.1-A local repository, and the current-balance/tag enrichment join reads only already-
 * synced local Ledger/Tag data (via [LedgerSnapshotPort] and the bulk Party bulk-read use cases),
 * never per-row, to stay bounded and avoid N+1 queries on realistic Party counts.
 */
@HiltViewModel
class ConnectViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val listPartiesByClassification: ListPartiesByClassificationUseCase,
    private val searchParties: SearchPartiesUseCase,
    private val getPartySourceLinksForCompany: GetPartySourceLinksForCompanyUseCase,
    private val getPartyTagsForCompany: GetPartyTagsForCompanyUseCase,
    private val ledgerSnapshotPort: LedgerSnapshotPort,
    private val ledgerBulkContactDetailPort: LedgerBulkContactDetailPort,
    private val applyLedgerContactDetailsBulk: ApplyLedgerContactDetailsBulkUseCase,
    private val companySession: CompanySessionPort,
    private val connectivityObserver: NetworkConnectivityObserver,
) : ViewModel() {

    private val initialQuery = savedStateHandle.get<String>(Routes.QUERY_ARG).orEmpty()

    private val _uiState = MutableStateFlow(ConnectUiState(searchQuery = initialQuery))
    val uiState: StateFlow<ConnectUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ConnectEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<ConnectEffect> = _effects.asSharedFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    // Per-company enrichment caches: rebuilt once per Load/Refresh/company-change, then reused
    // across every search-driven and tab-driven page reload for that same company session — a
    // deliberate choice so typing in the search box never re-triggers two extra full-company
    // reads on every keystroke (Part 10 — avoid full-table/N+1 query patterns).
    private var sourceLinksByPartyId: Map<String, PartySourceLink> = emptyMap()
    private var ledgersById: Map<String, Ledger> = emptyMap()
    private var tagsByPartyId: Map<String, List<Tag>> = emptyMap()
    private var enrichmentCompanyId: String? = null
    private var dataFreshnessAt: String? = null

    // TD-041 mitigation: the classification-listing read (no search query) has been observed to
    // transiently return totalItems=0 for a company/tab that provably has data, self-healing on a
    // later read with no app-visible cause. Remembering the last known non-zero total per
    // companyId+tab lets a later read recognise "this exact combination had data a moment ago" and
    // retry once before showing an empty state -- deliberately scoped to only the no-query listing
    // path (never the search path, where a genuine no-match zero is completely normal) and to only
    // fire when we have a concrete prior non-zero reading to compare against (never on a tab/company
    // we have no expectation for yet, e.g. a brand-new company or a genuinely-empty Prospects tab).
    private val lastNonZeroTotalByKey = mutableMapOf<String, Int>()

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
        var hasSeenCompany = false
        viewModelScope.launch {
            companySession.observeSelectedCompanyId().distinctUntilChanged().collect { companyId ->
                val isRealSwitch = hasSeenCompany
                hasSeenCompany = true
                // A leftover search query from the previous company must never scope the new
                // company's list: load() reads searchQuery straight off the current UiState
                // snapshot, so leaving it set here would silently filter company B's very first
                // load by whatever text was typed for company A -- not only during the debounce
                // window, but on every switch where a search was active at all. Cancelling
                // searchJob too prevents a pending debounced search from re-applying that same
                // stale text a moment later. Only cleared on a genuine switch, not the initial
                // subscription -- preserves Routes.connect(query)'s deep-link-with-a-query intent.
                loadJob?.cancel()
                if (isRealSwitch) searchJob?.cancel()
                _uiState.update {
                    it.copy(
                        companyId = companyId,
                        searchQuery = if (isRealSwitch) "" else it.searchQuery,
                        rows = emptyList(),
                        page = 1,
                        totalItems = 0,
                        canLoadMore = false,
                        error = null,
                    )
                }
                timber.log.Timber.tag("TD041").d(
                    "connect company-subscription companyId=$companyId isRealSwitch=$isRealSwitch " +
                        "at=${System.currentTimeMillis()}",
                )
                load(page = 1, append = false, refreshing = false, forceEnrichmentRefresh = true, source = "CompanySubscription(isRealSwitch=$isRealSwitch)")
            }
        }
    }

    fun onEvent(event: ConnectEvent) {
        when (event) {
            ConnectEvent.Load -> load(page = 1, append = false, refreshing = false, forceEnrichmentRefresh = true, source = "Load")
            ConnectEvent.Refresh -> load(page = 1, append = false, refreshing = true, forceEnrichmentRefresh = true, source = "Refresh")
            ConnectEvent.Retry -> load(page = 1, append = false, refreshing = false, forceEnrichmentRefresh = true, source = "Retry")
            ConnectEvent.LoadNextPage -> {
                val state = _uiState.value
                if (!state.canLoadMore || state.isBusy) return
                load(page = state.page + 1, append = true, refreshing = false, forceEnrichmentRefresh = false, source = "LoadNextPage")
            }
            is ConnectEvent.TabChanged -> {
                if (_uiState.value.selectedTab == event.tab) return
                _uiState.update { it.copy(selectedTab = event.tab, rows = emptyList(), page = 1, error = null) }
                load(page = 1, append = false, refreshing = false, forceEnrichmentRefresh = false, source = "TabChanged(${event.tab})")
            }
            is ConnectEvent.SearchChanged -> {
                _uiState.update { it.copy(searchQuery = event.query) }
                searchJob?.cancel()
                searchJob = viewModelScope.launch {
                    delay(MasterDataBrowserDefaults.SEARCH_DEBOUNCE_MS)
                    load(page = 1, append = false, refreshing = false, forceEnrichmentRefresh = false, source = "SearchChanged(debounced)")
                }
            }
            is ConnectEvent.RowTapped -> _effects.tryEmit(ConnectEffect.OpenPartyDetail(event.partyId))
            ConnectEvent.AddProspectTapped -> _effects.tryEmit(ConnectEffect.OpenProspectCreate)
            is ConnectEvent.ViewLedgerTapped -> {
                val ledgerId = event.ledgerId
                if (ledgerId.isNullOrBlank()) {
                    _effects.tryEmit(ConnectEffect.ShowMessage("No linked Ledger for this party."))
                } else {
                    _effects.tryEmit(ConnectEffect.OpenLedgerStatement(ledgerId))
                }
            }
            is ConnectEvent.ViewVouchersTapped -> {
                _effects.tryEmit(ConnectEffect.OpenVouchers(event.ledgerName))
            }
            is ConnectEvent.CallTapped -> {
                val phone = event.phoneE164
                if (phone.isNullOrBlank()) {
                    _effects.tryEmit(ConnectEffect.ShowMessage("No phone number available."))
                } else {
                    _effects.tryEmit(ConnectEffect.LaunchCall(phone))
                }
            }
            is ConnectEvent.WhatsAppTapped -> {
                val phone = event.phoneE164
                if (phone.isNullOrBlank()) {
                    _effects.tryEmit(ConnectEffect.ShowMessage("No phone number available."))
                } else {
                    _effects.tryEmit(ConnectEffect.LaunchWhatsApp(phone))
                }
            }
            ConnectEvent.FetchContactDetailsTapped -> fetchContactDetailsBulk()
        }
    }

    private fun fetchContactDetailsBulk() {
        val companyId = _uiState.value.companyId ?: return
        if (_uiState.value.isFetchingContactDetails) return
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingContactDetails = true) }
            when (val result = ledgerBulkContactDetailPort.fetchContactDetailsBulk()) {
                is AppResult.Success -> {
                    val seed = applyLedgerContactDetailsBulk(companyId, result.value.items)
                    _uiState.update { it.copy(isFetchingContactDetails = false) }
                    _effects.tryEmit(
                        ConnectEffect.ShowMessage(
                            "Fetched contact details for ${result.value.ledgerCount} ledgers: " +
                                "${seed.fieldsFilled} filled, ${seed.fieldsConfirmed} confirmed, " +
                                "${seed.fieldsConflicted} need review.",
                        ),
                    )
                    load(page = 1, append = false, refreshing = true, forceEnrichmentRefresh = true, source = "FetchContactDetails")
                }
                is AppResult.Failure -> {
                    val uiError = result.error.toMasterDataUiError()
                    _uiState.update { it.copy(isFetchingContactDetails = false) }
                    _effects.tryEmit(ConnectEffect.ShowMessage("Could not reach the Connector: ${uiError.displayMessage()}"))
                }
            }
        }
    }

    private fun load(page: Int, append: Boolean, refreshing: Boolean, forceEnrichmentRefresh: Boolean, source: String) {
        if (append && loadJob?.isActive == true) return
        val cancelledInFlight = loadJob?.isActive == true
        loadJob?.cancel()
        timber.log.Timber.tag("TD041").d(
            "connect load START source=$source page=$page append=$append cancelledInFlight=$cancelledInFlight " +
                "at=${System.currentTimeMillis()}",
        )
        loadJob = viewModelScope.launch {
            val snapshot = _uiState.value
            val companyId = snapshot.companyId ?: companySession.observeSelectedCompanyId().first()
            if (companyId.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        companyId = null,
                        rows = emptyList(),
                        error = MasterDataUiError.Message("Select a company before browsing Connect."),
                    )
                }
                return@launch
            }

            _uiState.update {
                when {
                    refreshing -> it.copy(isRefreshing = true, error = null, companyId = companyId)
                    append -> it.copy(isLoadingMore = true, error = null, companyId = companyId)
                    it.hasContent -> it.copy(isRefreshing = true, error = null, companyId = companyId)
                    else -> it.copy(isInitialLoading = true, error = null, companyId = companyId)
                }
            }

            if (forceEnrichmentRefresh || enrichmentCompanyId != companyId) {
                refreshEnrichmentCaches(companyId)
            }

            val query = snapshot.searchQuery.trim()
            val queryStartedAt = System.currentTimeMillis()
            var result: PartyPage = if (query.isNotEmpty()) {
                searchParties(companyId, query, snapshot.selectedTab.toClassification(), page, snapshot.pageSize)
            } else {
                listPartiesByClassification(companyId, snapshot.selectedTab.toClassification(), page, snapshot.pageSize)
            }

            if (query.isEmpty()) {
                val nonZeroKey = "$companyId|${snapshot.selectedTab}"
                if (result.totalItems == 0 && lastNonZeroTotalByKey.containsKey(nonZeroKey)) {
                    timber.log.Timber.tag("TD041").w(
                        "connect load SUSPICIOUS EMPTY source=$source company=$companyId " +
                            "tab=${snapshot.selectedTab} previouslySeen=${lastNonZeroTotalByKey[nonZeroKey]} -- retrying once",
                    )
                    delay(EMPTY_READ_RETRY_DELAY_MS)
                    result = listPartiesByClassification(companyId, snapshot.selectedTab.toClassification(), page, snapshot.pageSize)
                    timber.log.Timber.tag("TD041").w(
                        "connect load RETRY result source=$source company=$companyId tab=${snapshot.selectedTab} " +
                            "totalItems=${result.totalItems} items=${result.items.size}",
                    )
                }
                if (result.totalItems > 0) {
                    lastNonZeroTotalByKey[nonZeroKey] = result.totalItems
                }
            }

            timber.log.Timber.tag("TD041").d(
                "connect load END source=$source company=$companyId tab=${snapshot.selectedTab} at=$queryStartedAt " +
                    "totalItems=${result.totalItems} items=${result.items.size}",
            )

            val newRows = result.items.map { it.toRowUi(sourceLinksByPartyId, ledgersById, tagsByPartyId) }
            _uiState.update { state ->
                state.copy(
                    isInitialLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    rows = if (append) state.rows + newRows else newRows,
                    page = result.page,
                    totalItems = result.totalItems,
                    canLoadMore = result.page * result.pageSize < result.totalItems,
                    error = null,
                    dataFreshnessAt = dataFreshnessAt,
                )
            }
        }
    }

    private suspend fun refreshEnrichmentCaches(companyId: String) {
        sourceLinksByPartyId = getPartySourceLinksForCompany(companyId).associateBy { it.partyId }
        val ledgers = ledgerSnapshotPort.getCachedLedgers(companyId)
        ledgersById = ledgers.associateBy { it.id }
        tagsByPartyId = getPartyTagsForCompany(companyId)
        enrichmentCompanyId = companyId
        // Connect has no independent "synced at" of its own -- Parties are reconciled from
        // Ledgers, not synced directly -- so the most recent underlying Ledger sync timestamp is
        // the honest freshness signal, mirroring Ledger Browser's own dataFreshnessAt display.
        dataFreshnessAt = ledgers.maxOfOrNull { it.syncedAt }
    }
}

internal fun Party.toRowUi(
    sourceLinksByPartyId: Map<String, PartySourceLink>,
    ledgersById: Map<String, Ledger>,
    tagsByPartyId: Map<String, List<Tag>>,
): ConnectRowUi {
    val link = sourceLinksByPartyId[partyId]
    val ledger = link?.let { ledgersById[it.externalEntityId] }
    val balanceLabel = ledger?.closingBalance?.let { money -> "${money.amount} ${money.side.name}" }
    return ConnectRowUi(
        partyId = partyId,
        displayName = displayName,
        phoneDisplay = primaryPhone,
        phoneE164 = PhoneNumberNormalizer.normalizeIndianMobile(primaryPhone),
        tagNames = tagsByPartyId[partyId]?.map { it.name }.orEmpty(),
        balanceLabel = balanceLabel,
        linkedLedgerId = link?.externalEntityId,
        // Kept in sync with the ledger's current Tally name at every reconciliation — see
        // PartyRepositoryImpl.reconcileOne — so this remains a reasonable Voucher-search seed
        // even though today's Voucher schema has no stable ledger-id filter (documented
        // limitation, see BUDCOM-MVP-1-1-CONNECT-STATUS.md).
        linkedLedgerName = link?.let { displayName },
        linkedLedgerAlias = ledger?.alias,
    )
}
