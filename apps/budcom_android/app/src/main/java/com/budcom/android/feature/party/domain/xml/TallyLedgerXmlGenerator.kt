package com.budcom.android.feature.party.domain.xml

import com.budcom.android.feature.party.domain.model.TallyExportFieldMapping

/**
 * Generates a Tally-compatible external IMPORTDATA XML envelope for altering one Ledger master's
 * contact-compatible fields (MVP-1.1-D). BUDCOM never sends this to Tally itself — it is written
 * to a file the user manually imports via Tally's own Import Data feature (architecture §18/§19).
 *
 * Follows the standard, well-documented Tally external-XML-import convention
 * (`<TALLYREQUEST>Import Data</TALLYREQUEST>` / `<IMPORTDATA>` / `<REQUESTDATA>` /
 * `<TALLYMESSAGE xmlns:UDF="TallyUDF">` / `<LEDGER NAME="..." ACTION="Alter">`), styled
 * consistently with the Connector's own `TallyXmlRequestBuilder` (template-literal envelope
 * construction, no XML DOM library, the same 5-entity `escapeXml` convention) but implemented
 * independently in Kotlin since no prior write/import path exists anywhere in this codebase.
 *
 * **Known, disclosed Tally XML limitation** (not a BUDCOM shortcut): Tally's XML `ACTION="Alter"`
 * matches an existing Ledger master by `NAME`, not by GUID — there is no documented GUID-based
 * alter-matching in Tally's external XML API. [ledgerGuid], when supplied, is still included as an
 * informational `<GUID>` field inside the ledger body (some Tally configurations use it for
 * reconciliation), but the actual match key Tally itself uses is the `NAME` attribute. To keep
 * that name reliably current, callers must always pass the ledger's *current* known Tally name
 * (`PartySourceLink.externalDisplayName`, kept fresh at every Party reconciliation) — never a
 * stale cached display name.
 */
object TallyLedgerXmlGenerator {

    data class Request(
        val companyName: String,
        val ledgerName: String,
        val ledgerGuid: String?,
        /** fieldName -> value; every key must be [TallyExportFieldMapping]-eligible. */
        val fields: Map<String, String>,
    )

    fun generate(request: Request): String {
        require(request.companyName.isNotBlank()) { "Company name is required." }
        require(request.ledgerName.isNotBlank()) { "Ledger name is required." }
        require(request.fields.isNotEmpty()) { "At least one field must be selected for export." }
        val ineligible = request.fields.keys.filterNot { TallyExportFieldMapping.isEligible(it) }
        require(ineligible.isEmpty()) { "Field(s) not eligible for Tally export: $ineligible" }
        require(request.fields.values.none { it.isBlank() }) {
            "An export field's value must not be blank — clearing a Tally field is not supported."
        }

        val guidLine = request.ledgerGuid
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { "          <GUID>${escapeXml(it)}</GUID>\n" }
            .orEmpty()

        val fieldLines = TallyExportFieldMapping.ELIGIBLE_FIELDS
            .mapNotNull { fieldName -> request.fields[fieldName]?.let { fieldName to it } }
            .joinToString("\n") { (fieldName, value) ->
                val tag = requireNotNull(TallyExportFieldMapping.tallyTagFor(fieldName))
                "          <$tag>${escapeXml(value)}</$tag>"
            }

        return """<ENVELOPE>
  <HEADER>
    <TALLYREQUEST>Import Data</TALLYREQUEST>
  </HEADER>
  <BODY>
    <IMPORTDATA>
      <REQUESTDESC>
        <REPORTNAME>All Masters</REPORTNAME>
        <STATICVARIABLES>
          <SVCURRENTCOMPANY>${escapeXml(request.companyName)}</SVCURRENTCOMPANY>
        </STATICVARIABLES>
      </REQUESTDESC>
      <REQUESTDATA>
        <TALLYMESSAGE xmlns:UDF="TallyUDF">
          <LEDGER NAME="${escapeXml(request.ledgerName)}" ACTION="Alter">
$guidLine$fieldLines
          </LEDGER>
        </TALLYMESSAGE>
      </REQUESTDATA>
    </IMPORTDATA>
  </BODY>
</ENVELOPE>"""
    }

    /** Same 5-entity escape convention as the Connector's `TallyXmlRequestBuilder.escapeXml` — no
     * CDATA usage anywhere in this codebase's Tally XML, deliberately kept consistent. */
    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
