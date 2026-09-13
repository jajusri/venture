package com.jajusri.venture.feature.businessprofile.data.local

import androidx.room.Entity

/**
 * MVP-1.3-A Business Profile — one row per Tally company (PDL-019: scoped per selected `companyId`,
 * never an installation-global identity). `companyId` alone is the primary key, since this is a
 * singleton per company — unlike every Party-adjacent table, no second natural-key component is
 * needed. 100% VENTURE-owned data (PDL-019): deliberately no field-provenance tracking, no Tally
 * source-link reference — [com.jajusri.venture.feature.party.data.local.PartyFieldProvenanceEntity]'s
 * model is explicitly not applied here.
 */
@Entity(
    tableName = "business_profile",
    primaryKeys = ["companyId"],
)
data class BusinessProfileEntity(
    val companyId: String,
    val tradingName: String,
    val legalName: String?,
    val addressLine1: String?,
    val addressCity: String?,
    val addressState: String?,
    val addressPincode: String?,
    val phone: String?,
    val phoneNormalized: String?,
    val email: String?,
    val gstin: String?,
    val website: String?,
    val description: String?,
    val logoAssetPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
