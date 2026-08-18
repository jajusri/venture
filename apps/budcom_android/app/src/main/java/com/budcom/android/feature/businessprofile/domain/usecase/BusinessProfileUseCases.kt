package com.budcom.android.feature.businessprofile.domain.usecase

import android.net.Uri
import com.budcom.android.feature.businessprofile.domain.model.BusinessProfile
import com.budcom.android.feature.businessprofile.domain.model.BusinessProfileDraft
import com.budcom.android.feature.businessprofile.domain.repository.BusinessProfileRepository
import com.budcom.android.feature.businessprofile.storage.BusinessProfileLogoResult
import javax.inject.Inject

class GetBusinessProfileUseCase @Inject constructor(private val repository: BusinessProfileRepository) {
    suspend operator fun invoke(companyId: String): BusinessProfile? = repository.getProfile(companyId)
}

class SaveBusinessProfileUseCase @Inject constructor(private val repository: BusinessProfileRepository) {
    suspend operator fun invoke(companyId: String, draft: BusinessProfileDraft): BusinessProfile =
        repository.saveProfile(companyId, draft)
}

class UpdateBusinessProfileLogoUseCase @Inject constructor(private val repository: BusinessProfileRepository) {
    suspend operator fun invoke(companyId: String, sourceUri: Uri): BusinessProfileLogoResult? =
        repository.updateLogo(companyId, sourceUri)
}

class ClearBusinessProfileLogoUseCase @Inject constructor(private val repository: BusinessProfileRepository) {
    suspend operator fun invoke(companyId: String) = repository.clearLogo(companyId)
}
