package com.budcom.android.feature.transaction.data.repository

import com.budcom.android.feature.transaction.data.local.RecipientInboxCursorDao
import com.budcom.android.feature.transaction.data.local.RecipientInboxCursorEntity
import com.budcom.android.feature.transaction.data.local.StructuredRecipientInboxDao
import com.budcom.android.feature.transaction.data.local.StructuredRecipientInboxEntity
import com.budcom.android.feature.transaction.domain.model.RecipientInboxTransportState
import com.budcom.android.feature.transaction.domain.model.StructuredRecipientInboxValidation
import com.budcom.android.feature.transaction.domain.model.TransactionTimestamp
import com.budcom.android.feature.transaction.domain.model.TransactionTimestampSource
import com.budcom.android.feature.transaction.domain.port.RelayMailboxDeliveryItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StructuredRecipientInboxRepositoryImplTest {
    private val dispatchers = object : com.budcom.android.core.util.DispatcherProvider {
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
    override suspend fun findAll(companyId: String) = entries.filter { it.companyId == companyId }
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
