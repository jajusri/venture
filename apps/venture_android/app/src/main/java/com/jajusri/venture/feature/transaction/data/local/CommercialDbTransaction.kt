package com.jajusri.venture.feature.transaction.data.local

interface CommercialDbTransaction {
    suspend fun <T> run(block: suspend () -> T): T
}

object PassthroughCommercialDbTransaction : CommercialDbTransaction {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}
