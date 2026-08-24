package com.budcom.android.feature.catalogue.domain.excel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueCsvFormatTest {

    @Test
    fun `parses a simple two-column two-row file`() {
        val result = CatalogueCsvFormat.parse("Product Name,Unit\r\nWidget,PCS\r\nGadget,BOX\r\n")
        assertEquals(listOf("Product Name", "Unit"), result.headers)
        assertEquals(2, result.rows.size)
        assertEquals("Widget", result.rows[0].value("Product Name"))
        assertEquals("PCS", result.rows[0].value("Unit"))
        assertEquals(1, result.rows[0].rowNumber)
        assertEquals("Gadget", result.rows[1].value("Product Name"))
        assertEquals(2, result.rows[1].rowNumber)
        assertTrue(result.duplicateHeaderWarnings.isEmpty())
    }

    @Test
    fun `tolerates bare LF line endings, not only CRLF`() {
        val result = CatalogueCsvFormat.parse("Product Name,Unit\nWidget,PCS\nGadget,BOX")
        assertEquals(2, result.rows.size)
        assertEquals("Widget", result.rows[0].value("Product Name"))
    }

    @Test
    fun `a value containing a comma round-trips correctly when quoted`() {
        val result = CatalogueCsvFormat.parse("Product Name,Description\r\n\"Widget, deluxe\",\"Fits A, B, and C\"\r\n")
        assertEquals("Widget, deluxe", result.rows[0].value("Product Name"))
        assertEquals("Fits A, B, and C", result.rows[0].value("Description"))
    }

    @Test
    fun `an embedded quote is unescaped from doubled quotes`() {
        val result = CatalogueCsvFormat.parse("Product Name\r\n\"18\"\" Widget\"\r\n")
        assertEquals("18\" Widget", result.rows[0].value("Product Name"))
    }

    @Test
    fun `an embedded newline inside a quoted field does not split the row`() {
        val result = CatalogueCsvFormat.parse("Product Name,Description\r\nWidget,\"Line one\nLine two\"\r\nGadget,Plain\r\n")
        assertEquals(2, result.rows.size)
        assertEquals("Line one\nLine two", result.rows[0].value("Description"))
        assertEquals("Gadget", result.rows[1].value("Product Name"))
    }

    @Test
    fun `unicode content is preserved exactly`() {
        val result = CatalogueCsvFormat.parse("Product Name,Description\r\nमसाला,आকर्षक मूल्य ₹500\r\n")
        assertEquals("मसाला", result.rows[0].value("Product Name"))
        assertEquals("आকर्षक मूल्य ₹500", result.rows[0].value("Description"))
    }

    @Test
    fun `a leading UTF-8 BOM is stripped before parsing`() {
        val bomPrefixed = "﻿Product Name,Unit\r\nWidget,PCS\r\n"
        val result = CatalogueCsvFormat.parse(bomPrefixed)
        assertEquals(listOf("Product Name", "Unit"), result.headers)
        assertEquals("Widget", result.rows[0].value("Product Name"))
    }

    @Test
    fun `very long cell values are preserved without truncation`() {
        val longValue = "A".repeat(20_000)
        val result = CatalogueCsvFormat.parse("Product Name,Description\r\nWidget,$longValue\r\n")
        assertEquals(20_000, result.rows[0].value("Description")?.length)
    }

    @Test
    fun `special characters survive round trip through write then parse`() {
        val original = mapOf(
            "Product Name" to "Comma, Quote\" and\nNewline",
            "Unit" to "PCS",
        )
        val csv = CatalogueCsvFormat.write(listOf("Product Name", "Unit"), listOf(original))
        val parsed = CatalogueCsvFormat.parse(csv)
        assertEquals("Comma, Quote\" and\nNewline", parsed.rows[0].value("Product Name"))
        assertEquals("PCS", parsed.rows[0].value("Unit"))
    }

    @Test
    fun `a duplicate header is flagged and only the first occurrence's values are kept`() {
        val result = CatalogueCsvFormat.parse("Product Name,Unit,Unit\r\nWidget,PCS,BOX\r\n")
        assertEquals(listOf("Unit"), result.duplicateHeaderWarnings)
        assertEquals(listOf("Product Name", "Unit"), result.headers)
        assertEquals("PCS", result.rows[0].value("Unit"))
    }

    @Test
    fun `duplicate header detection is case-insensitive`() {
        val result = CatalogueCsvFormat.parse("Product Name,unit,UNIT\r\nWidget,PCS,BOX\r\n")
        // Flagged using the *repeated* occurrence's own text ("UNIT") -- the first occurrence
        // ("unit") is the one that wins the column slot, per the class doc comment.
        assertEquals(listOf("UNIT"), result.duplicateHeaderWarnings)
    }

    @Test
    fun `an empty file parses to no headers and no rows`() {
        val result = CatalogueCsvFormat.parse("")
        assertTrue(result.headers.isEmpty())
        assertTrue(result.rows.isEmpty())
    }

    @Test
    fun `a header-only file parses to zero rows, not a phantom blank row`() {
        val result = CatalogueCsvFormat.parse("Product Name,Unit\r\n")
        assertEquals(listOf("Product Name", "Unit"), result.headers)
        assertTrue(result.rows.isEmpty())
    }

    @Test
    fun `blank lines between data rows are dropped, not treated as empty rows`() {
        val result = CatalogueCsvFormat.parse("Product Name,Unit\r\nWidget,PCS\r\n\r\nGadget,BOX\r\n")
        assertEquals(2, result.rows.size)
        assertEquals("Widget", result.rows[0].value("Product Name"))
        assertEquals("Gadget", result.rows[1].value("Product Name"))
    }

    @Test
    fun `a trailing blank line at end of file does not produce a phantom row`() {
        val result = CatalogueCsvFormat.parse("Product Name,Unit\r\nWidget,PCS\r\n\r\n")
        assertEquals(1, result.rows.size)
    }

    @Test
    fun `an empty cell is reported as absent (null), not an empty string`() {
        val result = CatalogueCsvFormat.parse("Product Name,Description\r\nWidget,\r\n")
        assertEquals(null, result.rows[0].value("Description"))
    }

    @Test
    fun `write quotes only fields that need it, leaving plain values unquoted`() {
        val csv = CatalogueCsvFormat.write(
            listOf("Product Name", "Unit"),
            listOf(mapOf("Product Name" to "Widget", "Unit" to "PCS")),
        )
        assertTrue("plain values should not be wrapped in quotes", csv.contains("Widget,PCS"))
        assertTrue("output must be BOM-prefixed for Excel Unicode detection", csv.startsWith("﻿"))
    }

    @Test
    fun `write emits a blank cell for a missing or null value`() {
        val csv = CatalogueCsvFormat.write(
            listOf("Product Name", "Description"),
            listOf(mapOf("Product Name" to "Widget", "Description" to null)),
        )
        val parsed = CatalogueCsvFormat.parse(csv)
        assertEquals(null, parsed.rows[0].value("Description"))
    }

    @Test
    fun `a full write-then-parse round trip reproduces every row exactly`() {
        val headers = listOf("Product Name", "Unit", "Description", "Price")
        val originalRows = listOf(
            mapOf("Product Name" to "Widget", "Unit" to "PCS", "Description" to "A, useful \"widget\"", "Price" to "499"),
            mapOf("Product Name" to "गैजेट", "Unit" to "BOX", "Description" to null, "Price" to "1,299"),
        )
        val csv = CatalogueCsvFormat.write(headers, originalRows)
        val parsed = CatalogueCsvFormat.parse(csv)

        assertEquals(headers, parsed.headers)
        assertEquals(2, parsed.rows.size)
        headers.forEach { header ->
            assertEquals(originalRows[0][header], parsed.rows[0].value(header))
            assertEquals(originalRows[1][header], parsed.rows[1].value(header))
        }
    }
}
