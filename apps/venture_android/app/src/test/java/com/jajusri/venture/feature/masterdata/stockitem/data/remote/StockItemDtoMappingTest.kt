package com.jajusri.venture.feature.masterdata.stockitem.data.remote

import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockAmountSide
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemDataQuality
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StockItemDtoMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `deserializes confirmed stock item list payload`() {
        val payload = """
            {
              "schemaVersion": "1.0.0",
              "dataFreshnessAt": "2026-07-27T10:00:00.000Z",
              "storage": {
                "backend": "sqlite",
                "schemaVersion": "1",
                "databaseHealthy": true,
                "migrationStatus": "up_to_date",
                "message": null
              },
              "items": [
                {
                  "id": "guid:widget",
                  "name": "Widget",
                  "normalizedName": "widget",
                  "parentGroup": "Primary",
                  "category": "Finished",
                  "baseUnit": "Nos",
                  "dataQuality": "complete",
                  "closingBalance": { "amount": "12.00", "currencyCode": "INR", "side": "Dr" },
                  "hsnCode": "1234",
                  "gstRate": "18",
                  "guid": "aaaaaaaa-bbbb-cccc-222222222222",
                  "alias": "W1",
                  "partNumber": "P-1",
                  "status": "active",
                  "sourceSystem": "tally",
                  "isDeleted": false,
                  "syncedAt": "2026-07-27T09:00:00.000Z"
                }
              ],
              "pagination": { "page": 1, "pageSize": 50, "totalItems": 1, "totalPages": 1 }
            }
        """.trimIndent()

        val dto = json.decodeFromString(StockItemListResponseDto.serializer(), payload)
        val page = dto.toDomain()
        assertEquals(1, page.items.size)
        assertEquals("guid:widget", page.items[0].id)
        assertEquals("Widget", page.items[0].name)
        assertEquals("Primary", page.items[0].parentGroup)
        assertEquals("Finished", page.items[0].category)
        assertEquals("Nos", page.items[0].baseUnit)
        assertEquals(StockItemStatus.Active, page.items[0].status)
        assertEquals(StockItemDataQuality.Complete, page.items[0].dataQuality)
        assertEquals("12.00", page.items[0].closingBalance?.amount)
        assertEquals(StockAmountSide.Dr, page.items[0].closingBalance?.side)
        assertEquals("1234", page.items[0].hsnCode)
        assertEquals(1, page.pagination.totalPages)
    }

    @Test
    fun `filters deleted stock items from domain page`() {
        val dto = StockItemListResponseDto(
            items = listOf(
                StockItemSummaryDto(
                    id = "guid:a",
                    name = "A",
                    dataQuality = "complete",
                    status = "active",
                    isDeleted = false,
                    syncedAt = "t",
                ),
                StockItemSummaryDto(
                    id = "guid:b",
                    name = "B",
                    dataQuality = "incomplete",
                    status = "inactive",
                    isDeleted = true,
                    syncedAt = "t",
                ),
            ),
            pagination = StockItemPaginationDto(1, 50, 2, 1),
        )
        val page = dto.toDomain()
        assertEquals(1, page.items.size)
        assertEquals("guid:a", page.items[0].id)
        assertNull(page.items[0].alias)
    }
}
