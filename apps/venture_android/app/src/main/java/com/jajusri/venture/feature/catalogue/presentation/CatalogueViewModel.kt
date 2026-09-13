package com.jajusri.venture.feature.catalogue.presentation

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jajusri.venture.feature.businessprofile.domain.repository.BusinessProfileRepository
import com.jajusri.venture.feature.catalogue.domain.excel.CatalogueCsvFormat
import com.jajusri.venture.feature.catalogue.domain.excel.CatalogueExcelExportUseCase
import com.jajusri.venture.feature.catalogue.domain.excel.CatalogueExcelImportUseCase
import com.jajusri.venture.feature.catalogue.domain.excel.CatalogueExcelRow
import com.jajusri.venture.feature.catalogue.domain.excel.CatalogueExcelValidator
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueProduct
import com.jajusri.venture.feature.catalogue.domain.port.CatalogueBranchSelectionStore
import com.jajusri.venture.feature.catalogue.domain.port.CatalogueClock
import com.jajusri.venture.feature.catalogue.domain.repository.CatalogueChangeSignal
import com.jajusri.venture.feature.catalogue.domain.repository.CatalogueProductPageCursor
import com.jajusri.venture.feature.catalogue.domain.repository.CatalogueRepository
import com.jajusri.venture.feature.catalogue.sharing.CatalogueShareCoordinator
import com.jajusri.venture.feature.catalogue.sharing.CatalogueShareResult
import com.jajusri.venture.feature.catalogue.sharing.CatalogueShareScope
import com.jajusri.venture.feature.catalogue.sharing.PreparedCatalogueShare
import com.jajusri.venture.feature.company.domain.port.CompanySessionPort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CatalogueViewModel @Inject constructor(
    private val repository: CatalogueRepository,
    private val companySession: CompanySessionPort,
    private val clock: CatalogueClock,
    private val shareCoordinator: CatalogueShareCoordinator,
    private val businessProfileRepository: BusinessProfileRepository,
    private val branchSelectionStore: CatalogueBranchSelectionStore,
    private val excelImportUseCase: CatalogueExcelImportUseCase,
    private val excelExportUseCase: CatalogueExcelExportUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogueUiState())
    val uiState: StateFlow<CatalogueUiState> = _uiState.asStateFlow()

    private val _shareIntent = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val shareIntent = _shareIntent.asSharedFlow()

    private val _effects = MutableSharedFlow<CatalogueEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    private var companyId: String? = null

    /** The already-parsed rows behind the current [CatalogueUiState.importPreview] -- kept out of
     * UI state since the Screen never needs to render individual row data, only the preview's
     * summary counts and flagged reasons. Cleared on commit or dismiss. */
    private var pendingImportRows: List<CatalogueExcelRow> = emptyList()
    private var pendingPdfSave: PreparedCatalogueShare? = null

    /** The next page's cursor for [CatalogueRepository.listProductsPage], `null` once the last page
     * has been reached. Reset by every full [load]/[checkFreshnessAndReloadIfNeeded] reload. */
    private var nextCursor: CatalogueProductPageCursor? = null

    /** The change signal captured at the end of the most recent successful reload -- compared
     * against a fresh [CatalogueRepository.currentChangeSignal] on resume to decide whether a
     * reconciliation sweep + reload is actually needed (see [checkFreshnessAndReloadIfNeeded]). */
    private var lastChangeSignal: CatalogueChangeSignal? = null

    init {
        // Mirrors ConnectViewModel's own company-switch discipline (TD-037 class): every company
        // change (not just the initial subscription) reloads this screen's list from scratch.
        viewModelScope.launch {
            companySession.observeSelectedCompanyId().distinctUntilChanged().collect { id ->
                companyId = id
                load(refreshing = false)
            }
        }
    }

    fun onEvent(event: CatalogueEvent) {
        when (event) {
            CatalogueEvent.Refresh -> load(refreshing = true)
            CatalogueEvent.ResumeCheck -> checkFreshnessAndReloadIfNeeded()
            CatalogueEvent.LoadMoreProducts -> loadMore()
            CatalogueEvent.OpenAddChoiceDialog -> _uiState.update { it.copy(showAddChoiceDialog = true) }
            CatalogueEvent.DismissAddChoiceDialog -> _uiState.update { it.copy(showAddChoiceDialog = false) }
            CatalogueEvent.ChooseManualEntry ->
                _uiState.update { it.copy(showAddChoiceDialog = false, showAddManualDialog = true, addManualName = "") }
            CatalogueEvent.ChooseLinkFromStock -> {
                _uiState.update { it.copy(showAddChoiceDialog = false) }
                viewModelScope.launch { _effects.emit(CatalogueEffect.NavigateToStockItemPicker) }
            }
            CatalogueEvent.OpenAddManualDialog -> _uiState.update { it.copy(showAddManualDialog = true, addManualName = "") }
            CatalogueEvent.DismissAddManualDialog -> _uiState.update { it.copy(showAddManualDialog = false) }
            is CatalogueEvent.AddManualNameChanged -> _uiState.update { it.copy(addManualName = event.name) }
            CatalogueEvent.ConfirmAddManual -> confirmAddManual()
            is CatalogueEvent.SetPublic -> setPublic(event.isPublic)
            CatalogueEvent.ShareFullCatalogue -> {
                _uiState.update { it.copy(showShareMenu = false) }
                shareFullCatalogue()
            }
            CatalogueEvent.SaveFullCatalogue -> {
                _uiState.update { it.copy(showShareMenu = false) }
                saveFullCatalogue()
            }
            is CatalogueEvent.PdfSaveDestinationSelected -> savePdf(event.uri)
            CatalogueEvent.PdfSaveCancelled -> releasePendingPdf()
            CatalogueEvent.DismissShareMessage -> _uiState.update { it.copy(shareMessage = null) }
            CatalogueEvent.OpenShareMenu -> _uiState.update { it.copy(showShareMenu = true) }
            CatalogueEvent.DismissShareMenu -> _uiState.update { it.copy(showShareMenu = false) }
            CatalogueEvent.OpenCategoryShareDialog -> openCategoryShareDialog()
            CatalogueEvent.DismissCategoryShareDialog -> _uiState.update { it.copy(showCategoryShareDialog = false) }
            is CatalogueEvent.ShareCategory -> {
                _uiState.update { it.copy(showCategoryShareDialog = false) }
                shareCategory(event.category)
            }
            CatalogueEvent.OpenBranchMenu -> _uiState.update { it.copy(showBranchMenu = true) }
            CatalogueEvent.DismissBranchMenu -> _uiState.update { it.copy(showBranchMenu = false) }
            is CatalogueEvent.SelectBranch -> selectBranch(event.branchId)
            CatalogueEvent.OpenAddBranchDialog -> _uiState.update { it.copy(showBranchMenu = false, showAddBranchDialog = true, addBranchName = "") }
            CatalogueEvent.DismissAddBranchDialog -> _uiState.update { it.copy(showAddBranchDialog = false) }
            is CatalogueEvent.AddBranchNameChanged -> _uiState.update { it.copy(addBranchName = event.name) }
            CatalogueEvent.ConfirmAddBranch -> confirmAddBranch()
            CatalogueEvent.OpenMoreMenu -> _uiState.update { it.copy(showMoreMenu = true) }
            CatalogueEvent.DismissMoreMenu -> _uiState.update { it.copy(showMoreMenu = false) }
            CatalogueEvent.ImportFromExcel -> {
                _uiState.update { it.copy(showMoreMenu = false) }
                viewModelScope.launch { _effects.emit(CatalogueEffect.RequestExcelImportPick) }
            }
            is CatalogueEvent.ExcelFileTextLoaded -> loadImportPreview(event.text)
            CatalogueEvent.ConfirmImport -> confirmImport()
            CatalogueEvent.DismissImportPreview -> {
                pendingImportRows = emptyList()
                _uiState.update { it.copy(importPreview = null, importDuplicateHeaderWarnings = emptyList()) }
            }
            CatalogueEvent.ExportToExcel -> exportToExcel()
        }
    }

    private fun load(refreshing: Boolean) {
        val id = companyId ?: run {
            // A resume-triggered CatalogueEvent.ResumeCheck (see CatalogueRoute) can race the
            // company-session subscription on a cold start, firing before companyId is known.
            // Only the real, company-driven load path (refreshing = false, from the init-block
            // subscription) is allowed to declare "no company" and clear the loading indicator --
            // a premature check in this state must be a no-op, not a flash of the empty state this
            // screen isn't actually in yet.
            if (!refreshing) _uiState.update { it.copy(isInitialLoading = false, isRefreshing = false, products = emptyList()) }
            return
        }
        viewModelScope.launch {
            _uiState.update { if (refreshing) it.copy(isRefreshing = true) else it.copy(isInitialLoading = true) }
            reloadFirstPage(id)
            _uiState.update { it.copy(isInitialLoading = false, isRefreshing = false) }
        }
    }

    /**
     * Resume-triggered replacement for an unconditional [load] (Catalogue perf package). Found live
     * on a real device (2026-08-24): returning from Detail after a Publish/Archive/enrichment edit
     * left this list showing stale lifecycle-state chips -- the original fix was an unconditional
     * reconciliation+reload on every RESUMED. That preserved correctness but repeated the full sweep
     * even when nothing had changed (e.g. the user only glanced at Detail, or came back from the
     * Stock Item Picker/Transaction Composer without linking or editing anything).
     *
     * [CatalogueRepository.currentChangeSignal] is a cheap, local-only check (an in-memory counter
     * for this repository's own writes, plus one small aggregate query against the Stock Item
     * cache) -- comparing it against [lastChangeSignal] (captured at the end of the last successful
     * reload) tells us whether *anything* that could make the list stale actually happened. If not,
     * this is a true no-op: no reconciliation, no reload, no state update at all, so scroll position
     * and everything else already on screen is left completely untouched. If something did change,
     * this reloads exactly like [load] would, just without flashing the full-screen loading state
     * for what is usually a fast, already-cached read.
     */
    private fun checkFreshnessAndReloadIfNeeded() {
        val id = companyId ?: return
        viewModelScope.launch {
            val signal = repository.currentChangeSignal(id)
            if (signal == lastChangeSignal) return@launch
            reloadFirstPage(id)
        }
    }

    /** Loads the first page of [id]'s products (after an opportunistic reconciliation sweep) and
     * publishes it, branches, and the Public toggle to [CatalogueUiState] -- the shared core behind
     * both an explicit [load] and a resume-triggered [checkFreshnessAndReloadIfNeeded]. Deliberately
     * does not touch [CatalogueUiState.isInitialLoading]/[CatalogueUiState.isRefreshing]; callers
     * that want a loading indicator toggle it themselves around this call. */
    private suspend fun reloadFirstPage(id: String) {
        // Opportunistic reconciliation (architecture §6/§14/§21): cheap, local-only sweep against
        // the already-synced Stock Item cache -- never a network call of its own. Now O(linked
        // products) with one batched Stock Item lookup, not one DB round trip per linked product.
        runCatching { repository.reconcileStockItemLinks(id, clock.now()) }
        val page = repository.listProductsPage(id, cursor = null, pageSize = CATALOGUE_PAGE_SIZE)
        nextCursor = page.nextCursor
        // Captured after reconciliation so a real change reconciliation just applied is reflected
        // in what "no change since last load" means for the *next* freshness check.
        lastChangeSignal = repository.currentChangeSignal(id)
        val isPublic = repository.isPublic(id)
        val branches = repository.listBranches(id)
        val selectedBranchId = branchSelectionStore.observeSelectedBranchId(id).first()
            // A previously-selected branch that no longer exists for this company (e.g. this
            // is a fresh install/company-switch with a stale stored id from before) silently
            // falls back to the catalogue-wide default rather than showing a dangling selection.
            ?.takeIf { stored -> branches.any { it.branchId == stored } }
        val rows = toRowUis(id, page.products)
        _uiState.update {
            it.copy(
                products = rows,
                isPublic = isPublic,
                branches = branches.map { b -> CatalogueBranchUi(b.branchId, b.name) },
                selectedBranchId = selectedBranchId,
                canLoadMore = nextCursor != null,
                error = null,
            )
        }
    }

    /** Appends the next page after [nextCursor], if any -- a no-op if the last page was already
     * reached or another load-more is already in flight. */
    private fun loadMore() {
        val id = companyId ?: return
        val cursor = nextCursor ?: return
        if (_uiState.value.isLoadingMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val page = repository.listProductsPage(id, cursor, CATALOGUE_PAGE_SIZE)
            nextCursor = page.nextCursor
            val newRows = toRowUis(id, page.products)
            _uiState.update { it.copy(products = it.products + newRows, isLoadingMore = false, canLoadMore = page.nextCursor != null) }
        }
    }

    /** Resolves every row's primary photo file in one batched call (Catalogue perf package -- was
     * a [CatalogueRepository.listAssets]/[CatalogueRepository.resolveAssetFile] round trip per
     * product before). `null` for a product with no photo, or if the primary asset's own file has
     * since gone missing -- a stale/invalid reference must never crash the list, only fall back to
     * the existing no-image state. */
    private suspend fun toRowUis(companyId: String, products: List<CatalogueProduct>): List<CatalogueProductRowUi> {
        if (products.isEmpty()) return emptyList()
        val primaryFiles = repository.primaryAssetFiles(companyId, products.map { it.productId })
        return products.map { p -> p.toRowUi(primaryFiles[p.productId]) }
    }

    private fun confirmAddManual() {
        val id = companyId ?: return
        val name = _uiState.value.addManualName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            repository.createManualDraft(id, name, clock.now())
            _uiState.update { it.copy(showAddManualDialog = false, addManualName = "") }
            load(refreshing = false)
        }
    }

    private fun selectBranch(branchId: String?) {
        val id = companyId ?: return
        viewModelScope.launch {
            branchSelectionStore.setSelectedBranchId(id, branchId)
            _uiState.update { it.copy(selectedBranchId = branchId, showBranchMenu = false) }
        }
    }

    private fun confirmAddBranch() {
        val id = companyId ?: return
        val name = _uiState.value.addBranchName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val branch = repository.upsertBranch(id, java.util.UUID.randomUUID().toString(), name, isActive = true, clock.now())
            branchSelectionStore.setSelectedBranchId(id, branch.branchId)
            val branches = repository.listBranches(id)
            _uiState.update {
                it.copy(
                    branches = branches.map { b -> CatalogueBranchUi(b.branchId, b.name) },
                    selectedBranchId = branch.branchId,
                    showAddBranchDialog = false,
                    addBranchName = "",
                )
            }
        }
    }

    /**
     * Parses [text] (CatalogueCsvFormat, architecture §9's chosen file format) and builds the
     * mandatory import preview -- never commits anything. Identity resolution for Update-vs-Create
     * matching mirrors [CatalogueExcelValidator]'s own documented rule (Stock Item Reference, then
     * SKU -- never Product Name alone, per architecture §9's "stable identifier" requirement).
     */
    private fun loadImportPreview(text: String) {
        val id = companyId ?: return
        viewModelScope.launch {
            val parsed = CatalogueCsvFormat.parse(text)
            val existingProducts = repository.listProducts(id)
            val preview = CatalogueExcelValidator.preview(parsed.rows) { identifier ->
                existingProducts.firstOrNull { it.linkedStockItemId == identifier || it.sku == identifier }?.productId
            }
            pendingImportRows = parsed.rows
            _uiState.update {
                it.copy(importPreview = preview, importDuplicateHeaderWarnings = parsed.duplicateHeaderWarnings)
            }
        }
    }

    private fun confirmImport() {
        val id = companyId ?: return
        val preview = _uiState.value.importPreview ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            val result = excelImportUseCase.commit(id, pendingImportRows, preview)
            pendingImportRows = emptyList()
            _uiState.update {
                it.copy(
                    isImporting = false,
                    importPreview = null,
                    importDuplicateHeaderWarnings = emptyList(),
                    shareMessage = "Import complete: ${result.created} created, ${result.updated} updated, ${result.skipped} skipped",
                )
            }
            load(refreshing = true)
        }
    }

    private fun exportToExcel() {
        val id = companyId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(showMoreMenu = false) }
            val csv = excelExportUseCase.export(id).toCsv()
            _effects.emit(CatalogueEffect.ExportCsvReady(csv, suggestedFileName = "catalogue-export.csv"))
        }
    }

    private fun setPublic(isPublic: Boolean) {
        val id = companyId ?: return
        viewModelScope.launch {
            repository.setPublic(id, isPublic, clock.now())
            _uiState.update { it.copy(isPublic = isPublic) }
        }
    }

    private fun shareFullCatalogue() {
        val id = companyId ?: return
        viewModelScope.launch { performShare(id, CatalogueShareScope.FullCatalogue) }
    }

    private fun saveFullCatalogue() {
        val id = companyId ?: return
        viewModelScope.launch {
            val businessName = businessProfileRepository.getProfile(id)?.tradingName
            when (val prepared = shareCoordinator.prepareShare(id, CatalogueShareScope.FullCatalogue, businessName)) {
                is CatalogueShareResult.Failure -> _uiState.update { it.copy(shareMessage = prepared.message) }
                is CatalogueShareResult.Success -> {
                    pendingPdfSave?.let(shareCoordinator::releaseShare)
                    pendingPdfSave = prepared.value
                    _effects.emit(CatalogueEffect.RequestPdfSave(prepared.value.suggestedFilename))
                }
            }
        }
    }

    private fun savePdf(destination: android.net.Uri?) {
        val prepared = pendingPdfSave ?: return
        pendingPdfSave = null
        if (destination == null) {
            shareCoordinator.releaseShare(prepared)
            return
        }
        viewModelScope.launch {
            when (val result = shareCoordinator.savePdf(prepared, destination)) {
                is CatalogueShareResult.Failure -> _uiState.update { it.copy(shareMessage = result.message) }
                is CatalogueShareResult.Success -> _uiState.update { it.copy(shareMessage = "Catalogue PDF saved.") }
            }
        }
    }

    private fun releasePendingPdf() {
        pendingPdfSave?.let(shareCoordinator::releaseShare)
        pendingPdfSave = null
    }

    private fun openCategoryShareDialog() {
        val id = companyId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(showShareMenu = false) }
            val categories = repository.listAllPublished(id).mapNotNull { it.customerFacingCategory }.distinct().sorted()
            _uiState.update { it.copy(showCategoryShareDialog = true, availableCategories = categories) }
        }
    }

    private fun shareCategory(category: String) {
        val id = companyId ?: return
        viewModelScope.launch { performShare(id, CatalogueShareScope.Category(category)) }
    }

    private suspend fun performShare(companyId: String, scope: CatalogueShareScope) {
        val businessName = businessProfileRepository.getProfile(companyId)?.tradingName
        when (val prepared = shareCoordinator.prepareShare(companyId, scope, businessName)) {
            is CatalogueShareResult.Failure -> _uiState.update { it.copy(shareMessage = prepared.message) }
            is CatalogueShareResult.Success -> when (val intentResult = shareCoordinator.createShareIntent(prepared.value)) {
                is CatalogueShareResult.Failure -> _uiState.update { it.copy(shareMessage = intentResult.message) }
                is CatalogueShareResult.Success -> _shareIntent.emit(intentResult.value)
            }
        }
    }
}

/** Page size for [CatalogueViewModel]'s [CatalogueRepository.listProductsPage] reads (Catalogue
 * perf package) -- small enough to keep a single page's DB/asset work cheap, large enough that a
 * typical seller catalogue rarely needs a second page at all. `internal` so tests can assert
 * against the exact same value production uses. */
internal const val CATALOGUE_PAGE_SIZE = 50

private fun CatalogueProduct.toRowUi(primaryAssetFile: java.io.File?) = CatalogueProductRowUi(
    productId = productId,
    displayName = displayName,
    lifecycleState = lifecycleState,
    sourceAvailable = sourceAvailable,
    source = source,
    primaryAssetFile = primaryAssetFile,
)
