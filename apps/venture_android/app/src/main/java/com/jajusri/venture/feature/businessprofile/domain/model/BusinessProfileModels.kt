package com.jajusri.venture.feature.businessprofile.domain.model

/**
 * MVP-1.3-A Business Profile (PDL-019) — one VENTURE-owned identity per selected Tally `companyId`.
 * Deliberately not provenance-tracked: unlike [com.jajusri.venture.feature.party.domain.model.Party],
 * no field here has an approved Tally round-trip, so
 * [com.jajusri.venture.feature.party.domain.model.FieldProvenanceState] is never applied.
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
 * [com.jajusri.venture.feature.party.domain.model.ProspectDraft]'s exact shape/purpose: the
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

/**
 * MVP-1.3-B "sharing foundation" (PDL-019 §5 conceptual chain: Business Profile → future
 * Catalogue → future Vartalap) — a plain, read-only, display-ready projection of the fields
 * appropriate for eventually sharing a business's identity outward. Deliberately excludes
 * internal-only concepts (`companyId`, timestamps, the raw `logoAssetPath` file-system path).
 *
 * This is *only* the internal data boundary: no Android `Intent`, no file export, no network call,
 * no public/cloud transport of any kind is wired to this type anywhere in this milestone. Building
 * an actual "Share my business card" action is explicitly out of MVP-1.3 scope (PDL-019) — this
 * type exists so a later milestone (Vartalap) has a stable, tested shape to build on rather than
 * inventing one from scratch against raw entity fields.
 */
data class BusinessProfileShareSnapshot(
    val tradingName: String,
    val legalName: String?,
    val formattedAddress: String?,
    val phone: String?,
    val email: String?,
    val gstin: String?,
    val website: String?,
    val description: String?,
)

/** Pure, side-effect-free projection — see [BusinessProfileShareSnapshot]'s own doc comment for
 * why this exists and what it deliberately does not do. */
fun BusinessProfile.toShareSnapshot(): BusinessProfileShareSnapshot = BusinessProfileShareSnapshot(
    tradingName = tradingName,
    legalName = legalName,
    formattedAddress = listOfNotNull(
        addressLine1?.takeIf { it.isNotBlank() },
        addressCity?.takeIf { it.isNotBlank() },
        addressState?.takeIf { it.isNotBlank() },
        addressPincode?.takeIf { it.isNotBlank() },
    ).joinToString(", ").takeIf { it.isNotBlank() },
    phone = phone,
    email = email,
    gstin = gstin,
    website = website,
    description = description,
)
