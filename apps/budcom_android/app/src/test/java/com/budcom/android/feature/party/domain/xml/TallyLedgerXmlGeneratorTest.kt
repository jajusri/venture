package com.budcom.android.feature.party.domain.xml

import com.budcom.android.feature.party.domain.model.PartyFieldNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TallyLedgerXmlGeneratorTest {

    private fun request(
        companyName: String = "Acme Corp",
        ledgerName: String = "ABC Traders",
        ledgerGuid: String? = "guid-1234",
        fields: Map<String, String> = mapOf(PartyFieldNames.PRIMARY_EMAIL to "owner@example.com"),
    ) = TallyLedgerXmlGenerator.Request(companyName, ledgerName, ledgerGuid, fields)

    @Test
    fun `generates a well-formed IMPORTDATA envelope with the standard Tally import structure`() {
        val xml = TallyLedgerXmlGenerator.generate(request())

        assertTrue(xml.contains("<TALLYREQUEST>Import Data</TALLYREQUEST>"))
        assertTrue(xml.contains("<IMPORTDATA>"))
        assertTrue(xml.contains("<REQUESTDATA>"))
        assertTrue(xml.contains("<TALLYMESSAGE xmlns:UDF=\"TallyUDF\">"))
        assertTrue(xml.contains("<LEDGER NAME=\"ABC Traders\" ACTION=\"Alter\">"))
        assertTrue(xml.contains("<SVCURRENTCOMPANY>Acme Corp</SVCURRENTCOMPANY>"))
    }

    @Test
    fun `emits every selected field using its correct Tally tag name`() {
        val xml = TallyLedgerXmlGenerator.generate(
            request(
                fields = mapOf(
                    PartyFieldNames.PRIMARY_PHONE to "9876543210",
                    PartyFieldNames.PRIMARY_EMAIL to "owner@example.com",
                    PartyFieldNames.ADDRESS_LINE1 to "12 Market Road",
                    PartyFieldNames.ADDRESS_STATE to "Maharashtra",
                    PartyFieldNames.ADDRESS_PINCODE to "411001",
                    PartyFieldNames.GSTIN to "29ABCDE1234F1Z5",
                ),
            ),
        )

        assertTrue(xml.contains("<MOBILENUMBER>9876543210</MOBILENUMBER>"))
        assertTrue(xml.contains("<EMAIL>owner@example.com</EMAIL>"))
        assertTrue(xml.contains("<ADDRESS>12 Market Road</ADDRESS>"))
        assertTrue(xml.contains("<STATENAME>Maharashtra</STATENAME>"))
        assertTrue(xml.contains("<PINCODE>411001</PINCODE>"))
        assertTrue(xml.contains("<PARTYGSTIN>29ABCDE1234F1Z5</PARTYGSTIN>"))
    }

    @Test
    fun `never emits a city tag -- addressCity is not Tally-eligible`() {
        val xml = TallyLedgerXmlGenerator.generate(request())
        assertFalse(xml.contains("CITY"))
    }

    @Test
    fun `includes the GUID as an informational field, not as the Alter match key`() {
        val xml = TallyLedgerXmlGenerator.generate(request(ledgerGuid = "guid-abc-123"))
        assertTrue(xml.contains("<GUID>guid-abc-123</GUID>"))
        // The actual Tally match key is still NAME, per the documented ACTION="Alter" convention.
        assertTrue(xml.contains("NAME=\"ABC Traders\" ACTION=\"Alter\""))
    }

    @Test
    fun `omits the GUID field entirely when no GUID is known`() {
        val xml = TallyLedgerXmlGenerator.generate(request(ledgerGuid = null))
        assertFalse(xml.contains("<GUID>"))
    }

    @Test
    fun `escapes XML special characters in every value, never breaking well-formedness`() {
        val xml = TallyLedgerXmlGenerator.generate(
            request(
                companyName = "R&D Co <India>",
                ledgerName = "O'Brien & \"Sons\"",
                fields = mapOf(PartyFieldNames.ADDRESS_LINE1 to "5 <Tag> & \"Quote\" Street"),
            ),
        )

        assertTrue(xml.contains("R&amp;D Co &lt;India&gt;"))
        assertTrue(xml.contains("O&apos;Brien &amp; &quot;Sons&quot;"))
        assertTrue(xml.contains("5 &lt;Tag&gt; &amp; &quot;Quote&quot; Street"))
        assertFalse(xml.contains("<Tag>"))
        assertFalse(xml.contains("\"Quote\""))
    }

    @Test
    fun `handles unicode values without corrupting the envelope`() {
        val xml = TallyLedgerXmlGenerator.generate(
            request(ledgerName = "श्री ट्रेडर्स", fields = mapOf(PartyFieldNames.ADDRESS_LINE1 to "मुंबई, महाराष्ट्र")),
        )
        assertTrue(xml.contains("श्री ट्रेडर्स"))
        assertTrue(xml.contains("मुंबई, महाराष्ट्र"))
    }

    @Test
    fun `rejects a blank company name`() {
        var threw = false
        try {
            TallyLedgerXmlGenerator.generate(request(companyName = ""))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `rejects a blank ledger name`() {
        var threw = false
        try {
            TallyLedgerXmlGenerator.generate(request(ledgerName = "  "))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `rejects an empty field selection rather than emitting a no-op XML`() {
        var threw = false
        try {
            TallyLedgerXmlGenerator.generate(request(fields = emptyMap()))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `rejects a field not on the Tally-eligible whitelist`() {
        var threw = false
        try {
            TallyLedgerXmlGenerator.generate(request(fields = mapOf("addressCity" to "Pune")))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `rejects a blank field value rather than silently clearing a Tally field`() {
        var threw = false
        try {
            TallyLedgerXmlGenerator.generate(request(fields = mapOf(PartyFieldNames.PRIMARY_EMAIL to "   ")))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `field emission order is stable regardless of input map order`() {
        val xmlA = TallyLedgerXmlGenerator.generate(
            request(fields = linkedMapOf(PartyFieldNames.GSTIN to "29ABCDE1234F1Z5", PartyFieldNames.PRIMARY_EMAIL to "a@b.com")),
        )
        val xmlB = TallyLedgerXmlGenerator.generate(
            request(fields = linkedMapOf(PartyFieldNames.PRIMARY_EMAIL to "a@b.com", PartyFieldNames.GSTIN to "29ABCDE1234F1Z5")),
        )
        assertEquals(xmlA, xmlB)
    }
}
