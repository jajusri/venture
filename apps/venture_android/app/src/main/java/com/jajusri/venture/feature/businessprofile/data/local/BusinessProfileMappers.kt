package com.jajusri.venture.feature.businessprofile.data.local

import com.jajusri.venture.feature.businessprofile.domain.model.BusinessProfile

internal fun BusinessProfileEntity.toDomain(): BusinessProfile = BusinessProfile(
    companyId = companyId,
    tradingName = tradingName,
    legalName = legalName,
    addressLine1 = addressLine1,
    addressCity = addressCity,
    addressState = addressState,
    addressPincode = addressPincode,
    phone = phone,
    phoneNormalized = phoneNormalized,
    email = email,
    gstin = gstin,
    website = website,
    description = description,
    logoAssetPath = logoAssetPath,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
