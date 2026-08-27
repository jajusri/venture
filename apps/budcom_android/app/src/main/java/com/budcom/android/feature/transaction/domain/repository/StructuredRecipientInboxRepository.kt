package com.budcom.android.feature.transaction.domain.repository

import com.budcom.android.feature.transaction.domain.model.StructuredRecipientInboxEntry
import com.budcom.android.feature.transaction.domain.model.TransactionTimestamp
import com.budcom.android.feature.transaction.domain.port.RelayMailboxDeliveryItem

interface StructuredRecipientInboxRepository {
    suspend fun findByEnvelopeId(companyId: String, envelopeId: String): StructuredRecipientInboxEntry?
    suspend fun findAll(companyId: String): List<StructuredRecipientInboxEntry>
    suspend fun loadMailboxCursor(companyId: String, mailboxId: String): String?
    suspend fun saveMailboxCursor(companyId: String, mailboxId: String, cursor: String?)
    suspend fun persistIfNew(
        companyId: String,
        item: RelayMailboxDeliveryItem,
        timestamp: TransactionTimestamp,
    ): StructuredRecipientInboxEntry?
}
