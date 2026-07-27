package com.budcom.android.feature.masterdata.ledger.data.remote

import com.budcom.android.feature.masterdata.ledger.domain.model.AmountSide
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerDataQuality
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerDtoMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `deserializes confirmed ledger list payload`() {
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
                  "id": "guid:cash",
                  "name": "Cash",
                  "normalizedName": "cash",
                  "parentGroup": "Cash-in-Hand",
                  "status": "active",
                  "closingBalance": { "amount": "1000.00", "currencyCode": "INR", "side": "Dr" },
                  "balanceNature": "debit",
                  "guid": "aaaaaaaa-bbbb-cccc-111111111111",
                  "identitySource": "guid",
                  "dataQuality": "complete",
                  "isDeleted": false,
                  "syncedAt": "2026-07-27T09:00:00.000Z"
                }
              ],
              "pagination": { "page": 1, "pageSize": 50, "totalItems": 1, "totalPages": 1 }
            }
        """.trimIndent()

        val dto = json.decodeFromString(LedgerListResponseDto.serializer(), payload)
        val page = dto.toDomain()
        assertEquals(1, page.items.size)
        assertEquals("guid:cash", page.items[0].id)
        assertEquals("Cash", page.items[0].name)
        assertEquals("Cash-in-Hand", page.items[0].parentGroup)
        assertEquals(LedgerStatus.Active, page.items[0].status)
        assertEquals(LedgerDataQuality.Complete, page.items[0].dataQuality)
        assertEquals("1000.00", page.items[0].closingBalance?.amount)
        assertEquals(AmountSide.Dr, page.items[0].closingBalance?.side)
        assertEquals(1, page.totalPages)
    }

    @Test
    fun `filters deleted ledgers from domain page`() {
        val dto = LedgerListResponseDto(
            items = listOf(
                LedgerSummaryDto(
                    id = "guid:a",
                    name = "A",
                    status = "active",
                    dataQuality = "complete",
                    isDeleted = false,
                    syncedAt = "t",
                ),
                LedgerSummaryDto(
                    id = "guid:b",
                    name = "B",
                    status = "inactive",
                    dataQuality = "partial",
                    isDeleted = true,
                    syncedAt = "t",
                ),
            ),
            pagination = LedgerPaginationDto(1, 50, 2, 1),
        )
        val page = dto.toDomain()
        assertEquals(1, page.items.size)
        assertEquals("guid:a", page.items[0].id)
        assertNull(page.items[0].alias)
    }
}
