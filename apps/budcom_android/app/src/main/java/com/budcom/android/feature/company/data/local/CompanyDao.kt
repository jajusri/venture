package com.budcom.android.feature.company.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CompanyDao {
    @Query("SELECT * FROM cached_companies ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<CompanyEntity>

    @Query("SELECT COUNT(*) FROM cached_companies")
    suspend fun count(): Int

    @Query("SELECT * FROM company_discovery_meta WHERE id = :id LIMIT 1")
    suspend fun getMeta(id: Int = CompanyDiscoveryMetaEntity.SINGLETON_ID): CompanyDiscoveryMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(companies: List<CompanyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: CompanyDiscoveryMetaEntity)

    @Query("DELETE FROM cached_companies")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(companies: List<CompanyEntity>, meta: CompanyDiscoveryMetaEntity) {
        deleteAll()
        if (companies.isNotEmpty()) {
            insertAll(companies)
        }
        upsertMeta(meta)
    }
}
