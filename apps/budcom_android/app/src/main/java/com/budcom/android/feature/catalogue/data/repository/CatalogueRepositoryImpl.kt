package com.budcom.android.feature.catalogue.data.repository

import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.catalogue.data.local.CATALOGUE_SOURCE_TYPE_TALLY_STOCK_ITEM
import com.budcom.android.feature.catalogue.data.local.BranchDao
import com.budcom.android.feature.catalogue.data.local.BranchEntity
import com.budcom.android.feature.catalogue.data.local.CatalogueAssetDao
import com.budcom.android.feature.catalogue.data.local.CatalogueOverrideDao
import com.budcom.android.feature.catalogue.data.local.CatalogueOverrideEntity
import com.budcom.android.feature.catalogue.data.local.CatalogueProductDao
import com.budcom.android.feature.catalogue.data.local.CatalogueProductEntity
import com.budcom.android.feature.catalogue.data.local.CatalogueProductSourceLinkDao
import com.budcom.android.feature.catalogue.data.local.CatalogueProductSourceLinkEntity
import com.budcom.android.feature.catalogue.data.local.CataloguePublishedSnapshotDao
import com.budcom.android.feature.catalogue.data.local.CataloguePublishedSnapshotEntity
import com.budcom.android.feature.catalogue.data.local.CatalogueSettingsDao
import com.budcom.android.feature.catalogue.data.local.CatalogueSettingsEntity
import com.budcom.android.feature.catalogue.domain.model.Branch
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleAction
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleState
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleTransitions
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideAttribute
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideResolver
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideRow
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideScope
import com.budcom.android.feature.catalogue.domain.model.CatalogueProduct
import com.budcom.android.feature.catalogue.domain.model.CatalogueProductSource
import com.budcom.android.feature.catalogue.domain.model.CataloguePublishedSnapshot
import com.budcom.android.feature.catalogue.domain.model.CatalogueTimestamp
import com.budcom.android.feature.catalogue.domain.model.CatalogueTimestampSource
import com.budcom.android.feature.catalogue.domain.model.PriceDisplayMode
import com.budcom.android.feature.catalogue.domain.repository.CatalogueEnrichmentUpdate
import com.budcom.android.feature.catalogue.domain.repository.CatalogueLifecycleResult
import com.budcom.android.feature.catalogue.domain.repository.CatalogueReconciliationResult
import com.budcom.android.feature.catalogue.domain.repository.CatalogueRepository
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemStatus
import com.budcom.android.feature.masterdata.stockitem.domain.port.StockItemLookupPort
import kotlinx.coroutines.withContext
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
    @Suppress("unused") private val assetDao: CatalogueAssetDao,
    private val stockItemLookup: StockItemLookupPort,
    private val dispatchers: DispatcherProvider,
) : CatalogueRepository {

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
        val productId = UUID.randomUUID().toString()
        val (epoch, source) = timestamp.toPair()
        val entity = CatalogueProductEntity(
            companyId = companyId,
            productId = productId,
            source = CatalogueProductSource.Tally.name,
            linkedStockItemId = stockItemId,
            sku = stockItem.partNumber,
            displayNameOverride = null,
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
        productDao.upsert(entity)
        sourceLinkDao.upsert(
            CatalogueProductSourceLinkEntity(
                companyId = companyId,
                sourceType = CATALOGUE_SOURCE_TYPE_TALLY_STOCK_ITEM,
                externalStockItemId = stockItemId,
                productId = productId,
                lastConfirmedAt = epoch,
                lastConfirmedAtSource = source,
            ),
        )
        toDomain(companyId, entity, stockItem)
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
        productDao.upsert(entity)
        toDomain(companyId, entity)
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
            description = update.description ?: existing.description,
            specifications = update.specifications ?: existing.specifications,
            customerFacingCategory = update.customerFacingCategory ?: existing.customerFacingCategory,
            priceDisplayMode = update.priceDisplayMode?.name ?: existing.priceDisplayMode,
            manualPriceAmount = update.manualPriceAmount ?: existing.manualPriceAmount,
            manualPriceCurrencyCode = update.manualPriceCurrencyCode ?: existing.manualPriceCurrencyCode,
            updatedAt = epoch,
            updatedAtSource = source,
        )
        productDao.upsert(updated)
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
        productDao.upsert(updated)

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
            productDao.findAllLinkedToStockItems(companyId).forEach { product ->
                val stockItemId = product.linkedStockItemId ?: return@forEach
                val stockItem = stockItemLookup.findById(companyId, stockItemId)
                val isAvailable = stockItem != null && stockItem.status != StockItemStatus.Inactive
                if (isAvailable != product.sourceAvailable) {
                    productDao.upsert(product.copy(sourceAvailable = isAvailable, updatedAt = epoch, updatedAtSource = source))
                    if (isAvailable) reappeared += product.productId else nowUnavailable += product.productId
                }
            }
            CatalogueReconciliationResult(nowUnavailable, reappeared)
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
            unit = stockItem?.baseUnit,
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
