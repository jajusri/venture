package com.jajusri.venture.feature.catalogue.domain.model

/**
 * MVP-1.4 Catalogue domain models
 * (docs/architecture/VENTURE-MVP-1-4-CATALOGUE-ARCHITECTURE.md).
 *
 * Deliberately does NOT mirror any Tally-owned field (name/unit/HSN/GST/parent group) into a
 * Catalogue-owned column anywhere in this module (see [CatalogueProduct]'s own doc comment) — this
 * is what makes "Tally sync must not silently destroy Catalogue enrichment" (architecture §6)
 * trivially true rather than something that has to be carefully preserved by a partial-update
 * query: there is no mirrored copy in Catalogue's own tables for a sync to ever overwrite.
 */

/** How a [CatalogueProduct] came to exist. Brainstorm Outcome §4 "Core identity — Source". */
enum class CatalogueProductSource { Tally, Manual }

/**
 * Draft → Review → Publish → Archive (architecture §7). `Review` is intentionally not a stricter
 * edit-lock than `Draft` — the architecture document itself flags this as a proposed default, not
 * a locked rule (§7, §22 item 5), since no stricter rule was ever specified.
 */
enum class CatalogueLifecycleState { Draft, Review, Published, Archived }

/** Brainstorm Outcome §4 "Pricing — No pricing engine. Open (visible) or 'Contact for price' only." */
enum class PriceDisplayMode { Open, ContactForPrice }

/**
 * Mid-MVP-1.4 product lock: "Price is optional, but the price state is not" — a viewer must always
 * see one of these three **explicit** states, never an apparently-missing field. [ContactForPrice]
 * (the seller's deliberate choice) must never be shown for, or confused with, [NoPriceSupplied]
 * (the seller simply hasn't entered a number yet under [PriceDisplayMode.Open]) — the two look
 * identical to a naive `amount == null` check, which is exactly the bug this type exists to
 * prevent structurally. [resolveCataloguePriceState] is the single place this resolution happens;
 * every price-rendering call site (share text, and any future one) must go through it rather than
 * re-deriving the same three-way distinction ad hoc.
 */
sealed interface CataloguePriceState {
    data class ActualPrice(val amount: String, val currencyCode: String?) : CataloguePriceState
    /** [PriceDisplayMode.Open] selected, but no amount has been entered yet — distinct from
     * [ContactForPrice], never collapsed into it. */
    data object NoPriceSupplied : CataloguePriceState
    data object ContactForPrice : CataloguePriceState
}

fun resolveCataloguePriceState(displayMode: PriceDisplayMode, amount: String?, currencyCode: String?): CataloguePriceState =
    when (displayMode) {
        PriceDisplayMode.ContactForPrice -> CataloguePriceState.ContactForPrice
        PriceDisplayMode.Open -> amount?.trim()?.takeIf(String::isNotEmpty)
            ?.let { CataloguePriceState.ActualPrice(it, currencyCode) }
            ?: CataloguePriceState.NoPriceSupplied
    }

/** Brainstorm Outcome §4 "Price-sync from Tally: Auto or Manual". Resolved per the same override
 * chain as every other override-capable attribute (architecture §8/§12). */
enum class PriceSyncMode { Auto, Manual }

/** Architecture §15: a timestamp is either sourced from the paired Connector (the closest thing to
 * an authoritative "server" in this LAN-local architecture) or is a device-local reading used only
 * because no Connector was reachable at the time — never silently treated as equally authoritative. */
enum class CatalogueTimestampSource { Connector, DeviceLocalProvisional }

data class CatalogueTimestamp(
    val epochMillis: Long,
    val source: CatalogueTimestampSource,
)

/**
 * A single override-resolution level (architecture §8's locked precedence:
 * Item → Branch → Stock-group → Catalogue-wide). [CatalogueWide] carries no key because exactly
 * one catalogue-wide default exists per company per attribute.
 */
sealed interface CatalogueOverrideScope {
    data class Item(val productId: String) : CatalogueOverrideScope
    data class Branch(val branchId: String) : CatalogueOverrideScope
    /** Keyed by the Tally Stock Group's own name/slug — architecture §2/§16 confirms no persisted,
     * stable Stock Group id table exists anywhere in this codebase yet, so the group's own
     * (already-deterministic) name/slug string is the best available stable key. */
    data class StockGroup(val stockGroupKey: String) : CatalogueOverrideScope
    data object CatalogueWide : CatalogueOverrideScope
}

/** Named, extensible override-capable attributes. Only price-sync-mode is locked for MVP-1.4
 * (Brainstorm Outcome §4/§12); the row shape (architecture §8) generalizes to future attributes
 * without a schema change — adding one here is additive, not a migration. */
enum class CatalogueOverrideAttribute(val attributeName: String) {
    PriceSyncMode("PRICE_SYNC_MODE"),
}

data class CatalogueOverrideRow(
    val companyId: String,
    val scope: CatalogueOverrideScope,
    val attribute: CatalogueOverrideAttribute,
    val value: String,
    val updatedAt: CatalogueTimestamp,
)

data class Branch(
    val companyId: String,
    val branchId: String,
    val name: String,
    val isActive: Boolean,
    val createdAt: CatalogueTimestamp,
    val updatedAt: CatalogueTimestamp,
)

data class CatalogueAsset(
    val companyId: String,
    val productId: String,
    val assetId: String,
    val isPrimary: Boolean,
    val sortOrder: Int,
    /** Path relative to [com.jajusri.venture.feature.catalogue.storage.CatalogueAssetStore]'s own
     * managed directory — resolve via [com.jajusri.venture.feature.catalogue.domain.repository.CatalogueRepository.resolveAssetFile],
     * never used directly as a filesystem path. */
    val filePath: String,
    val createdAt: CatalogueTimestamp,
)

/**
 * A Catalogue product's own enrichment plus its resolved view of the Tally-owned fields it links
 * to (never stored, only joined at read time — see [com.jajusri.venture.feature.catalogue.data.repository.CatalogueRepositoryImpl]).
 *
 * [tallyName]/[unit]/[hsnCode]/[gstRate]/[stockGroupKey] are `null` for a [CatalogueProductSource.Manual]
 * product, or for a Tally-sourced product whose linked Stock Item is currently [sourceAvailable] == false.
 */
data class CatalogueProduct(
    val companyId: String,
    val productId: String,
    val source: CatalogueProductSource,
    val linkedStockItemId: String?,
    val sku: String?,
    /** Owner-entered override; when `null`, [tallyName] is the display name. */
    val displayNameOverride: String?,
    val tallyName: String?,
    val unit: String?,
    val hsnCode: String?,
    val gstRate: String?,
    val stockGroupKey: String?,
    val description: String?,
    val specifications: String?,
    val customerFacingCategory: String?,
    val priceDisplayMode: PriceDisplayMode,
    val manualPriceAmount: String?,
    val manualPriceCurrencyCode: String?,
    val lifecycleState: CatalogueLifecycleState,
    /** False when the linked Stock Item has disappeared from the latest Tally sync (architecture
     * §6 property 3) — never auto-deleted, always owner-visible, auto-clears on reappearance. */
    val sourceAvailable: Boolean,
    val createdAt: CatalogueTimestamp,
    val updatedAt: CatalogueTimestamp,
    val archivedAt: CatalogueTimestamp?,
) {
    val displayName: String get() = displayNameOverride ?: tallyName ?: "Unnamed product"
}

/**
 * The atomic "current published state" record (architecture §7) — overwritten wholesale on each
 * successful Publish, never partially mutated. This is what customer-facing sharing (architecture
 * §11) reads from; Draft/Review-state edits are structurally invisible to it because they simply
 * never touch this table.
 */
data class CataloguePublishedSnapshot(
    val companyId: String,
    val productId: String,
    val displayName: String,
    val description: String?,
    val specifications: String?,
    val customerFacingCategory: String?,
    val priceDisplayMode: PriceDisplayMode,
    val resolvedPriceAmount: String?,
    val resolvedPriceCurrencyCode: String?,
    val primaryAssetId: String?,
    val publishedAt: CatalogueTimestamp,
)
