package com.budcom.android.feature.businessprofile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BusinessProfileDao {
    @Query("SELECT * FROM business_profile WHERE companyId = :companyId")
    suspend fun findByCompany(companyId: String): BusinessProfileEntity?

    /** REPLACE-by-primary-key, the same upsert-by-natural-key convention every existing table in
     * this codebase uses — since `companyId` alone is the key, this is a genuine single-row-per-
     * company upsert, never a second row for the same company. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BusinessProfileEntity)
}
