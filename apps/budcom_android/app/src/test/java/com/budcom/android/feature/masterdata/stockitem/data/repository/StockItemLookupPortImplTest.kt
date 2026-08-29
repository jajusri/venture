package com.budcom.android.feature.masterdata.stockitem.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.masterdata.domain.model.MasterDataPagination
import com.budcom.android.feature.masterdata.stockitem.data.local.StockItemLocalDataSource
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemDataQuality
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemPage
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemQuery
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemStatus
import com.budcom.android.feature.masterdata.stockitem.domain.repository.StockItemRepository
import com.budcom.android.feature.masterdata.stockitem.domain.usecase.LoadStockItemsUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * TD-050: [StockItemLookupPort.warmStockItemCache][com.budcom.android.feature.masterdata.stockitem.domain.port.StockItemLookupPort.warmStockItemCache]
 * is the one exception to this port's local-cache-only rule. These tests prove it genuinely
 * reuses [LoadStockItemsUseCase] (the same pull the Stock Items browser performs) rather than
 * duplicating [StockItemRepositoryImpl]'s fetch/persist logic, and that a fetch failure never
 * propagates to the caller.
 */
class StockItemLookupPortImplTest {

    private fun port(repository: StockItemRepository, local: StockItemLocalDataSource = FakeLocal()) =
        StockItemLookupPortImpl(local, LoadStockItemsUseCase(repository))

    @Test
    fun `warmStockItemCache triggers an unfiltered page-1 load, the query shape that drives a full snapshot warm`() = runTest {
        val repository = RecordingStockItemRepository()

        port(repository).warmStockItemCache("co-1")

        assertEquals(1, repository.queries.size)
        val query = repository.queries.single()
        assertNull("an unfiltered query is required to trigger warmFullSnapshot, not a single filtered page", query.text)
        assertEquals(1, query.page)
    }

    @Test
    fun `a failed fetch is swallowed -- it never propagates to the caller`() = runTest {
        val repository = RecordingStockItemRepository(result = AppResult.Failure(AppError.Offline()))

        // Must simply return, not throw, exactly like every other degrade-to-cache path.
        port(repository).warmStockItemCache("co-1")

        assertEquals(1, repository.queries.size)
    }

    @Test
    fun `findById and listAllForCompany still read only from the local cache, never triggering a fetch`() = runTest {
        val repository = RecordingStockItemRepository()
        val local = FakeLocal().apply {
            stored.getOrPut("co-1") { mutableMapOf() }["guid:a"] = stockItem("guid:a")
        }

        val found = port(repository, local).findById("co-1", "guid:a")
        val all = port(repository, local).listAllForCompany("co-1")

        assertEquals("guid:a", found?.id)
        assertEquals(1, all.size)
        assertEquals("no lookup call may trigger a network fetch", 0, repository.queries.size)
    }
}

private fun stockItem(id: String) = StockItem(
    id = id, name = "Item $id", alias = null, parentGroup = null, category = null, baseUnit = null,
    partNumber = null, hsnCode = null, gstRate = null, status = StockItemStatus.Active, closingBalance = null,
    dataQuality = StockItemDataQuality.Complete, syncedAt = "t",
)

private class RecordingStockItemRepository(
    private val result: AppResult<StockItemPage> = AppResult.Success(
        StockItemPage(
            items = emptyList(),
            pagination = MasterDataPagination(page = 1, pageSize = 50, totalItems = 0, totalPages = 0),
            dataFreshnessAt = null,
        ),
    ),
) : StockItemRepository {
    val queries = mutableListOf<StockItemQuery>()
    override suspend fun loadStockItems(query: StockItemQuery): AppResult<StockItemPage> {
        queries += query
        return result
    }
}

private class FakeLocal : StockItemLocalDataSource {
    val stored = mutableMapOf<String, MutableMap<String, StockItem>>()
    override suspend fun hasCache(companyId: String): Boolean = stored[companyId]?.isNotEmpty() == true
    override suspend fun upsert(companyId: String, items: List<StockItem>, dataFreshnessAt: String?) {
        val map = stored.getOrPut(companyId) { mutableMapOf() }
        items.forEach { map[it.id] = it }
    }
    override suspend fun replaceAll(companyId: String, items: List<StockItem>, dataFreshnessAt: String?) {
        stored[companyId] = items.associateBy { it.id }.toMutableMap()
    }
    override suspend fun query(companyId: String, query: StockItemQuery): StockItemPage? = null
    override suspend fun findById(companyId: String, id: String): StockItem? = stored[companyId]?.get(id)
    override suspend fun listAllForCompany(companyId: String): List<StockItem> = stored[companyId]?.values?.toList().orEmpty()
    override suspend fun findByIds(companyId: String, ids: List<String>): List<StockItem> =
        ids.mapNotNull { stored[companyId]?.get(it) }
    override suspend fun freshnessFingerprint(companyId: String): String =
        "${stored[companyId]?.size ?: 0}:${stored[companyId]?.values?.maxOfOrNull { it.syncedAt } ?: ""}"
}
