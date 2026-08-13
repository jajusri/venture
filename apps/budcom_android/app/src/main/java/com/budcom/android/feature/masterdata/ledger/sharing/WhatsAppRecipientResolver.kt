package com.budcom.android.feature.masterdata.ledger.sharing

/**
 * Where [RecipientResolution.normalizedNumber] came from, preserved for traceability (never
 * surfaced as technical detail in the normal sharing flow — see
 * [com.budcom.android.feature.masterdata.ledger.presentation.LedgerStatementViewModel]).
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
 * number — never assumed just because it happens to be 10 digits. BUDCOM has no explicit
 * mobile/phone field for a ledger today (reserved for MVP-1.1 Connect), so [explicitMobile] is
 * always `null` at every current call site; the parameter exists so this resolver is already
 * correct once that field exists, without needing to change the priority logic later.
 */
fun resolveWhatsAppRecipient(explicitMobile: String?, alias: String?): RecipientResolution {
    normalizeIndianMobile(explicitMobile)?.let { return RecipientResolution(it, RecipientResolutionSource.ExplicitMobile) }
    normalizeIndianMobile(alias)?.let { return RecipientResolution(it, RecipientResolutionSource.AliasFallback) }
    return RecipientResolution(null, RecipientResolutionSource.None)
}

/**
 * Accepts only an unambiguous 10-digit Indian mobile number: numeric only after trimming leading
 * and trailing whitespace (never internal whitespace, punctuation, or ledger-code formatting),
 * exactly 10 digits, leading digit 6-9 (the standard Indian mobile number series). Returns the
 * normalized `+91XXXXXXXXXX` form, or null if the input is not confidently a mobile number.
 */
private fun normalizeIndianMobile(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.length != 10) return null
    if (!trimmed.all(Char::isDigit)) return null
    if (trimmed[0] !in '6'..'9') return null
    return "+91$trimmed"
}
