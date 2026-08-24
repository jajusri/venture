package com.budcom.android.feature.catalogue.domain.repository

import android.net.Uri
import com.budcom.android.feature.catalogue.domain.model.Branch
import com.budcom.android.feature.catalogue.domain.model.CatalogueAsset
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleAction
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideAttribute
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideRow
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideScope
import com.budcom.android.feature.catalogue.domain.model.CatalogueProduct
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleState
import com.budcom.android.feature.catalogue.domain.model.CataloguePublishedSnapshot
import com.budcom.android.feature.catalogue.domain.model.CatalogueTimestamp
import com.budcom.android.feature.catalogue.storage.CatalogueAssetResult
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem
import java.io.File

/** Enrichment fields an owner/staff member can edit — deliberately excludes every Tally-owned
 * field (architecture §6's field-ownership table) and every lifecycle/identity field (those move
 * only via [CatalogueRepository.transitionLifecycle]/link operations). `null` in any property
 * means "leave unchanged," matching this codebase's own partial-update convention elsewhere. */
data class CatalogueEnrichmentUpdate(
    val displayNameOverride: String? = null,
    val clearDisplayNameOverride: Boolean = false,
    val sku: String? = null,
    /** Catalogue-owned Unit (TD-047) — applied only to a `Manual`-sourced product; a Tally-linked
     * product's Unit stays exclusively Tally-authoritative regardless of what is passed here (see
     * [CatalogueRepository.updateEnrichment]'s own doc comment). `null` means "leave unchanged",
     * matching every other field here. */
    val unit: String? = null,
    val description: String? = null,
    val specifications: String? = null,
    val customerFacingCategory: String? = null,
    val priceDisplayMode: com.budcom.android.feature.catalogue.domain.model.PriceDisplayMode? = null,
    val manualPriceAmount: String? = null,
    val manualPriceCurrencyCode: String? = null,
)

sealed interface CatalogueLifecycleResult {
    data class Success(val product: CatalogueProduct) : CatalogueLifecycleResult
    /** The transition was not valid from the product's current state, or required Owner access the
     * caller does not have — the state is left completely unchanged (architecture §7: "invalid
     * transitions must be rejected, never silently accepted"). */
    data object Rejected : CatalogueLifecycleResult
    data object ProductNotFound : CatalogueLifecycleResult
}

data class CatalogueReconciliationResult(
    val nowUnavailable: List<String>,
    val reappeared: List<String>,
)

interface CatalogueRepository {
    suspend fun createDraftFromStockItem(companyId: String, stockItemId: String, timestamp: CatalogueTimestamp): CatalogueProduct?
    suspend fun createManualDraft(companyId: String, displayName: String, timestamp: CatalogueTimestamp): CatalogueProduct

    /** Every locally-synced Stock Item for [companyId] that has no [CatalogueProduct] linked to it
     * yet — the source list for a "link from Tally stock items" picker UI (architecture §21
     * Milestone 1's own named "create Drafts from Stock Items" action). Local-cache-only, matching
     * [com.budcom.android.feature.masterdata.stockitem.domain.port.StockItemLookupPort]'s own
     * discipline — never a fresh network fetch of its own. */
    suspend fun listUnlinkedStockItems(companyId: String): List<StockItem>
    suspend fun findProduct(companyId: String, productId: String): CatalogueProduct?
    suspend fun listProducts(companyId: String): List<CatalogueProduct>
    suspend fun listProductsByState(companyId: String, state: CatalogueLifecycleState): List<CatalogueProduct>
    /** [update.unit] is written only when the target product's [CatalogueProduct.source] is
     * [com.budcom.android.feature.catalogue.domain.model.CatalogueProductSource.Manual] (TD-047) —
     * silently ignored for a Tally-linked product, exactly like every other Tally-owned field this
     * method already refuses to let an enrichment update touch. This is enforced at the repository
     * layer, not merely by the UI hiding the field, so a Manual edit can never reach (let alone
     * overwrite) Tally-authoritative Unit data even if a future caller misuses this API. */
    suspend fun updateEnrichment(
        companyId: String,
        productId: String,
        update: CatalogueEnrichmentUpdate,
        timestamp: CatalogueTimestamp,
    ): CatalogueProduct?

    suspend fun transitionLifecycle(
        companyId: String,
        productId: String,
        action: CatalogueLifecycleAction,
        isOwner: Boolean,
        timestamp: CatalogueTimestamp,
    ): CatalogueLifecycleResult

    /** Sweeps every Tally-linked product for this company against the current Stock Item cache
     * (architecture §6 property 3 / §14): flips [CatalogueProduct.sourceAvailable] to `false` for a
     * disappeared/inactive Stock Item, and back to `true` on reappearance — never deletes or
     * archives a product automatically. */
    suspend fun reconcileStockItemLinks(companyId: String, timestamp: CatalogueTimestamp): CatalogueReconciliationResult

    suspend fun upsertBranch(companyId: String, branchId: String, name: String, isActive: Boolean, timestamp: CatalogueTimestamp): Branch
    suspend fun listBranches(companyId: String): List<Branch>

    suspend fun setOverride(
        companyId: String,
        scope: CatalogueOverrideScope,
        attribute: CatalogueOverrideAttribute,
        value: String,
        timestamp: CatalogueTimestamp,
    )

    suspend fun clearOverride(companyId: String, scope: CatalogueOverrideScope, attribute: CatalogueOverrideAttribute)

    /** Resolves [attribute] for [productId] in [branchId]'s context via the LOCKED
     * Item → Branch → Stock-group → Catalogue-wide precedence (architecture §8). */
    suspend fun resolveOverride(
        companyId: String,
        productId: String,
        branchId: String?,
        attribute: CatalogueOverrideAttribute,
    ): CatalogueOverrideRow?

    /** Persists an owner-defined Excel custom column's values for one product (architecture §9:
     * "Custom columns... round-tripped opaquely; BUDCOM never interprets their content"). A `null`
     * value in [values] clears that column for this product (mirrors an Excel cell going blank on
     * re-import); a column simply absent from [values] is left untouched. */
    suspend fun upsertCustomFields(companyId: String, productId: String, values: Map<String, String?>, timestamp: CatalogueTimestamp)

    suspend fun listCustomFields(companyId: String, productId: String): Map<String, String?>

    /** Every distinct custom column name ever used anywhere in [companyId]'s Catalogue — the stable
     * header set an export uses so a product missing one column still shows every other product's
     * columns (a blank cell, not a dropped column). */
    suspend fun listAllCustomFieldColumnNames(companyId: String): List<String>

    suspend fun listPublishedForCategory(companyId: String, category: String): List<CataloguePublishedSnapshot>
    suspend fun listAllPublished(companyId: String): List<CataloguePublishedSnapshot>

    /** Catalogue-level Public/Private toggle (Brainstorm Outcome §5). Defaults to `false`
     * (Private) for a company that has never set it — Catalogue must never default to publicly
     * shareable. */
    suspend fun isPublic(companyId: String): Boolean
    suspend fun setPublic(companyId: String, isPublic: Boolean, timestamp: CatalogueTimestamp)

    /** Validates and stores [sourceUri] (camera capture or gallery pick — the caller doesn't
     * distinguish) as a new image for [productId] via
     * [com.budcom.android.feature.catalogue.storage.CatalogueAssetStore], then records it in
     * `catalogue_asset`. The very first asset ever added to a product becomes its primary
     * automatically (architecture §10: "one primary image per SKU"); later ones do not, until
     * explicitly set via [setPrimaryAsset]. */
    suspend fun addAsset(companyId: String, productId: String, sourceUri: Uri, timestamp: CatalogueTimestamp): CatalogueAssetResult

    suspend fun listAssets(companyId: String, productId: String): List<CatalogueAsset>

    /** Clears every other asset's primary flag for this product first (architecture §10's two-phase
     * discipline — at most one row is ever primary, no window where two are). */
    suspend fun setPrimaryAsset(companyId: String, productId: String, assetId: String, timestamp: CatalogueTimestamp)

    /** Soft-removes the DB row before deleting the file (architecture §10) — a dangling DB
     * reference to a deleted file is never possible, only the reverse (briefly) window. If the
     * deleted asset was primary and others remain, the next one is promoted automatically so a
     * product with any images always has exactly one primary. */
    suspend fun deleteAsset(companyId: String, productId: String, assetId: String)

    fun resolveAssetFile(companyId: String, productId: String, filePath: String): File?
}
