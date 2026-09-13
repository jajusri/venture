package com.jajusri.venture.feature.company.data.local

import com.jajusri.venture.feature.company.domain.model.CompanyDiscoverySnapshot
import com.jajusri.venture.feature.company.domain.model.ConnectorCompany
import javax.inject.Inject
import javax.inject.Singleton

interface CompanyLocalDataSource {
    suspend fun hasCache(): Boolean
    suspend fun readSnapshot(): CompanyDiscoverySnapshot?
    suspend fun replaceSnapshot(snapshot: CompanyDiscoverySnapshot)
}

@Singleton
class RoomCompanyLocalDataSource @Inject constructor(
    private val companyDao: CompanyDao,
) : CompanyLocalDataSource {

    override suspend fun hasCache(): Boolean = companyDao.count() > 0 && companyDao.getMeta() != null

    override suspend fun readSnapshot(): CompanyDiscoverySnapshot? {
        val meta = companyDao.getMeta() ?: return null
        val items = companyDao.getAll().map { it.toDomain() }
        if (items.isEmpty()) return null
        return CompanyDiscoverySnapshot(
            items = items,
            schemaVersion = meta.schemaVersion,
            dataFreshnessAt = meta.dataFreshnessAt,
            contractVersion = meta.contractVersion,
            status = meta.status,
            tallyReachable = meta.tallyReachable,
            dataQualityStatus = meta.dataQualityStatus,
            dataQualityReason = meta.dataQualityReason,
            reason = meta.reason,
        )
    }

    override suspend fun replaceSnapshot(snapshot: CompanyDiscoverySnapshot) {
        companyDao.replaceAll(
            companies = snapshot.items.map { it.toEntity() },
            meta = CompanyDiscoveryMetaEntity(
                schemaVersion = snapshot.schemaVersion,
                dataFreshnessAt = snapshot.dataFreshnessAt,
                contractVersion = snapshot.contractVersion,
                status = snapshot.status,
                tallyReachable = snapshot.tallyReachable,
                dataQualityStatus = snapshot.dataQualityStatus,
                dataQualityReason = snapshot.dataQualityReason,
                reason = snapshot.reason,
                cachedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }
}

internal fun CompanyEntity.toDomain(): ConnectorCompany = ConnectorCompany(
    id = id,
    name = name,
    financialYear = financialYear,
    booksFrom = booksFrom,
    baseCurrency = baseCurrency,
)

internal fun ConnectorCompany.toEntity(): CompanyEntity = CompanyEntity(
    id = id,
    name = name,
    financialYear = financialYear,
    booksFrom = booksFrom,
    baseCurrency = baseCurrency,
)
