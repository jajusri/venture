package com.budcom.android.feature.businessprofile.data.repository

import android.net.Uri
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.core.util.PhoneNumberNormalizer
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.businessprofile.data.local.BusinessProfileDao
import com.budcom.android.feature.businessprofile.data.local.BusinessProfileEntity
import com.budcom.android.feature.businessprofile.data.local.toDomain
import com.budcom.android.feature.businessprofile.domain.model.BusinessProfile
import com.budcom.android.feature.businessprofile.domain.model.BusinessProfileDraft
import com.budcom.android.feature.businessprofile.domain.repository.BusinessProfileRepository
import com.budcom.android.feature.businessprofile.storage.BusinessProfileLogoResult
import com.budcom.android.feature.businessprofile.storage.BusinessProfileLogoStore
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BusinessProfileRepositoryImpl @Inject constructor(
    private val dao: BusinessProfileDao,
    private val logoStore: BusinessProfileLogoStore,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
) : BusinessProfileRepository {

    override suspend fun getProfile(companyId: String): BusinessProfile? = withContext(dispatchers.io) {
        dao.findByCompany(companyId)?.toDomain()
    }

    override suspend fun saveProfile(companyId: String, draft: BusinessProfileDraft): BusinessProfile =
        withContext(dispatchers.io) {
            val existing = dao.findByCompany(companyId)
            val now = timeProvider.nowEpochMillis()
            val entity = BusinessProfileEntity(
                companyId = companyId,
                tradingName = draft.tradingName,
                legalName = draft.legalName,
                addressLine1 = draft.addressLine1,
                addressCity = draft.addressCity,
                addressState = draft.addressState,
                addressPincode = draft.addressPincode,
                phone = draft.phone,
                phoneNormalized = PhoneNumberNormalizer.normalizeForSearch(draft.phone),
                email = draft.email,
                gstin = draft.gstin,
                website = draft.website,
                description = draft.description,
                logoAssetPath = existing?.logoAssetPath,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            dao.upsert(entity)
            entity.toDomain()
        }

    override suspend fun updateLogo(companyId: String, sourceUri: Uri): BusinessProfileLogoResult? =
        withContext(dispatchers.io) {
            val existing = dao.findByCompany(companyId) ?: return@withContext null
            when (val result = logoStore.saveLogo(companyId, sourceUri)) {
                is BusinessProfileLogoResult.Success -> {
                    dao.upsert(existing.copy(logoAssetPath = result.logoAssetPath, updatedAt = timeProvider.nowEpochMillis()))
                    result
                }
                is BusinessProfileLogoResult.Failure -> result
            }
        }

    override suspend fun clearLogo(companyId: String) = withContext(dispatchers.io) {
        val existing = dao.findByCompany(companyId)
        logoStore.deleteLogo(companyId)
        if (existing != null && existing.logoAssetPath != null) {
            dao.upsert(existing.copy(logoAssetPath = null, updatedAt = timeProvider.nowEpochMillis()))
        }
    }

    override suspend fun resolveLogoFile(logoAssetPath: String?): File? = withContext(dispatchers.io) {
        logoStore.resolveLogoFile(logoAssetPath)
    }
}
