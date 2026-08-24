package com.budcom.android.feature.catalogue.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * MVP-1.4 Catalogue Room entities (docs/architecture/BUDCOM-MVP-1-4-CATALOGUE-ARCHITECTURE.md §6,
 * §8, §10, §13). Every table carries `companyId` as the first key component, matching every other
 * entity in this codebase (architecture §13).
 *
 * Deliberately does NOT store any Tally-owned field (name/unit/HSN/GST/stock-group) — those are
 * always resolved live from `cached_stock_items`
 * ([com.budcom.android.feature.masterdata.stockitem.data.local.StockItemEntity]) via
 * [CatalogueProductSourceLinkEntity.externalStockItemId], never mirrored into a Catalogue-owned
 * column. This is what makes "Tally sync must not silently destroy Catalogue enrichment"
 * (architecture §6) true by construction rather than by a carefully-written partial-update query.
 */

/** `catalogue_product` — Catalogue-owned enrichment + lifecycle state only. */
@Entity(
    tableName = "catalogue_product",
    primaryKeys = ["companyId", "productId"],
    indices = [
        Index(value = ["companyId"]),
        Index(value = ["companyId", "lifecycleState"]),
        Index(value = ["companyId", "linkedStockItemId"]),
    ],
)
data class CatalogueProductEntity(
    val companyId: String,
    val productId: String,
    /** "TALLY" or "MANUAL" — [CatalogueProductSource] name. */
    val source: String,
    /** Denormalized copy of the active [CatalogueProductSourceLinkEntity.externalStockItemId] for
     * this product, kept only to make the read-time Stock Item join a single indexed lookup — the
     * link table remains the source of truth for the relationship itself (architecture §13's
     * uniqueness-on-`(companyId, productId)` invariant is enforced there, not here). */
    val linkedStockItemId: String?,
    val sku: String?,
    val displayNameOverride: String?,
    val description: String?,
    val specifications: String?,
    val customerFacingCategory: String?,
    /** "OPEN" or "CONTACT_FOR_PRICE" — [com.budcom.android.feature.catalogue.domain.model.PriceDisplayMode] name. */
    val priceDisplayMode: String,
    val manualPriceAmount: String?,
    val manualPriceCurrencyCode: String?,
    /** "DRAFT" / "REVIEW" / "PUBLISHED" / "ARCHIVED" — [com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleState] name. */
    val lifecycleState: String,
    val sourceAvailable: Boolean,
    val createdAt: Long,
    val createdAtSource: String,
    val updatedAt: Long,
    val updatedAtSource: String,
    val archivedAt: Long?,
    val archivedAtSource: String?,
)

/**
 * `catalogue_product_source_link` — mirrors
 * [com.budcom.android.feature.party.data.local.PartySourceLinkEntity]'s exact shape/discipline
 * (architecture §5, §14): keyed by the same stable, GUID-first
 * `resolveStockItemStableId()`-derived id Stock Item sync already produces, so a Tally rename
 * never breaks the link (the id is unchanged) and never creates a duplicate product.
 *
 * Only Tally-sourced products ever get a row here — a [CatalogueProductSource.Manual] product has
 * no row in this table at all, which is itself the "no Stock Item linked" signal.
 */
@Entity(
    tableName = "catalogue_product_source_link",
    primaryKeys = ["companyId", "sourceType", "externalStockItemId"],
    indices = [
        // Architecture §13: companyId alone does not prevent two different Stock Items both
        // claiming to link the same product — this unique index is the enforcement.
        Index(value = ["companyId", "productId"], unique = true),
    ],
)
data class CatalogueProductSourceLinkEntity(
    val companyId: String,
    /** Always [CATALOGUE_SOURCE_TYPE_TALLY_STOCK_ITEM] today — a distinct constant (not a shared
     * enum with Party's own source types) since this is a new, parallel table, never a shared one
     * (architecture §5's own "no generic source-link abstraction" finding). */
    val sourceType: String,
    val externalStockItemId: String,
    val productId: String,
    val lastConfirmedAt: Long,
    val lastConfirmedAtSource: String,
)

const val CATALOGUE_SOURCE_TYPE_TALLY_STOCK_ITEM = "tally_stock_item"

/**
 * `catalogue_settings` — one row per company (mirrors `business_profile`'s own single-row-per-
 * company shape). Holds the catalogue-level Public/Private toggle (Brainstorm Outcome §5:
 * "Public/Private toggle, catalogue-level, owner-controlled"). Absent for a company that has never
 * touched Catalogue settings — defaults to Private (see
 * [com.budcom.android.feature.catalogue.data.repository.CatalogueRepositoryImpl.isPublic]), never
 * defaults to Public by omission.
 */
@Entity(
    tableName = "catalogue_settings",
    primaryKeys = ["companyId"],
)
data class CatalogueSettingsEntity(
    val companyId: String,
    val isPublic: Boolean,
    val updatedAt: Long,
    val updatedAtSource: String,
)

/**
 * `catalogue_branch` — new, foundational entity (architecture §8: "Branch does not exist anywhere
 * in this codebase today"). Deliberately minimal (no address/contact/staff-roster fields) — exists
 * only to support override-resolution and branch-assignable Publish rights. Placed alongside
 * Catalogue's own tables rather than inside the Business Profile module: architecture §8 flags
 * this as an open placement choice where "either placement satisfies the locked scope," and
 * keeping it here avoids touching the existing, unrelated `business_profile` table/module at all.
 */
@Entity(
    tableName = "catalogue_branch",
    primaryKeys = ["companyId", "branchId"],
    indices = [Index(value = ["companyId"])],
)
data class BranchEntity(
    val companyId: String,
    val branchId: String,
    val name: String,
    val isActive: Boolean,
    val createdAt: Long,
    val createdAtSource: String,
    val updatedAt: Long,
    val updatedAtSource: String,
)

/**
 * `catalogue_override` — one row per (company, level, attribute). Architecture §8: "at most one of
 * `productId`/`branchId`/`stockGroupId` is non-null per row." Represented here as a single
 * deterministic `scopeKey` string plus a `scopeType` discriminator rather than three nullable
 * columns, so the primary key can enforce "at most one row per level per attribute" directly
 * (three-nullable-columns cannot participate cleanly in a Room composite primary key). `scopeKey`
 * is `""` for [ScopeType.CATALOGUE_WIDE] (exactly one row per company per attribute).
 */
@Entity(
    tableName = "catalogue_override",
    primaryKeys = ["companyId", "scopeType", "scopeKey", "attributeName"],
    indices = [Index(value = ["companyId", "attributeName"])],
)
data class CatalogueOverrideEntity(
    val companyId: String,
    /** "ITEM" / "BRANCH" / "STOCK_GROUP" / "CATALOGUE_WIDE". */
    val scopeType: String,
    /** productId / branchId / stock-group key, or `""` for CATALOGUE_WIDE. */
    val scopeKey: String,
    val attributeName: String,
    val value: String,
    val updatedAt: Long,
    val updatedAtSource: String,
)

/**
 * `catalogue_published_snapshot` — the atomic "current published state" (architecture §7).
 * Overwritten wholesale (delete+insert in one transaction, see
 * [com.budcom.android.feature.catalogue.data.local.CataloguePublishedSnapshotDao.publish]) on every
 * successful Publish, never partially mutated. Sharing (architecture §11) reads exclusively from
 * this table — a Draft/Review product has no code path that can reach it.
 */
@Entity(
    tableName = "catalogue_published_snapshot",
    primaryKeys = ["companyId", "productId"],
    indices = [Index(value = ["companyId"])],
)
data class CataloguePublishedSnapshotEntity(
    val companyId: String,
    val productId: String,
    val displayName: String,
    val description: String?,
    val specifications: String?,
    val customerFacingCategory: String?,
    val priceDisplayMode: String,
    val resolvedPriceAmount: String?,
    val resolvedPriceCurrencyCode: String?,
    val primaryAssetId: String?,
    val publishedAt: Long,
    val publishedAtSource: String,
)

/**
 * `catalogue_asset` — image identity is `(companyId, productId, assetId)` (architecture §10);
 * `assetId` is a generated UUID, never derived from the original filename. [filePath] is a path
 * relative to [com.budcom.android.feature.catalogue.storage.CatalogueAssetStore]'s own managed
 * directory, resolved (and containment-checked) by the store, never used as an absolute path
 * directly.
 */
@Entity(
    tableName = "catalogue_asset",
    primaryKeys = ["companyId", "productId", "assetId"],
    indices = [Index(value = ["companyId", "productId"])],
)
data class CatalogueAssetEntity(
    val companyId: String,
    val productId: String,
    val assetId: String,
    val isPrimary: Boolean,
    val sortOrder: Int,
    val filePath: String,
    val createdAt: Long,
    val createdAtSource: String,
)
