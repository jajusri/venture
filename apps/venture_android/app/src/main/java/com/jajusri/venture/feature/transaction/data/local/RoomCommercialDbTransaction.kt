package com.jajusri.venture.feature.transaction.data.local

import androidx.room.withTransaction
import com.jajusri.venture.core.database.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomCommercialDbTransaction @Inject constructor(
    private val database: AppDatabase,
) : CommercialDbTransaction {
    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction { block() }
}
