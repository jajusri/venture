package com.budcom.android.feature.masterdata.ledger.sharing

import android.content.Intent
import android.net.Uri
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement

interface LedgerStatementShareCoordinator {
    suspend fun preparePdf(statement: LedgerStatement, companyName: String?): LedgerStatementShareResult<PreparedLedgerStatementPdf>
    fun createPdfShareIntent(pdf: PreparedLedgerStatementPdf): LedgerStatementShareResult<Intent>

    /** Same generic PDF share intent, constrained to WhatsApp ("open WhatsApp and let the user
     * choose recipient" — never a specific contact). Fails distinctly, without releasing [pdf]'s
     * caller-visible state prematurely, when WhatsApp is not installed. */
    fun createWhatsAppShareIntent(pdf: PreparedLedgerStatementPdf): LedgerStatementShareResult<Intent>

    /** A view-only intent for the optional Preview PDF action — never a send action. */
    fun createPreviewIntent(pdf: PreparedLedgerStatementPdf): LedgerStatementShareResult<Intent>
    suspend fun savePdf(pdf: PreparedLedgerStatementPdf, destination: Uri): LedgerStatementShareResult<Unit>
    fun releasePdf(pdf: PreparedLedgerStatementPdf)
}

data class PreparedLedgerStatementPdf(
    val contentUri: String,
    val cacheFilePath: String,
    val suggestedFilename: String,
)

sealed interface LedgerStatementShareResult<out T> {
    data class Success<T>(val value: T) : LedgerStatementShareResult<T>
    data class Failure(val message: String) : LedgerStatementShareResult<Nothing>
}

/**
 * A statement is always shareable once it has loaded — unlike a Voucher, there is no
 * "wrong voucher type" concept here, only "the data available for this period" (which may be
 * a partial/coverage-limited statement, still worth sharing with its own honest coverage note
 * rendered on the PDF rather than blocked).
 */
fun sanitizedLedgerStatementFilename(ledgerName: String, from: String, to: String): String {
    val safe = ledgerName
        .trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('.', '-', '_')
        .take(60)
        .ifBlank { "ledger" }
    return "BUDCOM-Ledger-$safe-$from-to-$to.pdf"
}
