package com.jajusri.venture.feature.catalogue.storage

import android.net.Uri

/**
 * Multi-image asset storage for Catalogue (architecture §10) — a second *instance* of the same
 * proven pattern [com.jajusri.venture.feature.businessprofile.storage.BusinessProfileLogoStore]
 * establishes (allowlist, size cap, path-containment, deterministic ownership, safe two-phase
 * replace/delete), not a new mechanism. Unlike that store (structurally single-image-per-company),
 * this one is multi-image-per-product by identity: `(companyId, productId, assetId)`.
 */
interface CatalogueAssetStore {
    /** Validates and copies [sourceUri]'s bytes into durable, app-private storage, returning the
     * generated [assetId] (never derived from the original filename) and the stable relative path
     * to persist in [com.jajusri.venture.feature.catalogue.data.local.CatalogueAssetEntity.filePath].
     * Never throws — returns a specific, honest [CatalogueAssetResult.Failure] instead. */
    suspend fun saveAsset(companyId: String, productId: String, sourceUri: Uri): CatalogueAssetResult

    /** Resolves a persisted [filePath] to a readable [java.io.File], or `null` if missing/unreadable
     * or outside this store's own managed directory (defensive path-containment check, mirroring
     * `BusinessProfileLogoStore.resolveLogoFile`). */
    fun resolveAssetFile(companyId: String, productId: String, filePath: String?): java.io.File?

    /** Best-effort delete — safe to call even if the file no longer exists. */
    suspend fun deleteAsset(companyId: String, productId: String, filePath: String)
}

sealed interface CatalogueAssetResult {
    data class Success(val assetId: String, val filePath: String) : CatalogueAssetResult
    data class Failure(val reason: CatalogueAssetFailureReason) : CatalogueAssetResult
}

enum class CatalogueAssetFailureReason {
    UnsupportedFileType,
    FileTooLarge,
    UnreadableSource,
    StorageError,
}
