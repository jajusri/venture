package com.budcom.android.feature.catalogue.sharing

import android.content.Intent
import android.net.Uri

/** LOCKED sharing scope (architecture §11, Brainstorm Outcome §5): category-level and
 * full-catalogue only. Item-level sharing is deliberately not represented here. */
sealed interface CatalogueShareScope {
    data object FullCatalogue : CatalogueShareScope
    data class Category(val name: String) : CatalogueShareScope
}

sealed interface CatalogueShareResult<out T> {
    data class Success<T>(val value: T) : CatalogueShareResult<T>
    data class Failure(val message: String) : CatalogueShareResult<Nothing>
}

data class PreparedCatalogueShare(
    val contentUri: String,
    val cacheFilePath: String,
    val suggestedFilename: String,
)

/**
 * Generates and shares a controlled, generated representation of the current Published catalogue
 * scope (architecture §11) — reusing the existing Android `Intent.ACTION_SEND` pattern already
 * proven for Ledger statements and Voucher PDFs, never a new QR/deep-link/web-hosted mechanism.
 *
 * **Structural safety, not a UI-layer hint (architecture §18):** [prepareShare] must refuse before
 * reading any product content at all when the catalogue is Private, and must read exclusively from
 * [com.budcom.android.feature.catalogue.domain.repository.CatalogueRepository.listAllPublished] /
 * `listPublishedForCategory` — both of which only ever return the atomic "current published
 * snapshot" (architecture §7) — so a Draft/Review product has no code path that can reach a
 * generated share file.
 */
interface CatalogueShareCoordinator {
    suspend fun prepareShare(companyId: String, scope: CatalogueShareScope, businessName: String?): CatalogueShareResult<PreparedCatalogueShare>
    fun createShareIntent(prepared: PreparedCatalogueShare): CatalogueShareResult<Intent>
    suspend fun savePdf(prepared: PreparedCatalogueShare, destination: Uri): CatalogueShareResult<Unit> =
        CatalogueShareResult.Failure("Catalogue PDF could not be saved. Please choose another location.")
    fun releaseShare(prepared: PreparedCatalogueShare)
}
