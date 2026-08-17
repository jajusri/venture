package com.budcom.android.core.util

/**
 * Single canonical Indian mobile-number normalization used across BUDCOM — extracted from
 * [com.budcom.android.feature.masterdata.ledger.sharing.WhatsAppRecipientResolver] (MVP-1) so
 * MVP-1.1 Connect's Alias-phone-seeding rule and WhatsApp resolution never diverge on what counts
 * as a valid Indian mobile number.
 */
object PhoneNumberNormalizer {

    /**
     * Accepts only an unambiguous 10-digit Indian mobile number: numeric only after trimming
     * leading and trailing whitespace (never internal whitespace, punctuation, or ledger-code
     * formatting), exactly 10 digits, leading digit 6-9 (the standard Indian mobile number
     * series). Returns the normalized `+91XXXXXXXXXX` form, or null if the input is not
     * confidently a mobile number.
     *
     * This is the "strict Indian mobile validation rule" the locked Connect/Universal-Party
     * specification requires for the Tally-Alias-to-phone seeding fallback — reusing it (rather
     * than a looser 10-digit-only check) is what makes a plain 10-digit ledger code such as a
     * pincode or account number correctly ineligible.
     */
    fun normalizeIndianMobile(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.length != 10) return null
        if (!trimmed.all(Char::isDigit)) return null
        if (trimmed[0] !in '6'..'9') return null
        return "+91$trimmed"
    }

    /**
     * Lenient search-key normalization for arbitrary already-known phone numbers (not an
     * eligibility gate like [normalizeIndianMobile]): strips every non-digit character, then
     * keeps the last 10 digits so `9876543210`, `+91 98765 43210` and `098765-43210` all collapse
     * to the same `9876543210` search key. Returns null when fewer than 10 digits remain, since
     * that is not enough to be a plausible mobile number.
     */
    fun normalizeForSearch(raw: String?): String? {
        val digits = raw.orEmpty().filter(Char::isDigit)
        if (digits.length < 10) return null
        return digits.takeLast(10)
    }
}
