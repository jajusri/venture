package com.budcom.android.feature.transaction.domain.model

/**
 * One product's deduplicated buying-history summary — every field is directly traceable to actual
 * completed-transaction line items, never fabricated or predicted (task's own Phase 3: "Do NOT
 * build AI recommendations... Use deterministic transaction history").
 */
data class BuyAgainEntry(
    val linkedProductId: String,
    val mostRecentSnapshotProductName: String,
    val mostRecentSnapshotUnit: String?,
    val mostRecentSnapshotSku: String?,
    val mostRecentQuantity: String,
    /** How many completed transactions have included this product — a plain count, not a score. */
    val purchaseCount: Int,
)

/**
 * Deduplicates a buyer's full completed-purchase-history line items
 * ([com.budcom.android.feature.transaction.domain.repository.TransactionRepository.findCompletedPurchaseHistory],
 * the already-implemented buying-history foundation) into one entry per product, most-recently-
 * purchased first — the concrete input Q3's "All previously bought products" priority tier reads
 * from. Pure function, no repository/Android dependency.
 */
object BuyAgainListBuilder {

    /** @param history as returned by `findCompletedPurchaseHistory` — oldest transaction's lines
     *   first (that repository method sorts by `acceptedAt` ascending before flattening). */
    fun build(history: List<TransactionLineItem>): List<BuyAgainEntry> {
        val byProduct = LinkedHashMap<String, MutableList<TransactionLineItem>>()
        // Walk newest-first so each product's *first* encountered occurrence is its most recent one.
        history.asReversed().forEach { line ->
            val productId = line.linkedProductId ?: return@forEach
            byProduct.getOrPut(productId) { mutableListOf() }.add(line)
        }
        return byProduct.map { (productId, lines) ->
            val mostRecent = lines.first()
            BuyAgainEntry(
                linkedProductId = productId,
                mostRecentSnapshotProductName = mostRecent.snapshotProductName,
                mostRecentSnapshotUnit = mostRecent.snapshotUnit,
                mostRecentSnapshotSku = mostRecent.snapshotSku,
                mostRecentQuantity = mostRecent.quantity,
                purchaseCount = lines.size,
            )
        }
    }
}
