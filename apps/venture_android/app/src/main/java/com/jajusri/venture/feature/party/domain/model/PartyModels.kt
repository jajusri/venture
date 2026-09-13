package com.jajusri.venture.feature.party.domain.model

/**
 * MVP-1.1-A Universal Party Identity domain model.
 *
 * A [Party] is a stable VENTURE identity, distinct from any Tally Ledger — see
 * [PartySourceLink]. It may exist with no source link at all (a future VENTURE-native Prospect),
 * consistent with the locked Accounting-Optional product principle: Party identity must never
 * require an accounting ledger to exist.
 */

/** User-facing primary sections are Customer/Prospect; Supplier/Other exist for stable identity
 * and future compatibility only — see architecture §11. */
enum class PartyClassification {
    Customer,
    Prospect,
    Supplier,
    Other,
}

/** Field-level provenance/confirmation state — architecture §5/§6. */
enum class FieldProvenanceState {
    ConfirmedFromTally,
    VentureOnlyPending,
    ExportReady,
    Exported,
    Conflict,
    EmptyUnknown,
}

/** Only Tally Ledger exists as a source type for MVP-1.1-A; the column is a String to stay
 * extensible for future source types without a migration. */
enum class PartySourceType {
    TallyLedger,
}

/** Whether a source link's [PartySourceLink.externalEntityId] is backed by a real, rename-stable
 * Tally GUID, or a name-slug fallback used only when Tally never returned a GUID for that ledger
 * (see `resolveLedgerStableId` in the Connector) — a name-slug link is not rename-safe. */
enum class LedgerIdentitySource {
    Guid,
    Name,
}

/** Canonical field names participating in [PartyFieldProvenance] tracking. */
object PartyFieldNames {
    const val PRIMARY_PHONE = "primaryPhone"
    const val PRIMARY_EMAIL = "primaryEmail"
    const val ADDRESS_LINE1 = "addressLine1"
    const val ADDRESS_CITY = "addressCity"
    const val ADDRESS_STATE = "addressState"
    const val ADDRESS_PINCODE = "addressPincode"
    const val GSTIN = "gstin"
}

data class Party(
    val companyId: String,
    val partyId: String,
    val displayName: String,
    val classification: PartyClassification,
    val primaryPhone: String?,
    val primaryPhoneNormalized: String?,
    val primaryEmail: String?,
    val addressLine1: String?,
    val addressCity: String?,
    val addressState: String?,
    val addressPincode: String?,
    val gstin: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class PartyPage(
    val items: List<Party>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
)

/** Summary of one bulk contact-details seeding pass (Connect address/email/GSTIN
 * auto-population) — see [com.jajusri.venture.feature.party.domain.repository.PartyRepository.applyLedgerContactDetailsBulk]. */
data class BulkContactSeedResult(
    val matchedLedgers: Int,
    val unmatchedLedgers: Int,
    val fieldsFilled: Int,
    val fieldsConfirmed: Int,
    val fieldsConflicted: Int,
)

data class PartySourceLink(
    val companyId: String,
    val partyId: String,
    val sourceType: PartySourceType,
    val sourceInstanceId: String,
    val externalEntityId: String,
    val externalDisplayName: String,
    val identitySource: LedgerIdentitySource,
    val lastConfirmedAt: Long,
)

data class PartyFieldProvenance(
    val companyId: String,
    val partyId: String,
    val fieldName: String,
    val state: FieldProvenanceState,
    val tallyValue: String?,
    val ventureValue: String?,
    val lastConfirmedAt: Long?,
    val lastExportedAt: Long?,
    val updatedAt: Long,
)

data class PartyContactPerson(
    val companyId: String,
    val contactPersonId: String,
    val partyId: String,
    val name: String,
    val designation: String?,
    val mobile: String?,
    val mobileNormalized: String?,
    val whatsappNumber: String?,
    val email: String?,
    val isPrimary: Boolean,
    val provenance: FieldProvenanceState,
    val createdAt: Long,
    val updatedAt: Long,
)

data class Tag(
    val tagId: String,
    val parentTagId: String?,
    val name: String,
    val path: String,
    val createdAt: Long,
)

/** Note-type vocabulary (MVP-1.2-A, architecture §9.1) — a fixed, small, named set matching the
 * locked referral-spec vocabulary, not an extensible taxonomy (PDL-012). [General] is the default,
 * preserving the pre-1.2-A one-tap "just write a note" behavior. */
enum class NoteType {
    General,
    PaymentIssue,
    Complaint,
    DeliveryIssue,
    Commitment,
    ProductInterest,
    InternalRemark,
    FollowUp,
}

/** Lifecycle of a [PartyIssue] — resolving never deletes or hides its underlying notes. */
enum class IssueStatus {
    Open,
    Resolved,
}

/** VENTURE-only Party note (architecture §17) — never enters Tally XML. [linkedVoucherId] is a
 * stable reference only, never a copy of Voucher data; the referenced Voucher may not be locally
 * available (e.g. outside the synced window), which is a normal, handled state, not an error.
 *
 * MVP-1.2-A additions: [type] classifies the note (defaults to [NoteType.General], preserving
 * every pre-1.2-A note's behavior unchanged); [dueAt]/[completedAt] are only meaningful for
 * [NoteType.Commitment]/[NoteType.FollowUp] — a note is never deleted to "complete" it, completion
 * is a state change that preserves the note's place in Timeline history; [issueId] optionally
 * groups this note under a [PartyIssue].
 */
data class PartyNote(
    val companyId: String,
    val noteId: String,
    val partyId: String,
    val body: String,
    val linkedVoucherId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val type: NoteType = NoteType.General,
    val dueAt: Long? = null,
    val completedAt: Long? = null,
    val issueId: String? = null,
)

/** A grouped, continuing Party issue (architecture §9.2/§17) — e.g. "2 pieces short — Sales
 * Voucher #1842". Notes reference an issue via [PartyNote.issueId]; resolving an issue never
 * deletes or hides its notes from the Timeline, it only changes [status]. */
data class PartyIssue(
    val companyId: String,
    val issueId: String,
    val partyId: String,
    val title: String,
    val status: IssueStatus,
    val createdAt: Long,
    val resolvedAt: Long?,
    val updatedAt: Long,
)

data class PartyNotePage(
    val items: List<PartyNote>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
) {
    val canLoadMore: Boolean get() = page * pageSize < totalItems
}

/**
 * One ledger already known to be eligible for Party seeding (classification already resolved by
 * `LedgerPartyEligibilityPolicy`) — the unit the reconciliation use case hands to the repository.
 */
data class EligibleLedgerSeed(
    val ledgerId: String,
    val ledgerName: String,
    val alias: String?,
    val classification: PartyClassification,
)

/** Input for creating a VENTURE-native Prospect — a Party with no Tally source link at all
 * (architecture §27, spec §4.1). Every field except [displayName] is optional; no accounting
 * information is required or possible at creation time. */
data class ProspectDraft(
    val displayName: String,
    val phone: String? = null,
    val email: String? = null,
    val addressLine1: String? = null,
    val addressCity: String? = null,
    val addressState: String? = null,
    val addressPincode: String? = null,
    val tagIds: List<String> = emptyList(),
    val note: String? = null,
)
