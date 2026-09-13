package com.jajusri.venture.feature.catalogue.domain.excel

/**
 * The one file-format implementation the Excel contract's own architecture doc comment
 * deliberately deferred ([CatalogueExcelColumns]'s doc comment: "does not pick, or depend on, any
 * specific spreadsheet file-format library... a real dependency decision this pass does not make
 * unilaterally"). CSV is chosen over a binary `.xlsx` reader/writer specifically to avoid that
 * unreviewed third-party-dependency decision: Excel (and every other spreadsheet app a small
 * business owner is likely to use) opens, edits, and re-saves CSV natively, so this is the smallest
 * architecture-consistent solution that satisfies "Excel export/import bridges both platforms"
 * (Brainstorm Outcome §6) without adding a new dependency at all — pure Kotlin, RFC 4126 (RFC 4180)
 * quoting rules, no external library.
 *
 * Downstream of [parse]/[write], every locked rule ([CatalogueExcelColumns], [CatalogueExcelValidator],
 * [CatalogueExcelImportUseCase]) is completely format-agnostic and untouched by this file.
 */
object CatalogueCsvFormat {

    private const val BOM = '\uFEFF'

    /**
     * Parses [text] into an ordered header list and [CatalogueExcelRow]s. Lenient on input line
     * endings (`\r\n` or bare `\n`), a leading UTF-8 BOM (stripped if present, so a file exported
     * by Excel itself parses cleanly), and a wholly-blank line (dropped, not synthesized into a
     * phantom all-empty row — a blank line carries no data under any spreadsheet-editing
     * convention). Quoted fields may contain embedded commas, quotes (as `""`), and newlines,
     * per RFC 4180.
     *
     * A header appearing more than once (case-insensitively) is reported in
     * [CatalogueCsvParseResult.duplicateHeaderWarnings] rather than silently dropping data — only
     * the *first* occurrence's column is kept per row (a `Map<String, String?>` cannot hold two
     * values for one key), and the warning is surfaced so an import preview can show the owner
     * exactly what happened instead of quietly losing a column's values.
     */
    fun parse(text: String): CatalogueCsvParseResult {
        val withoutBom = if (text.isNotEmpty() && text[0] == BOM) text.substring(1) else text
        val records = tokenize(withoutBom).filterNot { record -> record.all { it.isEmpty() } }
        if (records.isEmpty()) return CatalogueCsvParseResult(headers = emptyList(), rows = emptyList(), duplicateHeaderWarnings = emptyList())

        val rawHeaders = records.first()
        val seen = mutableSetOf<String>()
        val duplicates = mutableSetOf<String>()
        rawHeaders.forEach { header ->
            val key = header.trim().lowercase()
            if (!seen.add(key)) duplicates.add(header.trim())
        }
        // First occurrence of each (case-insensitive) header name wins the column slot.
        val firstIndexByKey = mutableMapOf<String, Int>()
        rawHeaders.forEachIndexed { index, header ->
            val key = header.trim().lowercase()
            if (!firstIndexByKey.containsKey(key)) firstIndexByKey[key] = index
        }
        val orderedHeaders = firstIndexByKey.values.sorted().map { rawHeaders[it].trim() }

        val rows = records.drop(1).mapIndexed { i, record ->
            val cells = linkedMapOf<String, String?>()
            firstIndexByKey.forEach { (key, index) ->
                val header = rawHeaders[index].trim()
                val value = record.getOrNull(index)?.takeIf { it.isNotEmpty() }
                cells[header] = value
                // Silence unused-key warning path for clarity; key already used to locate header/index.
                key.length
            }
            CatalogueExcelRow(rowNumber = i + 1, cells = cells)
        }

        return CatalogueCsvParseResult(
            headers = orderedHeaders,
            rows = rows,
            duplicateHeaderWarnings = duplicates.sorted(),
        )
    }

    /**
     * Writes [headers] (in the given order) and [rows] (each a header-to-value map, missing/`null`
     * treated as blank) back into RFC 4180 CSV text, CRLF line endings, UTF-8 BOM-prefixed (Excel
     * on Windows needs the BOM to reliably detect UTF-8 rather than guessing a legacy codepage for
     * non-ASCII content — architecture §9's own "Unicode" requirement). A field is quoted only when
     * it contains a comma, quote, or line break; an embedded quote is doubled, matching [parse]'s
     * own reader exactly, so write-then-parse is lossless for every value this contract allows.
     */
    fun write(headers: List<String>, rows: List<Map<String, String?>>): String {
        val builder = StringBuilder()
        builder.append(BOM)
        builder.append(headers.joinToString(",") { it.csvEscape() }).append("\r\n")
        rows.forEach { row ->
            builder.append(headers.joinToString(",") { header -> (row[header] ?: "").csvEscape() }).append("\r\n")
        }
        return builder.toString()
    }

    private fun String.csvEscape(): String =
        if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + replace("\"", "\"\"") + "\""
        } else {
            this
        }

    /** Splits [text] into records of raw field strings, honoring RFC 4180 quoting. Each record is
     * one logical row (a quoted field's embedded `\n`/`\r\n` does not end the record). */
    private fun tokenize(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        var field = StringBuilder()
        var inQuotes = false
        var i = 0
        var sawAnyContentOnCurrentLine = false

        fun endField() {
            fields.add(field.toString())
            field = StringBuilder()
        }

        fun endRecord() {
            endField()
            records.add(fields)
            fields = mutableListOf()
            sawAnyContentOnCurrentLine = false
        }

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                        i++
                        continue
                    }
                } else {
                    field.append(c)
                    i++
                    continue
                }
            } else {
                when (c) {
                    '"' -> {
                        inQuotes = true
                        sawAnyContentOnCurrentLine = true
                        i++
                    }
                    ',' -> {
                        sawAnyContentOnCurrentLine = true
                        endField()
                        i++
                    }
                    '\r' -> {
                        if (i + 1 < text.length && text[i + 1] == '\n') i++
                        endRecord()
                        i++
                    }
                    '\n' -> {
                        endRecord()
                        i++
                    }
                    else -> {
                        sawAnyContentOnCurrentLine = true
                        field.append(c)
                        i++
                    }
                }
            }
        }
        // Final record with no trailing newline.
        if (field.isNotEmpty() || fields.isNotEmpty() || sawAnyContentOnCurrentLine) {
            endRecord()
        }
        return records
    }
}

data class CatalogueCsvParseResult(
    val headers: List<String>,
    val rows: List<CatalogueExcelRow>,
    /** Header names (original casing, trimmed) that appeared more than once in the file — only the
     * first occurrence's column was kept. Surfaced so an import preview can show this explicitly
     * rather than silently losing a duplicate column's values. */
    val duplicateHeaderWarnings: List<String>,
)
