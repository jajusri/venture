package com.budcom.android.feature.catalogue.sharing

import com.budcom.android.feature.catalogue.domain.repository.CatalogueRepository

/**
 * The safety-critical part of [CatalogueShareCoordinator.prepareShare], deliberately factored out
 * from [AndroidCatalogueShareCoordinator] so it carries no Android/Context dependency: the
 * Private-catalogue refusal (architecture §18 — structural, checked before any product content is
 * read) and the Published-only content read (architecture §7/§11 — exclusively
 * [CatalogueRepository.listAllPublished]/`listPublishedForCategory`, both of which only ever
 * return the atomic published snapshot, so a Draft/Review product has no path here) are exactly
 * the properties this project's adversarial test discipline requires proving, and both are
 * ordinary suspend/repository logic that needs no Android test runtime to verify.
 */
object CatalogueShareContent {
    suspend fun resolvePayload(
        repository: CatalogueRepository,
        companyId: String,
        scope: CatalogueShareScope,
        businessName: String?,
    ): CatalogueShareResult<CatalogueSharePayload> {
        if (!repository.isPublic(companyId)) {
            return CatalogueShareResult.Failure(
                "This catalogue is private and cannot be shared. Make it public in Catalogue settings first.",
            )
        }
        val snapshots = when (scope) {
            CatalogueShareScope.FullCatalogue -> repository.listAllPublished(companyId)
            is CatalogueShareScope.Category -> repository.listPublishedForCategory(companyId, scope.name)
        }
        if (snapshots.isEmpty()) return CatalogueShareResult.Failure("No published products to share yet.")
        return CatalogueShareResult.Success(CatalogueSharePayload(companyId, businessName, scope, snapshots))
    }

    suspend fun resolve(
        repository: CatalogueRepository,
        companyId: String,
        scope: CatalogueShareScope,
        businessName: String?,
    ): CatalogueShareResult<String> {
        return when (val payload = resolvePayload(repository, companyId, scope, businessName)) {
            is CatalogueShareResult.Failure -> payload
            is CatalogueShareResult.Success -> CatalogueShareResult.Success(
                CatalogueShareTextRenderer.render(payload.value.businessName, payload.value.scope, payload.value.snapshots),
            )
        }
    }
}

data class CatalogueSharePayload(
    val companyId: String,
    val businessName: String?,
    val scope: CatalogueShareScope,
    val snapshots: List<com.budcom.android.feature.catalogue.domain.model.CataloguePublishedSnapshot>,
)
