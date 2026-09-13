package com.jajusri.venture.feature.catalogue.data.repository

import android.net.Uri
import com.jajusri.venture.core.util.DispatcherProvider
import com.jajusri.venture.feature.catalogue.data.local.CATALOGUE_SOURCE_TYPE_TALLY_STOCK_ITEM
import com.jajusri.venture.feature.catalogue.data.local.BranchDao
import com.jajusri.venture.feature.catalogue.data.local.BranchEntity
import com.jajusri.venture.feature.catalogue.data.local.CatalogueAssetDao
import com.jajusri.venture.feature.catalogue.data.local.CatalogueAssetEntity
import com.jajusri.venture.feature.catalogue.data.local.CatalogueCustomFieldDao
import com.jajusri.venture.feature.catalogue.data.local.CatalogueCustomFieldEntity
import com.jajusri.venture.feature.catalogue.data.local.CatalogueOverrideDao
import com.jajusri.venture.feature.catalogue.data.local.CatalogueOverrideEntity
import com.jajusri.venture.feature.catalogue.data.local.CatalogueProductDao
import com.jajusri.venture.feature.catalogue.data.local.CatalogueProductEntity
import com.jajusri.venture.feature.catalogue.data.local.CatalogueProductSourceLinkDao
import com.jajusri.venture.feature.catalogue.data.local.CatalogueProductSourceLinkEntity
import com.jajusri.venture.feature.catalogue.data.local.CataloguePublishedSnapshotDao
import com.jajusri.venture.feature.catalogue.data.local.CataloguePublishedSnapshotEntity
import com.jajusri.venture.feature.catalogue.data.local.CatalogueSettingsDao
import com.jajusri.venture.feature.catalogue.data.local.CatalogueSettingsEntity
import com.jajusri.venture.feature.catalogue.domain.model.Branch
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueLifecycleAction
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueLifecycleState
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueLifecycleTransitions
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueOverrideAttribute
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueOverrideResolver
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueOverrideRow
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueOverrideScope
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueProduct
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueProductSource
import com.jajusri.venture.feature.catalogue.domain.model.CataloguePublishedSnapshot
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueTimestamp
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueTimestampSource
import com.jajusri.venture.feature.catalogue.domain.model.PriceDisplayMode
import com.jajusri.venture.feature.catalogue.domain.repository.CatalogueChangeSignal
import com.jajusri.venture.feature.catalogue.domain.repository.CatalogueEnrichmentUpdate
import com.jajusri.venture.feature.catalogue.domain.repository.CatalogueLifecycleResult
import com.jajusri.venture.feature.catalogue.domain.repository.CatalogueProductPage
import com.jajusri.venture.feature.catalogue.domain.repository.CatalogueProductPageCursor
import com.jajusri.venture.feature.catalogue.domain.repository.CatalogueReconciliationResult
import com.jajusri.venture.feature.catalogue.domain.repository.CatalogueRepository
import com.jajusri.venture.feature.catalogue.storage.CatalogueAssetResult
import com.jajusri.venture.feature.catalogue.storage.CatalogueAssetStore
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItem
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemStatus
import com.jajusri.venture.feature.masterdata.stockitem.domain.port.StockItemLookupPort
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MVP-1.4 Catalogue repository. Deliberately reads every Tally-owned field (name/unit/HSN/GST/
 * stock group) live from [StockItemLookupPort] on every product read rather than mirroring a copy
 * into `catalogue_product` — see `CatalogueEntities.kt`'s own doc comment for why this makes
 * non-destructive Tally sync true by construction (architecture §6).
 */
@Singleton
class CatalogueRepositoryImpl @Inject constructor(
    private val productDao: CatalogueProductDao,
    private val sourceLinkDao: CatalogueProductSourceLinkDao,
    private val branchDao: BranchDao,
    private val overrideDao: CatalogueOverrideDao,
    private val publishedSnapshotDao: CataloguePublishedSnapshotDao,
    private val settingsDao: CatalogueSettingsDao,
    private val assetDao: CatalogueAssetDao,
    private val assetStore: CatalogueAssetStore,
    private val customFieldDao: CatalogueCustomFieldDao,
    private val stockItemLookup: StockItemLookupPort,
    private val dispatchers: DispatcherProvider,
) : CatalogueRepository {

    /** In-memory, per-company local-write counter backing [currentChangeSignal] -- bumped by every
     * write this repository makes to `catalogue_product` (see [upsertProduct]/[upsertProducts]).
     * Deliberately not persisted: it only needs to answer "has anything changed since the last time
     * *this process* checked," and a fresh process always takes the unconditional-load path anyway
     * (see [CatalogueRepository.listProductsPage]/[CatalogueViewModel]'s own initial load). */
    private val localRevisions = java.util.concurrent.ConcurrentHashMap<String, Int>()

    private fun bumpRevision(companyId: String) {
        localRevisions.merge(companyId, 1, Int::plus)
    }

    private suspend fun upsertProduct(entity: CatalogueProductEntity) {
        productDao.upsert(entity)
        bumpRevision(entity.companyId)
    }

    private suspend fun upsertProducts(entities: List<CatalogueProductEntity>) {
        if (entities.isEmpty()) return
        productDao.upsertAll(entities)
        bumpRevision(entities.first().companyId)
    }

    override suspend fun createDraftFromStockItem(
        companyId: String,
        stockItemId: String,
        timestamp: CatalogueTimestamp,
    ): CatalogueProduct? = withContext(dispatchers.io) {
        val existingLink = sourceLinkDao.findByExternalKey(companyId, CATALOGUE_SOURCE_TYPE_TALLY_STOCK_ITEM, stockItemId)
        if (existingLink != null) {
            // Idempotent: re-requesting a Draft for an already-linked Stock Item returns the
            // existing product rather than creating a duplicate (architecture §6 property 2).
            return@withContext productDao.findById(companyId, existingLink.productId)?.let { toDomain(companyId, it) }
        }
        val stockItem = stockItemLookup.findById(companyId, stockItemId) ?: return@withContext null
        val (productEntity, linkEntity) = buildDraftEntities(companyId, stockItemId, stockItem, timestamp)
        upsertProduct(productEntity)
        sourceLinkDao.upsert(linkEntity)
        toDomain(companyId, productEntity, stockItem)
    }

    override suspend fun createDraftsFromStockItems(
        companyId: String,
        stockItemIds: List<String>,
        timestamp: CatalogueTimestamp,
        onProgress: suspend (linked: Int, total: Int) -> Unit,
    ): Int = withContext(dispatchers.io) {
        val total = stockItemIds.size
        if (total == 0) return@withContext 0

        // Loaded once for the whole batch rather than per item -- this, plus upsertAll below, is
        // what collapses ~3 DB round-trips per item down to a handful of calls for the whole run
        // (TD-051: was ~2 sec/item, ~45 min for 1,208 items).
        val alreadyLinked = sourceLinkDao.findAllForCompany(companyId).mapTo(mutableSetOf()) { it.externalStockItemId }
        val stockItemsById = stockItemLookup.listAllForCompany(companyId).associateBy { it.id }

        var linked = 0
        onProgress(linked, total)
        for (chunk in stockItemIds.chunked(CATALOGUE_LINK_ALL_CHUNK_SIZE)) {
            val products = mutableListOf<CatalogueProductEntity>()
            val links = mutableListOf<CatalogueProductSourceLinkEntity>()
            for (stockItemId in chunk) {
                // Same two idempotency/availability skips as createDraftFromStockItem: an
                // already-linked id is never duplicated, and a stock item no longer cached
                // locally (e.g. removed between listing and confirming) is silently skipped
                // rather than failing the whole run.
                if (stockItemId in alreadyLinked) continue
                val stockItem = stockItemsById[stockItemId] ?: continue
                val (productEntity, linkEntity) = buildDraftEntities(companyId, stockItemId, stockItem, timestamp)
                products += productEntity
                links += linkEntity
                alreadyLinked += stockItemId
            }
            if (products.isNotEmpty()) {
                // Each of these two calls is its own single-transaction batch (Room wraps a
                // List-parameter @Insert in one commit) -- this chunk either contributes both its
                // products and their links, or (on a mid-chunk failure) neither, since the product
                // write always happens first and nothing here reads a half-written chunk back.
                upsertProducts(products)
                sourceLinkDao.upsertAll(links)
                linked += products.size
            }
            onProgress(linked, total)
        }
        linked
    }

    override suspend fun warmStockItemCache(companyId: String): Unit = withContext(dispatchers.io) {
        stockItemLookup.warmStockItemCache(companyId)
    }

    private fun buildDraftEntities(
        companyId: String,
        stockItemId: String,
        stockItem: StockItem,
        timestamp: CatalogueTimestamp,
    ): Pair<CatalogueProductEntity, CatalogueProductSourceLinkEntity> {
        val productId = UUID.randomUUID().toString()
        val (epoch, source) = timestamp.toPair()
        val productEntity = CatalogueProductEntity(
            companyId = companyId,
            productId = productId,
            source = CatalogueProductSource.Tally.name,
            linkedStockItemId = stockItemId,
            sku = stockItem.partNumber,
            displayNameOverride = null,
            manualUnit = null,
            description = null,
            specifications = null,
            customerFacingCategory = null,
            priceDisplayMode = PriceDisplayMode.ContactForPrice.name,
            manualPriceAmount = null,
            manualPriceCurrencyCode = null,
            lifecycleState = CatalogueLifecycleState.Draft.name,
            sourceAvailable = true,
            createdAt = epoch,
            createdAtSource = source,
            updatedAt = epoch,
            updatedAtSource = source,
            archivedAt = null,
            archivedAtSource = null,
        )
        val linkEntity = CatalogueProductSourceLinkEntity(
            companyId = companyId,
            sourceType = CATALOGUE_SOURCE_TYPE_TALLY_STOCK_ITEM,
            externalStockItemId = stockItemId,
            productId = productId,
            lastConfirmedAt = epoch,
            lastConfirmedAtSource = source,
        )
        return productEntity to linkEntity
    }

    override suspend fun createManualDraft(
        companyId: String,
        displayName: String,
        timestamp: CatalogueTimestamp,
    ): CatalogueProduct = withContext(dispatchers.io) {
        val productId = UUID.randomUUID().toString()
        val (epoch, source) = timestamp.toPair()
        val entity = CatalogueProductEntity(
            companyId = companyId,
            productId = productId,
            source = CatalogueProductSource.Manual.name,
            linkedStockItemId = null,
            sku = null,
            displayNameOverride = displayName,
            manualUnit = null,
            description = null,
            specifications = null,
            customerFacingCategory = null,
            priceDisplayMode = PriceDisplayMode.ContactForPrice.name,
            manualPriceAmount = null,
            manualPriceCurrencyCode = null,
            lifecycleState = CatalogueLifecycleState.Draft.name,
            sourceAvailable = true,
            createdAt = epoch,
            createdAtSource = source,
            updatedAt = epoch,
            updatedAtSource = source,
            archivedAt = null,
            archivedAtSource = null,
        )
        upsertProduct(entity)
        toDomain(companyId, entity)
    }

    override suspend fun listUnlinkedStockItems(companyId: String): List<StockItem> = withContext(dispatchers.io) {
        val linkedIds = sourceLinkDao.findAllForCompany(companyId).map { it.externalStockItemId }.toSet()
        stockItemLookup.listAllForCompany(companyId).filterNot { it.id in linkedIds }
    }

    override suspend fun findProduct(companyId: String, productId: String): CatalogueProduct? = withContext(dispatchers.io) {
        productDao.findById(companyId, productId)?.let { toDomain(companyId, it) }
    }

    override suspend fun listProducts(companyId: String): List<CatalogueProduct> = withContext(dispatchers.io) {
        productDao.findAllForCompany(companyId).map { toDomain(companyId, it) }
    }

    override suspend fun listProductsByState(companyId: String, state: CatalogueLifecycleState): List<CatalogueProduct> =
        withContext(dispatchers.io) {
            productDao.findAllByLifecycleState(companyId, state.name).map { toDomain(companyId, it) }
        }

    override suspend fun updateEnrichment(
        companyId: String,
        productId: String,
        update: CatalogueEnrichmentUpdate,
        timestamp: CatalogueTimestamp,
    ): CatalogueProduct? = withContext(dispatchers.io) {
        val existing = productDao.findById(companyId, productId) ?: return@withContext null
        val (epoch, source) = timestamp.toPair()
        val updated = existing.copy(
            displayNameOverride = when {
                update.clearDisplayNameOverride -> null
                update.displayNameOverride != null -> update.displayNameOverride
                else -> existing.displayNameOverride
            },
            sku = update.sku ?: existing.sku,
            // TD-047: a Manual product's Unit is Catalogue-owned; a Tally-linked product's Unit
            // stays exclusively Tally-authoritative -- this write never reaches it regardless of
            // what a caller passes in `update.unit`, enforced here rather than only by the UI.
            manualUnit = if (existing.source == CatalogueProductSource.Manual.name) update.unit ?: existing.manualUnit else existing.manualUnit,
            description = update.description ?: existing.description,
            specifications = update.specifications ?: existing.specifications,
            customerFacingCategory = update.customerFacingCategory ?: existing.customerFacingCategory,
            priceDisplayMode = update.priceDisplayMode?.name ?: existing.priceDisplayMode,
            manualPriceAmount = update.manualPriceAmount ?: existing.manualPriceAmount,
            manualPriceCurrencyCode = update.manualPriceCurrencyCode ?: existing.manualPriceCurrencyCode,
            updatedAt = epoch,
            updatedAtSource = source,
        )
        upsertProduct(updated)
        toDomain(companyId, updated)
    }

    override suspend fun transitionLifecycle(
        companyId: String,
        productId: String,
        action: CatalogueLifecycleAction,
        isOwner: Boolean,
        timestamp: CatalogueTimestamp,
    ): CatalogueLifecycleResult = withContext(dispatchers.io) {
        val existing = productDao.findById(companyId, productId) ?: return@withContext CatalogueLifecycleResult.ProductNotFound
        val current = CatalogueLifecycleState.valueOf(existing.lifecycleState)
        val next = CatalogueLifecycleTransitions.transition(current, action, isOwner)
            ?: return@withContext CatalogueLifecycleResult.Rejected
        val (epoch, source) = timestamp.toPair()
        val updated = existing.copy(
            lifecycleState = next.name,
            updatedAt = epoch,
            updatedAtSource = source,
            archivedAt = if (next == CatalogueLifecycleState.Archived) epoch else null,
            archivedAtSource = if (next == CatalogueLifecycleState.Archived) source else null,
        )
        upsertProduct(updated)

        when (next) {
            CatalogueLifecycleState.Published -> publishSnapshot(companyId, updated, timestamp)
            CatalogueLifecycleState.Archived -> publishedSnapshotDao.deleteByProductId(companyId, productId)
            else -> Unit
        }

        CatalogueLifecycleResult.Success(toDomain(companyId, updated))
    }

    /**
     * Builds the atomic "current published state" record (architecture §7). Resolved price is
     * always [CatalogueProductEntity.manualPriceAmount] today — [CatalogueOverrideAttribute.PriceSyncMode]
     * resolves correctly through the override chain and is exposed for future use, but no Tally
     * "rate" field exists anywhere in the current `StockItem` pipeline (the Connector fetches
     * `OPENINGRATE` but never maps/persists it) for Auto mode to actually source from — a named,
     * deliberate limitation, not a silent gap. See the MVP-1.4 implementation report.
     */
    private suspend fun publishSnapshot(companyId: String, product: CatalogueProductEntity, timestamp: CatalogueTimestamp) {
        val stockItem = product.linkedStockItemId?.let { stockItemLookup.findById(companyId, it) }
        val primaryAssetId = assetDao.findAllForProduct(companyId, product.productId).firstOrNull { it.isPrimary }?.assetId
        val (epoch, source) = timestamp.toPair()
        publishedSnapshotDao.publish(
            CataloguePublishedSnapshotEntity(
                companyId = companyId,
                productId = product.productId,
                displayName = product.displayNameOverride ?: stockItem?.name ?: "Unnamed product",
                description = product.description,
                specifications = product.specifications,
                customerFacingCategory = product.customerFacingCategory,
                priceDisplayMode = product.priceDisplayMode,
                resolvedPriceAmount = product.manualPriceAmount,
                resolvedPriceCurrencyCode = product.manualPriceCurrencyCode,
                primaryAssetId = primaryAssetId,
                publishedAt = epoch,
                publishedAtSource = source,
            ),
        )
    }

    override suspend fun reconcileStockItemLinks(companyId: String, timestamp: CatalogueTimestamp): CatalogueReconciliationResult =
        withContext(dispatchers.io) {
            val nowUnavailable = mutableListOf<String>()
            val reappeared = mutableListOf<String>()
            val (epoch, source) = timestamp.toPair()
            val linkedProducts = productDao.findAllLinkedToStockItems(companyId)
            // Batched lookup (Catalogue perf package): one query for every linked product's Stock
            // Item instead of one findById round trip per product -- this loop below is now a pure
            // in-memory map read, never a DB call.
            val stockItemsById = linkedProducts.mapNotNull { it.linkedStockItemId }.distinct()
                .let { ids -> if (ids.isEmpty()) emptyMap() else stockItemLookup.findByIds(companyId, ids).associateBy { it.id } }
            linkedProducts.forEach { product ->
                val stockItemId = product.linkedStockItemId ?: return@forEach
                val stockItem = stockItemsById[stockItemId]
                val isAvailable = stockItem != null && stockItem.status != StockItemStatus.Inactive
                if (isAvailable != product.sourceAvailable) {
                    upsertProduct(product.copy(sourceAvailable = isAvailable, updatedAt = epoch, updatedAtSource = source))
                    if (isAvailable) reappeared += product.productId else nowUnavailable += product.productId
                }
            }
            CatalogueReconciliationResult(nowUnavailable, reappeared)
        }

    override suspend fun listProductsPage(
        companyId: String,
        cursor: CatalogueProductPageCursor?,
        pageSize: Int,
    ): CatalogueProductPage = withContext(dispatchers.io) {
        val cursorUpdatedAt = cursor?.updatedAt ?: Long.MAX_VALUE
        val cursorProductId = cursor?.productId ?: ""
        // Fetch one extra row so "is there another page" is known from this single query, never a
        // second round trip just to check.
        val fetched = productDao.findPageForCompany(companyId, cursorUpdatedAt, cursorProductId, pageSize + 1)
        val pageEntities = fetched.take(pageSize)
        val hasMore = fetched.size > pageSize
        val stockItemIds = pageEntities.mapNotNull { it.linkedStockItemId }.distinct()
        val stockItemsById = if (stockItemIds.isEmpty()) emptyMap() else stockItemLookup.findByIds(companyId, stockItemIds).associateBy { it.id }
        val products = pageEntities.map { entity ->
            toDomain(companyId, entity, preResolved = entity.linkedStockItemId?.let { stockItemsById[it] })
        }
        val nextCursor = if (hasMore) pageEntities.last().let { CatalogueProductPageCursor(it.updatedAt, it.productId) } else null
        CatalogueProductPage(products, nextCursor)
    }

    override suspend fun primaryAssetFiles(companyId: String, productIds: List<String>): Map<String, File?> =
        withContext(dispatchers.io) {
            if (productIds.isEmpty()) return@withContext emptyMap()
            val byProduct = assetDao.findAllForProducts(companyId, productIds).groupBy { it.productId }
            productIds.associateWith { productId ->
                val productAssets = byProduct[productId] ?: return@associateWith null
                val primary = productAssets.firstOrNull { it.isPrimary } ?: productAssets.firstOrNull() ?: return@associateWith null
                assetStore.resolveAssetFile(companyId, productId, primary.filePath)
            }
        }

    override suspend fun currentChangeSignal(companyId: String): CatalogueChangeSignal = withContext(dispatchers.io) {
        CatalogueChangeSignal(
            localRevision = localRevisions[companyId] ?: 0,
            stockItemFingerprint = stockItemLookup.freshnessFingerprint(companyId),
        )
    }

    override suspend fun upsertBranch(
        companyId: String,
        branchId: String,
        name: String,
        isActive: Boolean,
        timestamp: CatalogueTimestamp,
    ): Branch = withContext(dispatchers.io) {
        val (epoch, source) = timestamp.toPair()
        val existing = branchDao.findById(companyId, branchId)
        val entity = BranchEntity(
            companyId = companyId,
            branchId = branchId,
            name = name,
            isActive = isActive,
            createdAt = existing?.createdAt ?: epoch,
            createdAtSource = existing?.createdAtSource ?: source,
            updatedAt = epoch,
            updatedAtSource = source,
        )
        branchDao.upsert(entity)
        entity.toDomain()
    }

    override suspend fun listBranches(companyId: String): List<Branch> = withContext(dispatchers.io) {
        branchDao.findAllForCompany(companyId).map { it.toDomain() }
    }

    override suspend fun setOverride(
        companyId: String,
        scope: CatalogueOverrideScope,
        attribute: CatalogueOverrideAttribute,
        value: String,
        timestamp: CatalogueTimestamp,
    ) = withContext(dispatchers.io) {
        val (epoch, source) = timestamp.toPair()
        val (scopeType, scopeKey) = scope.toEntityKey()
        overrideDao.upsert(
            CatalogueOverrideEntity(
                companyId = companyId,
                scopeType = scopeType,
                scopeKey = scopeKey,
                attributeName = attribute.attributeName,
                value = value,
                updatedAt = epoch,
                updatedAtSource = source,
            ),
        )
    }

    override suspend fun clearOverride(companyId: String, scope: CatalogueOverrideScope, attribute: CatalogueOverrideAttribute) =
        withContext(dispatchers.io) {
            val (scopeType, scopeKey) = scope.toEntityKey()
            overrideDao.delete(companyId, scopeType, scopeKey, attribute.attributeName)
        }

    override suspend fun resolveOverride(
        companyId: String,
        productId: String,
        branchId: String?,
        attribute: CatalogueOverrideAttribute,
    ): CatalogueOverrideRow? = withContext(dispatchers.io) {
        val product = productDao.findById(companyId, productId)
        val stockItem = product?.linkedStockItemId?.let { stockItemLookup.findById(companyId, it) }
        val rows = overrideDao.findAllForCompanyAndAttribute(companyId, attribute.attributeName).map { it.toDomain() }
        CatalogueOverrideResolver.resolve(rows, productId, branchId, stockItem?.parentGroup)
    }

    override suspend fun upsertCustomFields(
        companyId: String,
        productId: String,
        values: Map<String, String?>,
        timestamp: CatalogueTimestamp,
    ) = withContext(dispatchers.io) {
        val (epoch, source) = timestamp.toPair()
        values.forEach { (columnName, value) ->
            customFieldDao.upsert(CatalogueCustomFieldEntity(companyId, productId, columnName, value, epoch, source))
        }
    }

    override suspend fun listCustomFields(companyId: String, productId: String): Map<String, String?> = withContext(dispatchers.io) {
        customFieldDao.findAllForProduct(companyId, productId).associate { it.columnName to it.value }
    }

    override suspend fun listAllCustomFieldColumnNames(companyId: String): List<String> = withContext(dispatchers.io) {
        customFieldDao.findAllColumnNamesForCompany(companyId)
    }

    override suspend fun listPublishedForCategory(companyId: String, category: String): List<CataloguePublishedSnapshot> =
        withContext(dispatchers.io) {
            publishedSnapshotDao.findAllPublishedForCategory(companyId, category).map { it.toDomain() }
        }

    override suspend fun listAllPublished(companyId: String): List<CataloguePublishedSnapshot> = withContext(dispatchers.io) {
        publishedSnapshotDao.findAllPublishedForCompany(companyId).map { it.toDomain() }
    }

    override suspend fun isPublic(companyId: String): Boolean = withContext(dispatchers.io) {
        settingsDao.findByCompany(companyId)?.isPublic ?: false
    }

    override suspend fun setPublic(companyId: String, isPublic: Boolean, timestamp: CatalogueTimestamp) = withContext(dispatchers.io) {
        val (epoch, source) = timestamp.toPair()
        settingsDao.upsert(CatalogueSettingsEntity(companyId, isPublic, epoch, source))
    }

    override suspend fun addAsset(
        companyId: String,
        productId: String,
        sourceUri: Uri,
        timestamp: CatalogueTimestamp,
    ): CatalogueAssetResult = withContext(dispatchers.io) {
        when (val saved = assetStore.saveAsset(companyId, productId, sourceUri)) {
            is CatalogueAssetResult.Failure -> saved
            is CatalogueAssetResult.Success -> {
                val (epoch, source) = timestamp.toPair()
                val existing = assetDao.findAllForProduct(companyId, productId)
                assetDao.upsert(
                    CatalogueAssetEntity(
                        companyId = companyId,
                        productId = productId,
                        assetId = saved.assetId,
                        // The first image a product ever gets becomes primary automatically
                        // (architecture §10: "one primary image per SKU"); later ones do not,
                        // until explicitly promoted via setPrimaryAsset.
                        isPrimary = existing.isEmpty(),
                        sortOrder = existing.size,
                        filePath = saved.filePath,
                        createdAt = epoch,
                        createdAtSource = source,
                    ),
                )
                saved
            }
        }
    }

    override suspend fun listAssets(companyId: String, productId: String): List<com.jajusri.venture.feature.catalogue.domain.model.CatalogueAsset> =
        withContext(dispatchers.io) {
            assetDao.findAllForProduct(companyId, productId).map { it.toDomain() }
        }

    override suspend fun setPrimaryAsset(companyId: String, productId: String, assetId: String, timestamp: CatalogueTimestamp) =
        withContext(dispatchers.io) {
            assetDao.clearPrimaryExcept(companyId, productId, keepAssetId = assetId)
            val entity = assetDao.findById(companyId, productId, assetId) ?: return@withContext
            assetDao.upsert(entity.copy(isPrimary = true))
        }

    override suspend fun deleteAsset(companyId: String, productId: String, assetId: String): Unit = withContext(dispatchers.io) {
        val entity = assetDao.findById(companyId, productId, assetId) ?: return@withContext
        // Soft-remove the DB row before deleting the file (architecture §10) -- a dangling DB
        // reference to a deleted file is never possible.
        assetDao.delete(companyId, productId, assetId)
        assetStore.deleteAsset(companyId, productId, entity.filePath)
        if (entity.isPrimary) {
            // A product with any images remaining always has exactly one primary -- promote the
            // next one automatically rather than leaving the product primary-less.
            assetDao.findAllForProduct(companyId, productId).firstOrNull()?.let { assetDao.upsert(it.copy(isPrimary = true)) }
        }
    }

    override fun resolveAssetFile(companyId: String, productId: String, filePath: String): File? =
        assetStore.resolveAssetFile(companyId, productId, filePath)

    private suspend fun toDomain(companyId: String, entity: CatalogueProductEntity, preResolved: StockItem? = null): CatalogueProduct {
        val stockItem = preResolved ?: entity.linkedStockItemId?.let { stockItemLookup.findById(companyId, it) }
        return CatalogueProduct(
            companyId = companyId,
            productId = entity.productId,
            source = CatalogueProductSource.valueOf(entity.source),
            linkedStockItemId = entity.linkedStockItemId,
            sku = entity.sku,
            displayNameOverride = entity.displayNameOverride,
            tallyName = stockItem?.name,
            // TD-047: Tally-linked products keep Unit exclusively Tally-authoritative (never
            // manualUnit, even if somehow populated); Manual products use their own Catalogue-owned
            // manualUnit, since no Stock Item join exists to resolve it from.
            unit = if (entity.source == CatalogueProductSource.Tally.name) stockItem?.baseUnit else entity.manualUnit,
            hsnCode = stockItem?.hsnCode,
            gstRate = stockItem?.gstRate,
            stockGroupKey = stockItem?.parentGroup,
            description = entity.description,
            specifications = entity.specifications,
            customerFacingCategory = entity.customerFacingCategory,
            priceDisplayMode = PriceDisplayMode.valueOf(entity.priceDisplayMode),
            manualPriceAmount = entity.manualPriceAmount,
            manualPriceCurrencyCode = entity.manualPriceCurrencyCode,
            lifecycleState = CatalogueLifecycleState.valueOf(entity.lifecycleState),
            sourceAvailable = entity.sourceAvailable,
            createdAt = entityTimestamp(entity.createdAt, entity.createdAtSource),
            updatedAt = entityTimestamp(entity.updatedAt, entity.updatedAtSource),
            archivedAt = entity.archivedAt?.let { entityTimestamp(it, entity.archivedAtSource!!) },
        )
    }
}

/** Batch size for [CatalogueRepositoryImpl.createDraftsFromStockItems]'s chunked writes (TD-051)
 * — small enough that [onProgress] (see that method's own doc comment) updates at a reasonable
 * interval during a large Link-all run, large enough that the number of DB transactions stays
 * tiny (1,208 items -> 7 chunks, not 1,208 single-row transactions). `internal` so tests can
 * construct exact multi-chunk scenarios against the same value production uses. */
internal const val CATALOGUE_LINK_ALL_CHUNK_SIZE = 200

private fun CatalogueTimestamp.toPair(): Pair<Long, String> = epochMillis to source.name
private fun entityTimestamp(epochMillis: Long, sourceName: String): CatalogueTimestamp =
    CatalogueTimestamp(epochMillis, CatalogueTimestampSource.valueOf(sourceName))

private fun CatalogueOverrideScope.toEntityKey(): Pair<String, String> = when (this) {
    is CatalogueOverrideScope.Item -> "ITEM" to productId
    is CatalogueOverrideScope.Branch -> "BRANCH" to branchId
    is CatalogueOverrideScope.StockGroup -> "STOCK_GROUP" to stockGroupKey
    CatalogueOverrideScope.CatalogueWide -> "CATALOGUE_WIDE" to ""
}

private fun CatalogueOverrideEntity.toDomain(): CatalogueOverrideRow = CatalogueOverrideRow(
    companyId = companyId,
    scope = when (scopeType) {
        "ITEM" -> CatalogueOverrideScope.Item(scopeKey)
        "BRANCH" -> CatalogueOverrideScope.Branch(scopeKey)
        "STOCK_GROUP" -> CatalogueOverrideScope.StockGroup(scopeKey)
        else -> CatalogueOverrideScope.CatalogueWide
    },
    attribute = CatalogueOverrideAttribute.entries.first { it.attributeName == attributeName },
    value = value,
    updatedAt = entityTimestamp(updatedAt, updatedAtSource),
)

private fun BranchEntity.toDomain(): Branch = Branch(
    companyId = companyId,
    branchId = branchId,
    name = name,
    isActive = isActive,
    createdAt = entityTimestamp(createdAt, createdAtSource),
    updatedAt = entityTimestamp(updatedAt, updatedAtSource),
)

private fun CataloguePublishedSnapshotEntity.toDomain(): CataloguePublishedSnapshot = CataloguePublishedSnapshot(
    companyId = companyId,
    productId = productId,
    displayName = displayName,
    description = description,
    specifications = specifications,
    customerFacingCategory = customerFacingCategory,
    priceDisplayMode = PriceDisplayMode.valueOf(priceDisplayMode),
    resolvedPriceAmount = resolvedPriceAmount,
    resolvedPriceCurrencyCode = resolvedPriceCurrencyCode,
    primaryAssetId = primaryAssetId,
    publishedAt = entityTimestamp(publishedAt, publishedAtSource),
)

private fun CatalogueAssetEntity.toDomain(): com.jajusri.venture.feature.catalogue.domain.model.CatalogueAsset =
    com.jajusri.venture.feature.catalogue.domain.model.CatalogueAsset(
        companyId = companyId,
        productId = productId,
        assetId = assetId,
        isPrimary = isPrimary,
        sortOrder = sortOrder,
        filePath = filePath,
        createdAt = entityTimestamp(createdAt, createdAtSource),
    )
