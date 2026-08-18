package com.budcom.android.feature.businessprofile.domain.repository

import android.net.Uri
import com.budcom.android.feature.businessprofile.domain.model.BusinessProfile
import com.budcom.android.feature.businessprofile.domain.model.BusinessProfileDraft
import com.budcom.android.feature.businessprofile.storage.BusinessProfileLogoResult
import java.io.File

/**
 * Local-first, company-scoped Business Profile read+write surface (MVP-1.3-A, PDL-019). Never
 * performs a network/Connector/Tally call — 100% BUDCOM-owned data.
 */
interface BusinessProfileRepository {
    /** `null` when the company has never saved a profile — a genuine, honest "not set up yet"
     * state, never fabricated. */
    suspend fun getProfile(companyId: String): BusinessProfile?

    /** Creates the profile on first save, or replaces the full editable field set on every
     * subsequent save — callers must pass the full intended state, exactly like
     * [com.budcom.android.feature.party.domain.repository.PartyRepository.editNote] already
     * requires for Party notes. Never touches [BusinessProfile.logoAssetPath] — see [updateLogo]. */
    suspend fun saveProfile(companyId: String, draft: BusinessProfileDraft): BusinessProfile

    /** Validates and stores [sourceUri] as the company's logo via the injected
     * [com.budcom.android.feature.businessprofile.storage.BusinessProfileLogoStore], then persists
     * the resulting internal path on the profile row. Returns `null` if no profile exists yet for
     * [companyId] (the base profile must be saved — with at least a trading name — before a logo
     * can attach to it). */
    suspend fun updateLogo(companyId: String, sourceUri: Uri): BusinessProfileLogoResult?

    /** Clears the stored logo reference and deletes the underlying file. Safe to call even if no
     * logo currently exists. */
    suspend fun clearLogo(companyId: String)

    /** Resolves a persisted [BusinessProfile.logoAssetPath] to a real, safely-contained [File] for
     * display — re-validates via the same path-traversal-defended
     * [com.budcom.android.feature.businessprofile.storage.BusinessProfileLogoStore] check used on
     * write, even though the path originated from this app's own database, so a corrupted/tampered
     * row can never resolve outside the logo directory. Returns `null` for a blank path, a missing
     * file, or a path that fails containment — never throws. */
    suspend fun resolveLogoFile(logoAssetPath: String?): File?
}
