package com.jajusri.venture.feature.transaction.domain.repository

import com.jajusri.venture.feature.transaction.domain.model.StructuredRecipientInboxEntry
import com.jajusri.venture.feature.transaction.domain.model.TransactionTimestamp
import com.jajusri.venture.feature.transaction.domain.port.RelayMailboxDeliveryItem

interface StructuredRecipientInboxRepository {
    suspend fun findByEnvelopeId(companyId: String, envelopeId: String): StructuredRecipientInboxEntry?
    suspend fun findPage(companyId: String, limit: Int, offset: Int): List<StructuredRecipientInboxEntry>
    suspend fun findAll(companyId: String): List<StructuredRecipientInboxEntry>
    suspend fun loadMailboxCursor(companyId: String, mailboxId: String): String?
    suspend fun saveMailboxCursor(companyId: String, mailboxId: String, cursor: String?)
    suspend fun persistIfNew(
        companyId: String,
        item: RelayMailboxDeliveryItem,
        timestamp: TransactionTimestamp,
    ): StructuredRecipientInboxEntry?
}
