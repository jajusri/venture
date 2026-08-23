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

    @Test
    fun `deserializes a ledger detail payload with mailing, contact and gst`() {
        val payload = """
            {
              "schemaVersion": "1.0.0",
              "dataFreshnessAt": "2026-08-17T10:00:00.000Z",
              "ledger": {
                "id": "guid:abc",
                "name": "ABC Traders",
                "guid": "aaaaaaaa-bbbb-cccc-111111111111",
                "mailing": { "mailingName": "ABC Traders", "address": "12 Market Road", "state": "Maharashtra", "country": "India", "pincode": "411001" },
                "contact": { "email": "owner@example.com", "phone": "020-12345", "mobile": "9876543210" },
                "gst": { "gstin": "29ABCDE1234F1Z5", "registrationType": "Regular", "applicableFrom": "2020-01-01" }
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString(LedgerDetailEnvelopeDto.serializer(), payload)
        val details = dto.ledger.toContactDetails()

        assertEquals("guid:abc", details.ledgerId)
        assertEquals("9876543210", details.mobile)
        assertEquals("owner@example.com", details.email)
        assertEquals("12 Market Road", details.address)
        assertEquals("Maharashtra", details.state)
        assertEquals("411001", details.pincode)
        assertEquals("29ABCDE1234F1Z5", details.gstin)
    }

    @Test
    fun `ignores unrelated fields on the detail payload`() {
        val payload = """
            {
              "ledger": {
                "id": "guid:abc",
                "name": "ABC Traders",
                "status": "active",
                "closingBalance": { "amount": "1000.00", "currencyCode": "INR", "side": "Dr" },
                "isBillWiseOn": true
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString(LedgerDetailEnvelopeDto.serializer(), payload)
        val details = dto.ledger.toContactDetails()

        assertEquals("guid:abc", details.ledgerId)
        assertNull(details.mobile)
        assertNull(details.gstin)
    }

    @Test
    fun `deserializes a bulk contact-details response into one item per ledger`() {
        val payload = """
            {
              "schemaVersion": "1.0.0",
              "requestedAt": "2026-08-24T10:00:00.000Z",
              "durationMs": 42,
              "ledgerCount": 2,
              "updatedCount": 1,
              "skippedCount": 1,
              "items": [
                {
                  "ledgerId": "guid:abc",
                  "mobile": "9876543210",
                  "email": "accounts@acme.example",
                  "address": "123 MG Road",
                  "state": "Karnataka",
                  "pincode": "560001",
                  "gstin": "29AABCU9603R1ZM"
                },
                {
                  "ledgerId": "guid:def"
                }
              ]
            }
        """.trimIndent()

        val dto = json.decodeFromString(LedgerContactDetailsBulkResponseDto.serializer(), payload)
        val result = dto.toDomain()

        assertEquals(2, result.ledgerCount)
        assertEquals(42, result.durationMs)
        assertEquals(2, result.items.size)
        assertEquals("guid:abc", result.items[0].ledgerId)
        assertEquals("accounts@acme.example", result.items[0].email)
        assertEquals("29AABCU9603R1ZM", result.items[0].gstin)
        assertEquals("guid:def", result.items[1].ledgerId)
        assertNull(result.items[1].email)
    }

    @Test
    fun `bulk contact-details blank strings normalize to null, same as the single-ledger path`() {
        val dto = LedgerContactDetailsBulkResponseDto(
            items = listOf(
                LedgerContactDetailsBulkItemDto(ledgerId = "guid:abc", email = "", mobile = "  ", gstin = ""),
            ),
        )
        val result = dto.toDomain()

        assertNull(result.items.single().email)
        assertNull(result.items.single().mobile)
        assertNull(result.items.single().gstin)
    }

    @Test
    fun `blank contact-field strings are normalized to null, never stored as empty`() {
        val payload = """
            {
              "ledger": {
                "id": "guid:abc",
                "name": "ABC Traders",
                "contact": { "email": "", "mobile": "  " },
                "gst": { "gstin": "" }
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString(LedgerDetailEnvelopeDto.serializer(), payload)
        val details = dto.ledger.toContactDetails()

        assertNull(details.email)
        assertNull(details.mobile)
        assertNull(details.gstin)
    }
}
