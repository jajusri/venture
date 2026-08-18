package com.budcom.android.feature.businessprofile.domain.model

/**
 * MVP-1.3-A Business Profile (PDL-019) — one BUDCOM-owned identity per selected Tally `companyId`.
 * Deliberately not provenance-tracked: unlike [com.budcom.android.feature.party.domain.model.Party],
 * no field here has an approved Tally round-trip, so
 * [com.budcom.android.feature.party.domain.model.FieldProvenanceState] is never applied.
 */
data class BusinessProfile(
    val companyId: String,
    val tradingName: String,
    val legalName: String?,
    val addressLine1: String?,
    val addressCity: String?,
    val addressState: String?,
    val addressPincode: String?,
    val phone: String?,
    val phoneNormalized: String?,
    val email: String?,
    val gstin: String?,
    val website: String?,
    val description: String?,
    val logoAssetPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** User-editable subset the owner-side editor writes — mirrors
 * [com.budcom.android.feature.party.domain.model.ProspectDraft]'s exact shape/purpose: the
 * repository fills in `companyId`/`phoneNormalized`/timestamps/`logoAssetPath`, never the caller. */
data class BusinessProfileDraft(
    val tradingName: String,
    val legalName: String?,
    val addressLine1: String?,
    val addressCity: String?,
    val addressState: String?,
    val addressPincode: String?,
    val phone: String?,
    val email: String?,
    val gstin: String?,
    val website: String?,
    val description: String?,
)
