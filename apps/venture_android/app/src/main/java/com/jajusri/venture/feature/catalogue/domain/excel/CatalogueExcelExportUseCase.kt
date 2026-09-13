package com.jajusri.venture.feature.catalogue.domain.excel

import com.jajusri.venture.feature.catalogue.domain.model.CatalogueProduct
import com.jajusri.venture.feature.catalogue.domain.repository.CatalogueRepository
import javax.inject.Inject

/**
 * Produces the export side of the Excel contract (architecture §9): "round-trips every native +
 * custom column currently on each product, including current Publication State as a read-only
 * informational column." Every company's product, active or archived, is included — Archive is a
 * soft/reversible state (architecture §7), not a reason to silently drop a row from an export the
 * owner explicitly asked for.
 */
class CatalogueExcelExportUseCase @Inject constructor(
    private val repository: CatalogueRepository,
) {
    suspend fun export(companyId: String): CatalogueExcelExportResult {
        val products = repository.listProducts(companyId)
        val customColumnNames = repository.listAllCustomFieldColumnNames(companyId).sorted()
        val headers = CatalogueExcelColumns.NATIVE_EXPORT_ORDER + customColumnNames

        val rows = products.map { product ->
            val customValues = repository.listCustomFields(companyId, product.productId)
            product.toRow() + customValues
        }
        return CatalogueExcelExportResult(headers, rows)
    }

    private fun CatalogueProduct.toRow(): Map<String, String?> = mapOf(
        CatalogueExcelColumns.PRODUCT_NAME to displayName,
        CatalogueExcelColumns.UNIT to unit,
        CatalogueExcelColumns.SKU to sku,
        CatalogueExcelColumns.STOCK_ITEM_REFERENCE to linkedStockItemId,
        CatalogueExcelColumns.HSN to hsnCode,
        CatalogueExcelColumns.GST_RATE to gstRate,
        CatalogueExcelColumns.STOCK_GROUP to stockGroupKey,
        CatalogueExcelColumns.DESCRIPTION to description,
        CatalogueExcelColumns.SPECIFICATIONS to specifications,
        CatalogueExcelColumns.CATEGORY to customerFacingCategory,
        CatalogueExcelColumns.PRICE to manualPriceAmount,
        CatalogueExcelColumns.PRICE_DISPLAY_MODE to priceDisplayMode.name,
        CatalogueExcelColumns.PUBLICATION_STATE to lifecycleState.name,
    )
}

data class CatalogueExcelExportResult(
    val headers: List<String>,
    val rows: List<Map<String, String?>>,
) {
    /** File-format-agnostic — a caller hands this to [CatalogueCsvFormat.write] (or any future
     * file-format writer) to actually produce bytes/text. */
    fun toCsv(): String = CatalogueCsvFormat.write(headers, rows)
}
