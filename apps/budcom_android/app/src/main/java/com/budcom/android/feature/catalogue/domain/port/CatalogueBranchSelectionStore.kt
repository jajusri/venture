package com.budcom.android.feature.catalogue.domain.port

import kotlinx.coroutines.flow.Flow

/**
 * Persists the owner's currently-selected Branch context for the Catalogue branch selector
 * (architecture §17: "A company-level branch selector, mirroring the existing company selector's
 * pattern"). Deliberately a small, standalone preference — not the full session/validation surface
 * [com.budcom.android.feature.company.domain.port.CompanySessionPort] provides for company
 * selection, since branch selection carries no server-side session semantics of its own.
 *
 * **Company isolation:** every method is `companyId`-scoped explicitly — selecting a branch for one
 * company must never be visible to, or overwrite, another company's own remembered selection
 * (mirrors this codebase's own standing convention, architecture §13).
 *
 * `null` means "no branch selected" (catalogue-wide default context) — the same meaning as
 * [com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideScope.CatalogueWide], never
 * conflated with "not yet loaded."
 */
interface CatalogueBranchSelectionStore {
    fun observeSelectedBranchId(companyId: String): Flow<String?>
    suspend fun setSelectedBranchId(companyId: String, branchId: String?)
}
