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
 * not a member here — BUDCOM does not yet resolve a party phone/WhatsApp number locally (that
 * capability is reserved for MVP-1.1 Connect's "search every ledger by phone number"), so it can
 * never be a meaningful default; it remains selectable only per-share, always disabled, from the
 * advanced options surface.
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
    /** Always disabled today — see [LedgerShareDefaultDestination]'s doc comment. Never silently
     * sends; BUDCOM does not currently have a way to resolve who to send to. */
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
