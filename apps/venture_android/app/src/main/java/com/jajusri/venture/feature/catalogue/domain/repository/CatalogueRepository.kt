package com.jajusri.venture.feature.catalogue.domain.repository

import android.net.Uri
import com.jajusri.venture.feature.catalogue.domain.model.Branch
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueAsset
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueLifecycleAction
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueOverrideAttribute
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueOverrideRow
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueOverrideScope
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueProduct
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueLifecycleState
import com.jajusri.venture.feature.catalogue.domain.model.CataloguePublishedSnapshot
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueTimestamp
import com.jajusri.venture.feature.catalogue.storage.CatalogueAssetResult
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItem
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
    val priceDisplayMode: com.jajusri.venture.feature.catalogue.domain.model.PriceDisplayMode? = null,
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

/** Opaque cursor into [CatalogueRepository.listProductsPage]'s deterministic order (newest-updated
 * first, tiebroken by id) -- always sourced from a previous page's own [CatalogueProductPage.nextCursor],
 * never constructed by a caller from scratch. */
data class CatalogueProductPageCursor(val updatedAt: Long, val productId: String)

data class CatalogueProductPage(
    val products: List<CatalogueProduct>,
    /** `null` means this was the last page. */
    val nextCursor: CatalogueProductPageCursor?,
)

/** Cheap, poll-friendly signal for "might this company's Catalogue list or its live-resolved Stock
 * Item fields have changed since the last time I loaded/reconciled it." Comparing two calls'
 * results for equality is enough to decide whether a resume-triggered refresh needs to do any work
 * at all -- see [CatalogueRepository.currentChangeSignal]. */
data class CatalogueChangeSignal(val localRevision: Int, val stockItemFingerprint: String)

interface CatalogueRepository {
    suspend fun createDraftFromStockItem(companyId: String, stockItemId: String, timestamp: CatalogueTimestamp): CatalogueProduct?
    suspend fun createManualDraft(companyId: String, displayName: String, timestamp: CatalogueTimestamp): CatalogueProduct

    /**
     * Bulk equivalent of calling [createDraftFromStockItem] once per id (TD-051 perf fix) — writes
     * in chunks of a small internal batch size via a single `upsertAll` per chunk per table instead
     * of three separate DB round-trips per item, cutting a 1,200-item Link-all from ~45 minutes to
     * seconds. Preserves [createDraftFromStockItem]'s own idempotency (an id already linked is
     * silently skipped, never duplicated) and its "stock item no longer cached locally" skip. Each
     * chunk commits independently — if a later chunk fails, every earlier chunk's Drafts remain
     * durably linked (never rolled back), and the exception propagates to the caller after
     * whatever [onProgress] calls already happened, so the caller's last-reported count is always
     * exactly what was actually persisted, never an overcount.
     *
     * [onProgress] is invoked with `(linked so far, total requested)` once before the first chunk
     * and once after each chunk commits — a batch interval, never once per single row.
     *
     * Returns the total number of Drafts actually created (excludes idempotent skips).
     */
    suspend fun createDraftsFromStockItems(
        companyId: String,
        stockItemIds: List<String>,
        timestamp: CatalogueTimestamp,
        onProgress: suspend (linked: Int, total: Int) -> Unit = { _, _ -> },
    ): Int

    /** Ensures the locally-cached Stock Item snapshot this repository's [listUnlinkedStockItems]/
     * [createDraftFromStockItem]/[createDraftsFromStockItems] all read from is warm before a
     * "Link from stock"/"Link all" flow depends on it (TD-050) — see
     * [com.jajusri.venture.feature.masterdata.stockitem.domain.port.StockItemLookupPort.warmStockItemCache]'s
     * own doc comment for why this is needed and what it does on failure. */
    suspend fun warmStockItemCache(companyId: String)

    /** Every locally-synced Stock Item for [companyId] that has no [CatalogueProduct] linked to it
     * yet — the source list for a "link from Tally stock items" picker UI (architecture §21
     * Milestone 1's own named "create Drafts from Stock Items" action). Local-cache-only, matching
     * [com.jajusri.venture.feature.masterdata.stockitem.domain.port.StockItemLookupPort]'s own
     * discipline — never a fresh network fetch of its own. */
    suspend fun listUnlinkedStockItems(companyId: String): List<StockItem>
    suspend fun findProduct(companyId: String, productId: String): CatalogueProduct?

    /** Every product for [companyId], unbounded -- correct for a genuine "need the whole list"
     * operation (Excel-import identity resolution, Excel export), never for a screen-open cost.
     * The normal Catalogue list screen uses [listProductsPage] instead. */
    suspend fun listProducts(companyId: String): List<CatalogueProduct>
    suspend fun listProductsByState(companyId: String, state: CatalogueLifecycleState): List<CatalogueProduct>

    /**
     * Bounded, deterministically-ordered page of [companyId]'s products (newest-updated first) --
     * never materializes the whole company's product table regardless of catalogue size. Pass the
     * previous call's [CatalogueProductPage.nextCursor] as [cursor] to fetch the following page, or
     * `null` for the first page. Every Tally-linked row's live-resolved fields (name/unit/HSN/GST)
     * are resolved with a single batched Stock Item lookup for the whole page, never one query per
     * row (architecture: this is what replaces [listProducts] on the Catalogue screen's own
     * list-open/resume path).
     *
     * The default implementation here delegates to [listProducts] and pages the result in memory --
     * correct but not bounded at the DB layer; [com.jajusri.venture.feature.catalogue.data.repository.CatalogueRepositoryImpl]
     * overrides this with an actual bounded/indexed Room query. The default exists purely so an
     * older test fake that hasn't been taught about paging still compiles and behaves correctly.
     */
    suspend fun listProductsPage(
        companyId: String,
        cursor: CatalogueProductPageCursor? = null,
        pageSize: Int = 50,
    ): CatalogueProductPage {
        val ordered = listProducts(companyId).sortedWith(
            compareByDescending<CatalogueProduct> { it.updatedAt.epochMillis }.thenBy { it.productId },
        )
        val afterCursor = if (cursor == null) {
            ordered
        } else {
            ordered.filter { p ->
                p.updatedAt.epochMillis < cursor.updatedAt ||
                    (p.updatedAt.epochMillis == cursor.updatedAt && p.productId > cursor.productId)
            }
        }
        val page = afterCursor.take(pageSize)
        val nextCursor = if (afterCursor.size > pageSize) {
            page.last().let { CatalogueProductPageCursor(it.updatedAt.epochMillis, it.productId) }
        } else {
            null
        }
        return CatalogueProductPage(page, nextCursor)
    }

    /** One primary-photo file per id in [productIds], resolved in a single batched call rather than
     * one [listAssets]/[resolveAssetFile] round trip per product (architecture: this is what
     * replaces the Catalogue list screen's own former per-row thumbnail N+1). A product with no
     * asset, or whose primary asset's file has since gone missing, is simply absent/`null` in the
     * result -- never throws, matching every other asset-resolution path in this codebase.
     *
     * Default implementation delegates to [listAssets]/[resolveAssetFile] per id (same fallback
     * rationale as [listProductsPage]'s own default); [com.jajusri.venture.feature.catalogue.data.repository.CatalogueRepositoryImpl]
     * overrides this with one batched Room query for the whole page.
     */
    suspend fun primaryAssetFiles(companyId: String, productIds: List<String>): Map<String, File?> =
        productIds.associateWith { productId ->
            val assets = listAssets(companyId, productId)
            val primary = assets.firstOrNull { it.isPrimary } ?: assets.firstOrNull()
            primary?.let { resolveAssetFile(companyId, productId, it.filePath) }
        }

    /**
     * Cheap, poll-friendly signal for "might anything relevant to the Catalogue list have changed
     * since the last time I checked" -- see [CatalogueChangeSignal]. Comparing two calls' results
     * for equality lets a resume-triggered check skip a full reconciliation+reload when nothing
     * changed, instead of always doing one unconditionally.
     *
     * Default implementation returns a value that is never equal across two calls (built from
     * [System.nanoTime]) -- an implementation that hasn't overridden this (e.g. an older test fake)
     * simply never unlocks the "skip, nothing changed" fast path, which is exactly this package's
     * prior always-refresh behavior, so existing test expectations on the default keep holding.
     * [com.jajusri.venture.feature.catalogue.data.repository.CatalogueRepositoryImpl] overrides this
     * with the real cheap signal.
     */
    suspend fun currentChangeSignal(companyId: String): CatalogueChangeSignal =
        CatalogueChangeSignal(localRevision = 0, stockItemFingerprint = System.nanoTime().toString())
    /** [update.unit] is written only when the target product's [CatalogueProduct.source] is
     * [com.jajusri.venture.feature.catalogue.domain.model.CatalogueProductSource.Manual] (TD-047) —
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
     * "Custom columns... round-tripped opaquely; VENTURE never interprets their content"). A `null`
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
     * [com.jajusri.venture.feature.catalogue.storage.CatalogueAssetStore], then records it in
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
