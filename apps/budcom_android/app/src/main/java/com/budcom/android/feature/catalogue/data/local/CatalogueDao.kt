package com.budcom.android.feature.catalogue.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CatalogueProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CatalogueProductEntity)

    @Query("SELECT * FROM catalogue_product WHERE companyId = :companyId AND productId = :productId")
    suspend fun findById(companyId: String, productId: String): CatalogueProductEntity?

    @Query("SELECT * FROM catalogue_product WHERE companyId = :companyId ORDER BY updatedAt DESC")
    suspend fun findAllForCompany(companyId: String): List<CatalogueProductEntity>

    @Query("SELECT * FROM catalogue_product WHERE companyId = :companyId AND lifecycleState = :lifecycleState ORDER BY updatedAt DESC")
    suspend fun findAllByLifecycleState(companyId: String, lifecycleState: String): List<CatalogueProductEntity>

    /** Reconciliation sweep support (architecture §6/§14): every product currently linked to a
     * Stock Item, for rename/disappearance/reappearance detection. */
    @Query("SELECT * FROM catalogue_product WHERE companyId = :companyId AND linkedStockItemId IS NOT NULL")
    suspend fun findAllLinkedToStockItems(companyId: String): List<CatalogueProductEntity>
}

@Dao
interface CatalogueProductSourceLinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CatalogueProductSourceLinkEntity)

    /** The critical identity-resolution lookup (mirrors
     * [com.budcom.android.feature.party.data.local.PartySourceLinkDao.findByExternalKey]): a
     * rename never changes [CatalogueProductSourceLinkEntity.externalStockItemId], so this lookup
     * is what guarantees a renamed Stock Item keeps its existing `productId`. */
    @Query(
        "SELECT * FROM catalogue_product_source_link " +
            "WHERE companyId = :companyId AND sourceType = :sourceType AND externalStockItemId = :externalStockItemId",
    )
    suspend fun findByExternalKey(companyId: String, sourceType: String, externalStockItemId: String): CatalogueProductSourceLinkEntity?

    @Query("SELECT * FROM catalogue_product_source_link WHERE companyId = :companyId AND productId = :productId")
    suspend fun findByProductId(companyId: String, productId: String): CatalogueProductSourceLinkEntity?

    @Query("SELECT * FROM catalogue_product_source_link WHERE companyId = :companyId")
    suspend fun findAllForCompany(companyId: String): List<CatalogueProductSourceLinkEntity>
}

@Dao
interface BranchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BranchEntity)

    @Query("SELECT * FROM catalogue_branch WHERE companyId = :companyId ORDER BY name COLLATE NOCASE ASC")
    suspend fun findAllForCompany(companyId: String): List<BranchEntity>

    @Query("SELECT * FROM catalogue_branch WHERE companyId = :companyId AND branchId = :branchId")
    suspend fun findById(companyId: String, branchId: String): BranchEntity?
}

@Dao
interface CatalogueOverrideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CatalogueOverrideEntity)

    @Query("SELECT * FROM catalogue_override WHERE companyId = :companyId AND attributeName = :attributeName")
    suspend fun findAllForCompanyAndAttribute(companyId: String, attributeName: String): List<CatalogueOverrideEntity>

    @Query(
        "DELETE FROM catalogue_override WHERE companyId = :companyId AND scopeType = :scopeType " +
            "AND scopeKey = :scopeKey AND attributeName = :attributeName",
    )
    suspend fun delete(companyId: String, scopeType: String, scopeKey: String, attributeName: String)
}

@Dao
interface CataloguePublishedSnapshotDao {
    /** Atomic overwrite (architecture §7) — a single REPLACE against the `(companyId, productId)`
     * primary key is already atomic in SQLite, so no explicit delete-then-insert is needed; the
     * `@Transaction` annotation only matters once a caller batches more than one row through this
     * DAO in the same logical Publish action. */
    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun publish(entity: CataloguePublishedSnapshotEntity)

    @Query("SELECT * FROM catalogue_published_snapshot WHERE companyId = :companyId AND productId = :productId")
    suspend fun findByProductId(companyId: String, productId: String): CataloguePublishedSnapshotEntity?

    /** Sharing (architecture §11) reads exclusively through this query (or
     * [findAllPublishedForCategory]) — a Draft/Review product has no row here at all. */
    @Query("SELECT * FROM catalogue_published_snapshot WHERE companyId = :companyId ORDER BY displayName COLLATE NOCASE ASC")
    suspend fun findAllPublishedForCompany(companyId: String): List<CataloguePublishedSnapshotEntity>

    @Query(
        "SELECT * FROM catalogue_published_snapshot WHERE companyId = :companyId AND customerFacingCategory = :category " +
            "ORDER BY displayName COLLATE NOCASE ASC",
    )
    suspend fun findAllPublishedForCategory(companyId: String, category: String): List<CataloguePublishedSnapshotEntity>

    @Query("DELETE FROM catalogue_published_snapshot WHERE companyId = :companyId AND productId = :productId")
    suspend fun deleteByProductId(companyId: String, productId: String)
}

@Dao
interface CatalogueAssetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CatalogueAssetEntity)

    @Query("SELECT * FROM catalogue_asset WHERE companyId = :companyId AND productId = :productId ORDER BY sortOrder ASC")
    suspend fun findAllForProduct(companyId: String, productId: String): List<CatalogueAssetEntity>

    @Query("SELECT * FROM catalogue_asset WHERE companyId = :companyId AND productId = :productId AND assetId = :assetId")
    suspend fun findById(companyId: String, productId: String, assetId: String): CatalogueAssetEntity?

    /** Two-phase primary-image replacement support (architecture §10): clear every other asset's
     * primary flag for this product before/independently of setting a new one, so at most one row
     * is ever primary without a window where two are. */
    @Query("UPDATE catalogue_asset SET isPrimary = 0 WHERE companyId = :companyId AND productId = :productId AND assetId != :keepAssetId")
    suspend fun clearPrimaryExcept(companyId: String, productId: String, keepAssetId: String)

    @Query("DELETE FROM catalogue_asset WHERE companyId = :companyId AND productId = :productId AND assetId = :assetId")
    suspend fun delete(companyId: String, productId: String, assetId: String)
}

@Dao
interface CatalogueCustomFieldDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CatalogueCustomFieldEntity)

    @Query("SELECT * FROM catalogue_custom_field WHERE companyId = :companyId AND productId = :productId")
    suspend fun findAllForProduct(companyId: String, productId: String): List<CatalogueCustomFieldEntity>

    /** Export support: every distinct custom column name ever used across the company, so an
     * export's header row is stable even for a product that happens not to have every column set. */
    @Query("SELECT DISTINCT columnName FROM catalogue_custom_field WHERE companyId = :companyId")
    suspend fun findAllColumnNamesForCompany(companyId: String): List<String>

    @Query("SELECT * FROM catalogue_custom_field WHERE companyId = :companyId")
    suspend fun findAllForCompany(companyId: String): List<CatalogueCustomFieldEntity>
}

@Dao
interface CatalogueSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CatalogueSettingsEntity)

    @Query("SELECT * FROM catalogue_settings WHERE companyId = :companyId")
    suspend fun findByCompany(companyId: String): CatalogueSettingsEntity?
}
