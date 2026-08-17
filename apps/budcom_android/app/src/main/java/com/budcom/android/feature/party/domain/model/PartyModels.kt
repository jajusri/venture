package com.budcom.android.feature.party.domain.model

/**
 * MVP-1.1-A Universal Party Identity domain model.
 *
 * A [Party] is a stable BUDCOM identity, distinct from any Tally Ledger — see
 * [PartySourceLink]. It may exist with no source link at all (a future BUDCOM-native Prospect),
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
    BudcomOnlyPending,
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
    val budcomValue: String?,
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

/** BUDCOM-only Party note (architecture §17) — never enters Tally XML. [linkedVoucherId] is a
 * stable reference only, never a copy of Voucher data; the referenced Voucher may not be locally
 * available (e.g. outside the synced window), which is a normal, handled state, not an error. */
data class PartyNote(
    val companyId: String,
    val noteId: String,
    val partyId: String,
    val body: String,
    val linkedVoucherId: String?,
    val createdAt: Long,
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

/** Input for creating a BUDCOM-native Prospect — a Party with no Tally source link at all
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
