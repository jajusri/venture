package com.budcom.android.feature.catalogue.presentation

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.feature.businessprofile.domain.repository.BusinessProfileRepository
import com.budcom.android.feature.catalogue.domain.excel.CatalogueCsvFormat
import com.budcom.android.feature.catalogue.domain.excel.CatalogueExcelExportUseCase
import com.budcom.android.feature.catalogue.domain.excel.CatalogueExcelImportUseCase
import com.budcom.android.feature.catalogue.domain.excel.CatalogueExcelRow
import com.budcom.android.feature.catalogue.domain.excel.CatalogueExcelValidator
import com.budcom.android.feature.catalogue.domain.model.CatalogueProduct
import com.budcom.android.feature.catalogue.domain.port.CatalogueBranchSelectionStore
import com.budcom.android.feature.catalogue.domain.port.CatalogueClock
import com.budcom.android.feature.catalogue.domain.repository.CatalogueRepository
import com.budcom.android.feature.catalogue.sharing.CatalogueShareCoordinator
import com.budcom.android.feature.catalogue.sharing.CatalogueShareResult
import com.budcom.android.feature.catalogue.sharing.CatalogueShareScope
import com.budcom.android.feature.company.domain.port.CompanySessionPort
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
            // A resume-triggered CatalogueEvent.Refresh (see CatalogueRoute) can race the
            // company-session subscription on a cold start, firing before companyId is known.
            // Only the real, company-driven load path (refreshing = false, from the init-block
            // subscription) is allowed to declare "no company" and clear the loading indicator --
            // a premature Refresh in this state must be a no-op, not a flash of the empty state
            // this screen isn't actually in yet.
            if (!refreshing) _uiState.update { it.copy(isInitialLoading = false, isRefreshing = false, products = emptyList()) }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                if (refreshing) it.copy(isRefreshing = true) else it.copy(isInitialLoading = true)
            }
            // Opportunistic reconciliation (architecture §6/§14/§21): cheap, local-only sweep
            // against the already-synced Stock Item cache -- never a network call of its own.
            runCatching { repository.reconcileStockItemLinks(id, clock.now()) }
            val products = repository.listProducts(id)
            val isPublic = repository.isPublic(id)
            val branches = repository.listBranches(id)
            val selectedBranchId = branchSelectionStore.observeSelectedBranchId(id).first()
                // A previously-selected branch that no longer exists for this company (e.g. this
                // is a fresh install/company-switch with a stale stored id from before) silently
                // falls back to the catalogue-wide default rather than showing a dangling selection.
                ?.takeIf { stored -> branches.any { it.branchId == stored } }
            // Photo display fix: the list row never carried a resolved primary-asset file before,
            // even though the asset was already stored and already correctly resolvable (the exact
            // same read the Detail screen's own PhotosSection already uses) -- this reuses
            // listAssets/resolveAssetFile unchanged, per product, both already company-scoped.
            val rows = products.map { p -> p.toRowUi(primaryAssetFile(id, p.productId)) }
            _uiState.update {
                it.copy(
                    isInitialLoading = false,
                    isRefreshing = false,
                    products = rows,
                    isPublic = isPublic,
                    branches = branches.map { CatalogueBranchUi(it.branchId, it.name) },
                    selectedBranchId = selectedBranchId,
                    error = null,
                )
            }
        }
    }

    /** Resolves a product's primary photo file, if any -- reuses [CatalogueRepository.listAssets]/
     * [CatalogueRepository.resolveAssetFile] exactly as-is, both already `companyId`-scoped
     * (composite key + path-containment check in the underlying asset store), so this can never
     * resolve a file belonging to another company. Returns `null` (never throws) for a product
     * with no photo, or if the primary asset's own file has since gone missing -- a stale/invalid
     * reference must never crash the list, only fall back to the existing no-image state. */
    private suspend fun primaryAssetFile(companyId: String, productId: String): java.io.File? {
        val assets = repository.listAssets(companyId, productId)
        val primary = assets.firstOrNull { it.isPrimary } ?: assets.firstOrNull() ?: return null
        return repository.resolveAssetFile(companyId, productId, primary.filePath)
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

private fun CatalogueProduct.toRowUi(primaryAssetFile: java.io.File?) = CatalogueProductRowUi(
    productId = productId,
    displayName = displayName,
    lifecycleState = lifecycleState,
    sourceAvailable = sourceAvailable,
    source = source,
    primaryAssetFile = primaryAssetFile,
)
