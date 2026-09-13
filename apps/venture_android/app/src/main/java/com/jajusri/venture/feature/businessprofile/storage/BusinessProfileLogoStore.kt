package com.jajusri.venture.feature.businessprofile.storage

import android.net.Uri
import java.io.File

/**
 * MVP-1.3-A logo storage boundary (PDL-019 §4) — deliberately a small interface so the underlying
 * mechanism (today: app-private internal storage) can change later (e.g. to the existing Private
 * USB Storage subsystem) without touching the Business Profile domain model or repository. Mirrors
 * the existing [com.jajusri.venture.feature.party.sharing.PartyXmlExportCoordinator] shape: a
 * framework-coupled boundary interface living beside its Android implementation, not inside
 * `domain/`, since it is inherently `Context`/`Uri`/`File`-coupled.
 */
interface BusinessProfileLogoStore {
    /**
     * Validates and copies [sourceUri]'s bytes into durable, app-private storage for [companyId]'s
     * logo, replacing any prior logo for that company. Never depends on [sourceUri] remaining valid
     * afterward — the source may be a transient Photo-Picker grant. Returns the stable internal path
     * to persist on the profile ([BusinessProfileLogoResult.Success]), or a specific, honest failure
     * reason ([BusinessProfileLogoResult.Failure]) — never throws.
     */
    suspend fun saveLogo(companyId: String, sourceUri: Uri): BusinessProfileLogoResult

    /** Resolves a persisted `logoAssetPath` to a readable [File], or `null` if the path is blank,
     * missing, or unreadable — a missing/corrupt logo is always a handled state, never a crash. */
    fun resolveLogoFile(logoAssetPath: String?): File?

    /** Best-effort delete — safe to call even if no logo file exists for [companyId]. */
    suspend fun deleteLogo(companyId: String)
}

sealed interface BusinessProfileLogoResult {
    data class Success(val logoAssetPath: String) : BusinessProfileLogoResult
    data class Failure(val reason: BusinessProfileLogoFailureReason) : BusinessProfileLogoResult
}

enum class BusinessProfileLogoFailureReason {
    UnsupportedFileType,
    FileTooLarge,
    UnreadableSource,
    StorageError,
}
