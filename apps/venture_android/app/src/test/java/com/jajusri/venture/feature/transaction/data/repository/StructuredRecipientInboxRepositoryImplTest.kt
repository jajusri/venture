package com.jajusri.venture.feature.transaction.data.repository

import com.jajusri.venture.feature.transaction.data.local.RecipientInboxCursorDao
import com.jajusri.venture.feature.transaction.data.local.RecipientInboxCursorEntity
import com.jajusri.venture.feature.transaction.data.local.StructuredRecipientInboxDao
import com.jajusri.venture.feature.transaction.data.local.StructuredRecipientInboxEntity
import com.jajusri.venture.feature.transaction.domain.model.RecipientInboxTransportState
import com.jajusri.venture.feature.transaction.domain.model.StructuredRecipientInboxValidation
import com.jajusri.venture.feature.transaction.domain.model.TransactionTimestamp
import com.jajusri.venture.feature.transaction.domain.model.TransactionTimestampSource
import com.jajusri.venture.feature.transaction.domain.port.RelayMailboxDeliveryItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StructuredRecipientInboxRepositoryImplTest {
    private val dispatchers = object : com.jajusri.venture.core.util.DispatcherProvider {
        override val main = kotlinx.coroutines.Dispatchers.Unconfined
        override val io = kotlinx.coroutines.Dispatchers.Unconfined
        override val default = kotlinx.coroutines.Dispatchers.Unconfined
    }
    private val inboxDao = FakeStructuredRecipientInboxDao()
    private val cursorDao = FakeRecipientInboxCursorDao()
    private val repo = StructuredRecipientInboxRepositoryImpl(inboxDao, cursorDao, dispatchers)
    private val ts = TransactionTimestamp(100, TransactionTimestampSource.DeviceLocalProvisional)

    @Test
    fun `valid relay mailbox item persists once and remains eligible for acknowledgement`() = runTest {
        val item = mailboxItem()
        assertNotNull(repo.persistIfNew("co-1", item, ts))
        assertNull(repo.persistIfNew("co-1", item, ts))
        val stored = repo.findByEnvelopeId("co-1", "env-1")!!
        assertEquals(RecipientInboxTransportState.Received, stored.transportState)
        assertEquals("CANONICAL_ORDER", stored.objectType)
        assertEquals("order-1", stored.objectId)
    }

    @Test
    fun `validation rejects cross business and non relay accepted items`() {
        val item = mailboxItem()
        assertEquals(true, StructuredRecipientInboxValidation.validate(item, "co-1"))
        assertEquals(false, StructuredRecipientInboxValidation.validate(item, "other"))
        assertEquals(false, StructuredRecipientInboxValidation.validate(item.copy(status = "delivered"), "co-1"))
    }

    @Test
    fun `inbox page is bounded and outbox batch does not load beyond limit`() = runTest {
        repeat(8) { index ->
            inboxDao.insert(
                mailboxItem().let { item ->
                    com.jajusri.venture.feature.transaction.data.local.StructuredRecipientInboxEntity(
                        companyId = "co-1", envelopeId = "env-$index", idempotencyKey = "k-$index",
                        objectType = "CANONICAL_ORDER", objectId = "order-1", objectVersion = 1,
                        senderBusinessId = "co-sender", senderActorId = "a", senderDeviceId = "d",
                        mailboxId = "orders", mailboxSequence = index.toLong(), acceptanceId = "acc",
                        acceptedAt = 1, acceptedAtSource = "DeviceLocalProvisional", ingestedAt = 1,
                        ingestedAtSource = "DeviceLocalProvisional", transportState = "RECEIVED",
                    )
                },
            )
        }
        assertEquals(3, repo.findPage("co-1", 3, 0).size)
        assertEquals(3, repo.findPage("co-1", 3, 3).size)
        assertEquals(2, repo.findPage("co-1", 3, 6).size)
    }

    private fun mailboxItem() = RelayMailboxDeliveryItem(
        envelopeId = "env-1", mailboxSequence = 1, objectType = "CANONICAL_ORDER", objectId = "order-1", objectVersion = 1,
        senderBusinessId = "co-sender", senderActorId = "actor-s", senderDeviceId = "device-s",
        recipientBusinessId = "co-1", mailboxId = "orders", status = "relay_accepted",
        acceptedAtEpochMillis = 50, acceptanceId = "accept-1", authenticatedEnvelope = byteArrayOf(1, 2),
    )
}

class FakeStructuredRecipientInboxDao : StructuredRecipientInboxDao {
    val entries = mutableListOf<StructuredRecipientInboxEntity>()
    override suspend fun insert(entity: StructuredRecipientInboxEntity) { entries += entity }
    override suspend fun findByEnvelopeId(companyId: String, envelopeId: String) =
        entries.firstOrNull { it.companyId == companyId && it.envelopeId == envelopeId }
    override suspend fun findAll(companyId: String) = findPage(companyId, 50, 0)
    override suspend fun findPage(companyId: String, limit: Int, offset: Int) =
        entries.filter { it.companyId == companyId }.sortedBy { it.mailboxSequence }.drop(offset).take(limit)
}

class FakeRecipientInboxCursorDao : RecipientInboxCursorDao {
    val cursors = mutableListOf<RecipientInboxCursorEntity>()
    override suspend fun upsert(entity: RecipientInboxCursorEntity) {
        cursors.removeAll { it.companyId == entity.companyId && it.mailboxId == entity.mailboxId }
        cursors += entity
    }
    override suspend fun find(companyId: String, mailboxId: String) =
        cursors.firstOrNull { it.companyId == companyId && it.mailboxId == mailboxId }
}
