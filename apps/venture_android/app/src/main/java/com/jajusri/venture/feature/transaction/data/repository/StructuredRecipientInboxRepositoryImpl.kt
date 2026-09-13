package com.jajusri.venture.feature.transaction.data.repository

import com.jajusri.venture.core.util.DispatcherProvider
import com.jajusri.venture.feature.transaction.data.local.RecipientInboxCursorDao
import com.jajusri.venture.feature.transaction.data.local.RecipientInboxCursorEntity
import com.jajusri.venture.feature.transaction.data.local.StructuredRecipientInboxDao
import com.jajusri.venture.feature.transaction.data.local.StructuredRecipientInboxEntity
import com.jajusri.venture.feature.transaction.domain.model.RecipientInboxTransportState
import com.jajusri.venture.feature.transaction.domain.model.StructuredRecipientInboxEntry
import com.jajusri.venture.feature.transaction.domain.model.TransactionTimestamp
import com.jajusri.venture.feature.transaction.domain.model.TransactionTimestampSource
import com.jajusri.venture.feature.transaction.domain.port.RelayMailboxDeliveryItem
import com.jajusri.venture.feature.transaction.domain.repository.StructuredRecipientInboxRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StructuredRecipientInboxRepositoryImpl @Inject constructor(
    private val inboxDao: StructuredRecipientInboxDao,
    private val cursorDao: RecipientInboxCursorDao,
    private val dispatchers: DispatcherProvider,
) : StructuredRecipientInboxRepository {
    override suspend fun findByEnvelopeId(companyId: String, envelopeId: String): StructuredRecipientInboxEntry? =
        withContext(dispatchers.io) {
            inboxDao.findByEnvelopeId(companyId, envelopeId)?.toDomain()
        }

    override suspend fun findPage(companyId: String, limit: Int, offset: Int): List<StructuredRecipientInboxEntry> =
        withContext(dispatchers.io) {
            require(limit in 1..MAX_INBOX_PAGE)
            require(offset >= 0)
            inboxDao.findPage(companyId, limit, offset).map { it.toDomain() }
        }

    override suspend fun findAll(companyId: String): List<StructuredRecipientInboxEntry> =
        findPage(companyId, MAX_INBOX_PAGE, 0)

    override suspend fun loadMailboxCursor(companyId: String, mailboxId: String): String? =
        withContext(dispatchers.io) { cursorDao.find(companyId, mailboxId)?.cursor }

    override suspend fun saveMailboxCursor(companyId: String, mailboxId: String, cursor: String?) =
        withContext(dispatchers.io) {
            cursorDao.upsert(RecipientInboxCursorEntity(companyId, mailboxId, cursor))
        }

    override suspend fun persistIfNew(
        companyId: String,
        item: RelayMailboxDeliveryItem,
        timestamp: TransactionTimestamp,
    ): StructuredRecipientInboxEntry? = withContext(dispatchers.io) {
        if (inboxDao.findByEnvelopeId(companyId, item.envelopeId) != null) return@withContext null
        val entity = StructuredRecipientInboxEntity(
            companyId = companyId,
            envelopeId = item.envelopeId,
            idempotencyKey = "relay:${item.envelopeId}",
            objectType = item.objectType,
            objectId = item.objectId,
            objectVersion = item.objectVersion,
            senderBusinessId = item.senderBusinessId,
            senderActorId = item.senderActorId,
            senderDeviceId = item.senderDeviceId,
            mailboxId = item.mailboxId,
            mailboxSequence = item.mailboxSequence,
            acceptanceId = item.acceptanceId,
            acceptedAt = item.acceptedAtEpochMillis,
            acceptedAtSource = TransactionTimestampSource.DeviceLocalProvisional.name,
            ingestedAt = timestamp.epochMillis,
            ingestedAtSource = timestamp.source.name,
            transportState = RecipientInboxTransportState.Received.columnValue,
        )
        inboxDao.insert(entity)
        entity.toDomain()
    }

    private companion object {
        const val MAX_INBOX_PAGE = 50
    }
}

private fun StructuredRecipientInboxEntity.toDomain() = StructuredRecipientInboxEntry(
    companyId = companyId,
    envelopeId = envelopeId,
    idempotencyKey = idempotencyKey,
    objectType = objectType,
    objectId = objectId,
    objectVersion = objectVersion,
    senderBusinessId = senderBusinessId,
    senderActorId = senderActorId,
    senderDeviceId = senderDeviceId,
    mailboxSequence = mailboxSequence,
    acceptanceId = acceptanceId,
    acceptedAt = TransactionTimestamp(acceptedAt, TransactionTimestampSource.valueOf(acceptedAtSource)),
    ingestedAt = TransactionTimestamp(ingestedAt, TransactionTimestampSource.valueOf(ingestedAtSource)),
    transportState = RecipientInboxTransportState.fromColumn(transportState),
)
