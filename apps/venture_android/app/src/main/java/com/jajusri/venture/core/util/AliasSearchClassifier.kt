package com.jajusri.venture.core.util

/**
 * Classifies a Tally ledger Alias (or a search query typed against one) into the two distinct,
 * non-overlapping VENTURE-specific meanings a purely-numeric Alias can carry:
 *
 * - exactly 10 digits (leading digit 6-9) -> a mobile-number candidate, see
 *   [PhoneNumberNormalizer.normalizeIndianMobile] (the existing, separate, canonical rule);
 * - 1-5 digits -> a short ledger lookup shortcut (e.g. an account-code-style Alias like `25` or
 *   `1234`), never treated as a phone number.
 *
 * A 6-9 digit numeric Alias is deliberately neither: too long to be a plausible short lookup code,
 * too short to be an Indian mobile number. It gets no special treatment beyond ordinary substring
 * search — this is intentional, not a gap.
 */
object AliasSearchClassifier {

    /**
     * True only for a value that is 1 to 5 characters long, entirely numeric, after trimming only
     * outer whitespace (matching [PhoneNumberNormalizer.normalizeIndianMobile]'s same
     * never-touch-internal-formatting convention, so a search shortcut and a mobile candidate can
     * never both match the same input).
     */
    fun isShortNumericAlias(value: String?): Boolean {
        val trimmed = value?.trim().orEmpty()
        return trimmed.length in 1..5 && trimmed.all(Char::isDigit)
    }
}
