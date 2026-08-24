package com.budcom.android.feature.catalogue.domain.excel

import com.budcom.android.feature.catalogue.domain.model.PriceDisplayMode
import com.budcom.android.feature.catalogue.domain.port.CatalogueClock
import com.budcom.android.feature.catalogue.domain.repository.CatalogueEnrichmentUpdate
import com.budcom.android.feature.catalogue.domain.repository.CatalogueRepository
import javax.inject.Inject

/**
 * Commits an already-previewed (never blind) [CatalogueExcelImportPreview] (architecture §9:
 * "Import preview: mandatory step before commit... never a silent bulk-apply"). A [Create] row
 * always lands as a Manual Draft (never auto-Published, matching §7); an [Update] row applies
 * enrichment only — Tally-owned fields are never writable via Excel (architecture §6/§9), and
 * lifecycle/publication state is never touched here (§9: "Publish/Archive transitions happen only
 * through the lifecycle UI, never via Excel re-import").
 */
class CatalogueExcelImportUseCase @Inject constructor(
    private val repository: CatalogueRepository,
    private val clock: CatalogueClock,
) {
    suspend fun commit(companyId: String, rows: List<CatalogueExcelRow>, preview: CatalogueExcelImportPreview): CatalogueExcelCommitResult {
        val rowsByNumber = rows.associateBy { it.rowNumber }
        var created = 0
        var updated = 0
        preview.outcomes.forEach { outcome ->
            val row = rowsByNumber[outcome.rowNumber] ?: return@forEach
            when (outcome) {
                is CatalogueExcelRowOutcome.Create -> {
                    val timestamp = clock.now()
                    val product = repository.createManualDraft(companyId, outcome.productName, timestamp)
                    repository.updateEnrichment(companyId, product.productId, row.toEnrichmentUpdate(), timestamp)
                    created++
                }
                is CatalogueExcelRowOutcome.Update -> {
                    repository.updateEnrichment(companyId, outcome.productId, row.toEnrichmentUpdate(), clock.now())
                    updated++
                }
                is CatalogueExcelRowOutcome.Skipped -> Unit
            }
        }
        return CatalogueExcelCommitResult(created, updated, preview.skipCount)
    }

    private fun CatalogueExcelRow.toEnrichmentUpdate(): CatalogueEnrichmentUpdate = CatalogueEnrichmentUpdate(
        description = value(CatalogueExcelColumns.DESCRIPTION),
        specifications = value(CatalogueExcelColumns.SPECIFICATIONS),
        customerFacingCategory = value(CatalogueExcelColumns.CATEGORY),
        priceDisplayMode = value(CatalogueExcelColumns.PRICE_DISPLAY_MODE)?.let {
            if (it.equals("Open", ignoreCase = true)) PriceDisplayMode.Open else PriceDisplayMode.ContactForPrice
        },
        manualPriceAmount = value(CatalogueExcelColumns.PRICE),
    )
}

data class CatalogueExcelCommitResult(val created: Int, val updated: Int, val skipped: Int)
