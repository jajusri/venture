package com.budcom.android.feature.masterdata.ledger.sharing

import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPeriodSelection
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementMode
import kotlinx.coroutines.flow.Flow

/**
 * Remembered defaults for the fast Share Ledger path (Settings -> Ledger Sharing). The normal
 * Share Ledger tap uses these immediately, without an options screen — see
 * [com.budcom.android.feature.masterdata.ledger.presentation.LedgerStatementViewModel].
 */
data class LedgerSharingPreferences(
    val statementMode: LedgerStatementMode = LedgerStatementMode.Summary,
    val defaultPeriod: LedgerSharingDefaultPeriod = LedgerSharingDefaultPeriod.Last7Sales,
    val defaultDestination: LedgerShareDefaultDestination = LedgerShareDefaultDestination.AndroidShare,
)

/**
 * Persistable period choices for the default-period setting. Deliberately excludes
 * [LedgerPeriodSelection.Custom] — a fixed date range would go stale as a rolling "default" and
 * is only ever a one-off choice made from the advanced options surface.
 */
enum class LedgerSharingDefaultPeriod {
    /** Matches the screen's own pre-existing hardcoded default — chosen so leaving this setting
     * untouched changes nothing for an existing user. */
    Last7Sales,
    Today,
    ThisMonth,
    LastMonth,
    CurrentFinancialYear,
    ;

    fun toPeriodSelection(): LedgerPeriodSelection = when (this) {
        Last7Sales -> LedgerPeriodSelection.Last7Sales
        Today -> LedgerPeriodSelection.Today
        ThisMonth -> LedgerPeriodSelection.ThisMonth
        LastMonth -> LedgerPeriodSelection.LastMonth
        CurrentFinancialYear -> LedgerPeriodSelection.CurrentFinancialYear
    }
}

/**
 * Selectable default share destinations. [LedgerShareDestination.WhatsAppToParty] is intentionally
 * not a member here — its recipient is resolved per-party (an explicit mobile field when one
 * exists, or a plausible 10-digit ledger Alias fallback today) and can be unavailable for a given
 * party, so it can never be a meaningful *default*; it remains selectable only per-share,
 * conditionally enabled, from the advanced options surface.
 */
enum class LedgerShareDefaultDestination {
    WhatsAppSelect,
    AndroidShare,
    SavePdf,
    ;

    fun toShareDestination(): LedgerShareDestination = when (this) {
        WhatsAppSelect -> LedgerShareDestination.WhatsAppSelect
        AndroidShare -> LedgerShareDestination.AndroidShare
        SavePdf -> LedgerShareDestination.SavePdf
    }
}

/**
 * Every destination the advanced/change-options surface can offer for one share, including the
 * always-disabled [WhatsAppToParty].
 */
enum class LedgerShareDestination {
    /** Enabled only when a recipient resolves for the current party — see
     * [com.budcom.android.feature.masterdata.ledger.sharing.resolveWhatsAppRecipient]. Never
     * silently sends; the user always performs the final Send inside WhatsApp. */
    WhatsAppToParty,
    WhatsAppSelect,
    AndroidShare,
    SavePdf,
    PreviewPdf,
}

interface LedgerSharingPreferencesStore {
    val observation: Flow<LedgerSharingPreferences>
    suspend fun save(preferences: LedgerSharingPreferences)
}
