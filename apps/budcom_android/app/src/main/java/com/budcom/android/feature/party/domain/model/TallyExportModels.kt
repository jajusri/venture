package com.budcom.android.feature.party.domain.model

/**
 * Whitelist of [Party] fields eligible for Tally XML enrichment export (MVP-1.1-D). Every field
 * here maps to a real, Connector-confirmed Tally ledger XML tag name (see
 * `tally-ledger-mapper.ts` in the Connector, which already reads these exact tags from live Tally
 * ledger XML). Every BUDCOM-only field (tags, notes, extra contact persons, classification,
 * internal metadata) is excluded by construction — it simply has no entry here.
 *
 * [PartyFieldNames.ADDRESS_CITY] is deliberately **not** eligible: Tally's ledger master has no
 * distinct "city" tag — only free-form address lines — and the Connector's own extraction
 * (`ledger-mapper.ts`) reads a single combined `ADDRESS` string, never a separate city value. Any
 * mapping here would be an invented convention Tally itself doesn't support, and a value BUDCOM
 * could never honestly re-confirm from a genuine Tally read-back.
 */
object TallyExportFieldMapping {
    private val TAG_BY_FIELD: Map<String, String> = linkedMapOf(
        PartyFieldNames.PRIMARY_PHONE to "MOBILENUMBER",
        PartyFieldNames.PRIMARY_EMAIL to "EMAIL",
        PartyFieldNames.ADDRESS_LINE1 to "ADDRESS",
        PartyFieldNames.ADDRESS_STATE to "STATENAME",
        PartyFieldNames.ADDRESS_PINCODE to "PINCODE",
        PartyFieldNames.GSTIN to "PARTYGSTIN",
    )

    private val LABEL_BY_FIELD: Map<String, String> = mapOf(
        PartyFieldNames.PRIMARY_PHONE to "Phone",
        PartyFieldNames.PRIMARY_EMAIL to "Email",
        PartyFieldNames.ADDRESS_LINE1 to "Address",
        PartyFieldNames.ADDRESS_STATE to "State",
        PartyFieldNames.ADDRESS_PINCODE to "Pincode",
        PartyFieldNames.GSTIN to "GSTIN",
    )

    /** Stable display order for the change-review screen and XML field emission. */
    val ELIGIBLE_FIELDS: List<String> = TAG_BY_FIELD.keys.toList()

    fun tallyTagFor(fieldName: String): String? = TAG_BY_FIELD[fieldName]

    fun labelFor(fieldName: String): String = LABEL_BY_FIELD[fieldName] ?: fieldName

    fun isEligible(fieldName: String): Boolean = TAG_BY_FIELD.containsKey(fieldName)
}

/** One eligible field's export-review row: what Tally last confirmed (if anything), what BUDCOM
 * currently has pending, and its current provenance state — the change-review screen reads this
 * directly rather than re-deriving it from a raw provenance list. */
data class TallyFieldExportCandidate(
    val fieldName: String,
    val label: String,
    val tallyValue: String?,
    val budcomValue: String?,
    val state: FieldProvenanceState,
)

/** Lightweight audit record for one Tally-enrichment XML export — field *names* and output-file
 * metadata only, never raw field values (architecture §18). */
data class PartyExportEvent(
    val companyId: String,
    val exportId: String,
    val partyId: String,
    val createdAt: Long,
    val outputFileName: String,
    val fieldNames: List<String>,
)

/**
 * A live, on-demand read of one Tally ledger's contact-compatible fields, used only for re-sync
 * confirmation after an export (never for bulk/automatic Party seeding — that path stays exactly
 * as MVP-1.1-A left it). Raw values as Tally/the Connector returned them, not yet normalized.
 */
data class TallyLedgerContactSnapshot(
    val ledgerId: String,
    val mobile: String?,
    val email: String?,
    val address: String?,
    val state: String?,
    val pincode: String?,
    val gstin: String?,
)
