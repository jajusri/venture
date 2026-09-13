package com.jajusri.venture.feature.catalogue.presentation

import android.net.Uri
import com.jajusri.venture.feature.catalogue.domain.excel.CatalogueExcelImportPreview
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueLifecycleState
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueProductSource
import java.io.File

data class CatalogueBranchUi(
    val branchId: String,
    val name: String,
)

data class CatalogueProductRowUi(
    val productId: String,
    val displayName: String,
    val lifecycleState: CatalogueLifecycleState,
    val sourceAvailable: Boolean,
    val source: CatalogueProductSource,
    /** The product's primary photo, already resolved via the existing
     * [com.jajusri.venture.feature.catalogue.domain.repository.CatalogueRepository.resolveAssetFile]
     * path-containment check -- `null` when the product has no photo, matching the existing
     * no-image state (this was never a rendered field on this row before; the underlying asset
     * storage/resolution was already correct, only the list row never carried it). */
    val primaryAssetFile: File? = null,
)

data class CatalogueUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val products: List<CatalogueProductRowUi> = emptyList(),
    /** Whether another page exists beyond what [products] currently holds (Catalogue perf package
     * -- bounded/paged retrieval, never the whole company table at once). */
    val canLoadMore: Boolean = false,
    /** True only while a [CatalogueEvent.LoadMoreProducts] page fetch is in flight -- distinct from
     * [isInitialLoading]/[isRefreshing], which apply to the first page only. */
    val isLoadingMore: Boolean = false,
    val showAddChoiceDialog: Boolean = false,
    val showAddManualDialog: Boolean = false,
    val addManualName: String = "",
    /** Catalogue-level Public/Private toggle (Brainstorm Outcome §5). Defaults `false` (Private)
     * — never presumed Public before the company's own setting has loaded. */
    val isPublic: Boolean = false,
    val shareMessage: String? = null,
    val error: String? = null,
    val showShareMenu: Boolean = false,
    val showCategoryShareDialog: Boolean = false,
    /** Distinct customer-facing categories among this company's currently-Published products
     * (architecture §11: sharing reads exclusively from the published snapshot) -- a Draft/Review
     * product's category, if it hasn't been published yet, is never offered here. */
    val availableCategories: List<String> = emptyList(),
    /** Branch selector (architecture §8/§17) -- scoping context only, never a product-list filter:
     * "One shared catalogue across branches" is locked, so [products] is never filtered by this. */
    val branches: List<CatalogueBranchUi> = emptyList(),
    /** `null` means the catalogue-wide default context, same meaning as
     * [com.jajusri.venture.feature.catalogue.domain.model.CatalogueOverrideScope.CatalogueWide]. */
    val selectedBranchId: String? = null,
    val showBranchMenu: Boolean = false,
    val showAddBranchDialog: Boolean = false,
    val addBranchName: String = "",
    val showMoreMenu: Boolean = false,
    /** Set once a picked file has been parsed and previewed (architecture §9: "Import preview:
     * mandatory step before commit... never a silent bulk-apply") -- `null` means no import is in
     * progress. Committing or dismissing clears it back to `null`. */
    val importPreview: CatalogueExcelImportPreview? = null,
    val importDuplicateHeaderWarnings: List<String> = emptyList(),
    val isImporting: Boolean = false,
) {
    val selectedBranchName: String get() = branches.firstOrNull { it.branchId == selectedBranchId }?.name ?: "All branches"
    val isEmpty: Boolean get() = !isInitialLoading && products.isEmpty()
}

sealed interface CatalogueEvent {
    data object Refresh : CatalogueEvent
    /** Fired on every RESUMED (see CatalogueRoute) -- unlike [Refresh], this is a cheap staleness
     * check first (see [CatalogueViewModel]'s own `checkFreshnessAndReloadIfNeeded` doc comment)
     * that only reconciles/reloads when something could actually have changed. */
    data object ResumeCheck : CatalogueEvent
    /** Requests the next page once the list is scrolled near its current end. A no-op if the last
     * page was already reached or a load is already in flight. */
    data object LoadMoreProducts : CatalogueEvent
    /** FAB tap — offers a choice between manual entry and linking from Tally stock, rather than
     * assuming one (architecture: Catalogue supports both a Manual and a Tally
     * [com.jajusri.venture.feature.catalogue.domain.model.CatalogueProductSource] from day one). */
    data object OpenAddChoiceDialog : CatalogueEvent
    data object DismissAddChoiceDialog : CatalogueEvent
    data object ChooseManualEntry : CatalogueEvent
    data object ChooseLinkFromStock : CatalogueEvent
    data object OpenAddManualDialog : CatalogueEvent
    data object DismissAddManualDialog : CatalogueEvent
    data class AddManualNameChanged(val name: String) : CatalogueEvent
    data object ConfirmAddManual : CatalogueEvent
    data class SetPublic(val isPublic: Boolean) : CatalogueEvent
    data object ShareFullCatalogue : CatalogueEvent
    data object SaveFullCatalogue : CatalogueEvent
    data class PdfSaveDestinationSelected(val uri: Uri?) : CatalogueEvent
    data object PdfSaveCancelled : CatalogueEvent
    data object DismissShareMessage : CatalogueEvent
    data object OpenShareMenu : CatalogueEvent
    data object DismissShareMenu : CatalogueEvent
    /** Loads [CatalogueUiState.availableCategories] from the currently-Published snapshot and
     * opens the category picker. */
    data object OpenCategoryShareDialog : CatalogueEvent
    data object DismissCategoryShareDialog : CatalogueEvent
    data class ShareCategory(val category: String) : CatalogueEvent

    data object OpenBranchMenu : CatalogueEvent
    data object DismissBranchMenu : CatalogueEvent
    data class SelectBranch(val branchId: String?) : CatalogueEvent
    data object OpenAddBranchDialog : CatalogueEvent
    data object DismissAddBranchDialog : CatalogueEvent
    data class AddBranchNameChanged(val name: String) : CatalogueEvent
    data object ConfirmAddBranch : CatalogueEvent

    data object OpenMoreMenu : CatalogueEvent
    data object DismissMoreMenu : CatalogueEvent
    /** Route responds by launching a file picker (architecture §9 -- CSV, this contract's chosen
     * file format, §9's own doc comment). */
    data object ImportFromExcel : CatalogueEvent
    /** The Route has already read the picked file's raw text (a Route/Composable-layer concern,
     * mirroring [com.jajusri.venture.feature.catalogue.presentation.CatalogueDetailEvent.PhotoSelected]'s
     * own "Route resolves the platform Uri, ViewModel never touches Context" split) -- parses and
     * previews it, never committing anything yet. */
    data class ExcelFileTextLoaded(val text: String) : CatalogueEvent
    data object ConfirmImport : CatalogueEvent
    data object DismissImportPreview : CatalogueEvent
    /** Route responds by launching a "create document" picker with the already-generated CSV
     * content and a suggested filename (see [CatalogueEffect.ExportCsvReady]). */
    data object ExportToExcel : CatalogueEvent
}

/** One-shot navigation effect — mirrors [CatalogueDetailEffect]'s own pattern. */
sealed interface CatalogueEffect {
    data object NavigateToStockItemPicker : CatalogueEffect
    data object RequestExcelImportPick : CatalogueEffect
    data class ExportCsvReady(val csvText: String, val suggestedFileName: String) : CatalogueEffect
    data class RequestPdfSave(val suggestedFileName: String) : CatalogueEffect
}
