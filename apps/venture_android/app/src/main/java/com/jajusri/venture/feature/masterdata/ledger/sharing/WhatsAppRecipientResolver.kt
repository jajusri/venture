package com.jajusri.venture.feature.masterdata.ledger.sharing

import com.jajusri.venture.core.util.PhoneNumberNormalizer

/**
 * Where [RecipientResolution.normalizedNumber] came from, preserved for traceability (never
 * surfaced as technical detail in the normal sharing flow — see
 * [com.jajusri.venture.feature.masterdata.ledger.presentation.LedgerStatementViewModel]).
 */
enum class RecipientResolutionSource { ExplicitMobile, AliasFallback, None }

data class RecipientResolution(
    val normalizedNumber: String?,
    val source: RecipientResolutionSource,
)

/**
 * Resolves a WhatsApp-to-party recipient with the locked priority: an explicit mobile/phone
 * field always wins when present and valid; the ledger Alias is used as a fallback only when no
 * such field is available, and only when it unambiguously looks like a 10-digit Indian mobile
 * number — never assumed just because it happens to be 10 digits. VENTURE has no explicit
 * mobile/phone field for a ledger today (reserved for MVP-1.1 Connect), so [explicitMobile] is
 * always `null` at every current call site; the parameter exists so this resolver is already
 * correct once that field exists, without needing to change the priority logic later.
 */
fun resolveWhatsAppRecipient(explicitMobile: String?, alias: String?): RecipientResolution {
    PhoneNumberNormalizer.normalizeIndianMobile(explicitMobile)
        ?.let { return RecipientResolution(it, RecipientResolutionSource.ExplicitMobile) }
    PhoneNumberNormalizer.normalizeIndianMobile(alias)
        ?.let { return RecipientResolution(it, RecipientResolutionSource.AliasFallback) }
    return RecipientResolution(null, RecipientResolutionSource.None)
}
