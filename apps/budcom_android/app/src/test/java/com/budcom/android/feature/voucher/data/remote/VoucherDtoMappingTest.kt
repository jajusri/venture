package com.budcom.android.feature.voucher.data.remote

import com.budcom.android.feature.voucher.domain.model.VoucherMoneySide
import com.budcom.android.feature.voucher.domain.model.VoucherSort
import com.budcom.android.feature.voucher.domain.model.VoucherSortDirection
import com.budcom.android.feature.voucher.domain.model.VoucherSortField
import com.budcom.android.feature.voucher.domain.model.VoucherStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoucherDtoMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `deserializes confirmed voucher list envelope`() {
        val payload = """
            {
              "schemaVersion": "1.0.0",
              "data": {
                "companyId": "estimation",
                "items": [
                  {
                    "id": "v-1",
                    "date": "2026-07-27",
                    "type": "Sales",
                    "number": "S-1",
                    "partyName": "Acme",
                    "referenceNumber": "R-1",
                    "amount": { "value": "100.00", "side": "debit" },
                    "status": "active",
                    "dataQuality": "complete"
                  }
                ],
                "pagination": { "page": 1, "pageSize": 50, "totalItems": 1, "totalPages": 1 }
              }
            }
        """.trimIndent()

        val page = json.decodeFromString(VoucherListEnvelopeDto.serializer(), payload).toDomain()
        assertEquals("estimation", page.companyId)
        assertEquals(1, page.items.size)
        assertEquals("v-1", page.items[0].identity.id)
        assertEquals("Sales", page.items[0].type)
        assertEquals("S-1", page.items[0].number)
        assertEquals(VoucherStatus.Active, page.items[0].status)
        assertEquals("100.00", page.items[0].amount?.value)
        assertEquals(VoucherMoneySide.Debit, page.items[0].amount?.side)
    }

    @Test
    fun `deserializes confirmed voucher details envelope`() {
        val payload = """
            {
              "schemaVersion": "1.0.0",
              "data": {
                "companyId": "estimation",
                "voucher": {
                  "id": "v-1",
                  "date": "2026-07-27",
                  "type": "Sales",
                  "number": "S-1",
                  "partyName": "Acme",
                  "referenceNumber": "R-1",
                  "amount": { "value": "100.00", "side": "debit" },
                  "status": "active",
                  "dataQuality": "complete",
                  "effectiveDate": "2026-07-27",
                  "narration": "Narration",
                  "ledgerEntries": [
                    {
                      "lineNumber": 1,
                      "ledgerName": "Cash",
                      "amount": { "value": "100.00", "side": "debit" },
                      "isDeemedPositive": true
                    }
                  ],
                  "inventoryEntries": [
                    {
                      "lineNumber": 1,
                      "itemName": "Widget",
                      "quantity": "2 Nos",
                      "amount": { "value": "50.00", "side": "debit" }
                    }
                  ]
                }
              }
            }
        """.trimIndent()
        val envelope = json.decodeFromString(VoucherDetailsEnvelopeDto.serializer(), payload)
        val details = envelope.data.voucher!!.toDomain()
        assertEquals("v-1", details.summary.identity.id)
        assertEquals("Narration", details.narration)
        assertEquals(1, details.ledgerEntries.size)
        assertEquals("Cash", details.ledgerEntries[0].ledgerName)
        assertEquals(1, details.inventoryEntries.size)
        assertEquals("2 Nos", details.inventoryEntries[0].quantity)
    }

    @Test
    fun `maps details and null amount side`() {
        val dto = VoucherPublicDetailsDto(
            id = "v-2",
            date = "2026-07-27",
            type = "Payment",
            number = null,
            partyName = null,
            referenceNumber = null,
            amount = VoucherAmountDto(value = "10.00", side = null),
            status = "cancelled",
            dataQuality = "incomplete",
            effectiveDate = "2026-07-27",
            narration = "Paid",
            ledgerEntries = listOf(
                VoucherLedgerEntryDto(
                    lineNumber = 1,
                    ledgerName = "Cash",
                    amount = VoucherAmountDto("10.00", "credit"),
                    isDeemedPositive = true,
                ),
            ),
            inventoryEntries = emptyList(),
        )
        val details = dto.toDomain()
        assertEquals(VoucherStatus.Cancelled, details.summary.status)
        assertNull(details.summary.amount?.side)
        assertEquals(1, details.ledgerEntries.size)
        assertEquals("Cash", details.ledgerEntries[0].ledgerName)
    }

    @Test
    fun `encodes sort param for connector`() {
        assertEquals(
            "date",
            VoucherSort(VoucherSortField.Date, VoucherSortDirection.Asc).toApiSortParam(),
        )
        assertEquals(
            "-voucherNumber",
            VoucherSort(VoucherSortField.VoucherNumber, VoucherSortDirection.Desc).toApiSortParam(),
        )
    }
}
