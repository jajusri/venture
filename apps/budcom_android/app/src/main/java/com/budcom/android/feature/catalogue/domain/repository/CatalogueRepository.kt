package com.budcom.android.feature.catalogue.domain.repository

import com.budcom.android.feature.catalogue.domain.model.Branch
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleAction
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideAttribute
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideRow
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideScope
import com.budcom.android.feature.catalogue.domain.model.CatalogueProduct
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleState
import com.budcom.android.feature.catalogue.domain.model.CataloguePublishedSnapshot
import com.budcom.android.feature.catalogue.domain.model.CatalogueTimestamp

/** Enrichment fields an owner/staff member can edit — deliberately excludes every Tally-owned
 * field (architecture §6's field-ownership table) and every lifecycle/identity field (those move
 * only via [CatalogueRepository.transitionLifecycle]/link operations). `null` in any property
 * means "leave unchanged," matching this codebase's own partial-update convention elsewhere. */
data class CatalogueEnrichmentUpdate(
    val displayNameOverride: String? = null,
    val clearDisplayNameOverride: Boolean = false,
    val sku: String? = null,
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
    suspend fun findProduct(companyId: String, productId: String): CatalogueProduct?
    suspend fun listProducts(companyId: String): List<CatalogueProduct>
    suspend fun listProductsByState(companyId: String, state: CatalogueLifecycleState): List<CatalogueProduct>
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

    suspend fun listPublishedForCategory(companyId: String, category: String): List<CataloguePublishedSnapshot>
    suspend fun listAllPublished(companyId: String): List<CataloguePublishedSnapshot>

    /** Catalogue-level Public/Private toggle (Brainstorm Outcome §5). Defaults to `false`
     * (Private) for a company that has never set it — Catalogue must never default to publicly
     * shareable. */
    suspend fun isPublic(companyId: String): Boolean
    suspend fun setPublic(companyId: String, isPublic: Boolean, timestamp: CatalogueTimestamp)
}
