package com.budcom.android.feature.party.data.repository

import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.party.data.local.PartyContactPersonDao
import com.budcom.android.feature.party.data.local.PartyContactPersonEntity
import com.budcom.android.feature.party.data.local.PartyDao
import com.budcom.android.feature.party.data.local.PartyEntity
import com.budcom.android.feature.party.data.local.PartyFieldProvenanceDao
import com.budcom.android.feature.party.data.local.PartyFieldProvenanceEntity
import com.budcom.android.feature.party.data.local.PartySourceLinkDao
import com.budcom.android.feature.party.data.local.PartySourceLinkEntity
import com.budcom.android.feature.party.data.local.PartyTagCrossRefEntity
import com.budcom.android.feature.party.data.local.TagDao
import com.budcom.android.feature.party.data.local.TagEntity
import com.budcom.android.feature.party.domain.model.EligibleLedgerSeed
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.PartyClassification
import com.budcom.android.feature.party.domain.model.PartyFieldNames
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyRepositoryImplTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }
    private val time = FakeTimeProvider()
    private val partyDao = FakePartyDao()
    private val sourceLinkDao = FakePartySourceLinkDao()
    private val fieldProvenanceDao = FakePartyFieldProvenanceDao()
    private val contactPersonDao = FakePartyContactPersonDao()
    private val tagDao = FakeTagDao()

    private fun repository() = PartyRepositoryImpl(
        partyDao, sourceLinkDao, fieldProvenanceDao, contactPersonDao, tagDao, time, dispatchers,
    )

    private fun seed(
        ledgerId: String = "guid:cash-customer",
        name: String = "ABC Traders",
        alias: String? = null,
        classification: PartyClassification = PartyClassification.Customer,
    ) = EligibleLedgerSeed(ledgerId, name, alias, classification)

    // ============================== IDENTITY ==============================

    @Test
    fun `creates a Party from an eligible ledger`() = runTest(dispatcher) {
        val result = repository().reconcilePartiesFromEligibleLedgers("co-1", listOf(seed()))
        assertEquals(1, result.size)
        assertEquals("ABC Traders", result.single().displayName)
        assertEquals(PartyClassification.Customer, result.single().classification)
    }

    @Test
    fun `repeat seed with identical input is idempotent`() = runTest(dispatcher) {
        val repo = repository()
        val first = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed())).single()
        val second = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed())).single()

        assertEquals(first.partyId, second.partyId)
        assertEquals(1, partyDao.store.size)
        assertEquals(1, sourceLinkDao.store.size)
    }

    @Test
    fun `same GUID preserves partyId across separate reconciliation runs`() = runTest(dispatcher) {
        val repo = repository()
        val run1 = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(ledgerId = "guid:abc"))).single()
        val run2 = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(ledgerId = "guid:abc"))).single()
        assertEquals(run1.partyId, run2.partyId)
    }

    @Test
    fun `ledger rename preserves partyId and updates the display name`() = runTest(dispatcher) {
        val repo = repository()
        val before = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(ledgerId = "guid:abc", name = "Old Name"))).single()
        val after = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(ledgerId = "guid:abc", name = "New Name"))).single()

        assertEquals(before.partyId, after.partyId)
        assertEquals("New Name", after.displayName)
        assertEquals(1, partyDao.store.size)
    }

    @Test
    fun `same name different GUID creates two distinct Parties`() = runTest(dispatcher) {
        val repo = repository()
        val a = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(ledgerId = "guid:aaa", name = "Same Name"))).single()
        val b = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(ledgerId = "guid:bbb", name = "Same Name"))).single()

        assertNotEquals(a.partyId, b.partyId)
        assertEquals(2, partyDao.store.size)
    }

    @Test
    fun `getPartyForLedger resolves via the source link`() = runTest(dispatcher) {
        val repo = repository()
        val created = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(ledgerId = "guid:xyz"))).single()

        val resolved = repo.getPartyForLedger("co-1", "guid:xyz")
        assertEquals(created.partyId, resolved?.partyId)
        assertNull(repo.getPartyForLedger("co-1", "guid:does-not-exist"))
    }

    // ============================== COMPANY ISOLATION ==============================

    @Test
    fun `same ledger name across two companies never collapses into one Party`() = runTest(dispatcher) {
        val repo = repository()
        val a = repo.reconcilePartiesFromEligibleLedgers("co-A", listOf(seed(ledgerId = "guid:same", name = "Shared Name"))).single()
        val b = repo.reconcilePartiesFromEligibleLedgers("co-B", listOf(seed(ledgerId = "guid:same", name = "Shared Name"))).single()

        assertNotEquals(a.partyId, b.partyId)
        assertEquals(a.partyId, repo.getPartyForLedger("co-A", "guid:same")?.partyId)
        assertEquals(b.partyId, repo.getPartyForLedger("co-B", "guid:same")?.partyId)
        assertNull(repo.getPartyForLedger("co-A", "guid:same-not-in-b").also { assertNull(it) })
    }

    @Test
    fun `same phone alias across two companies produces two independent Parties`() = runTest(dispatcher) {
        val repo = repository()
        val a = repo.reconcilePartiesFromEligibleLedgers(
            "co-A",
            listOf(seed(ledgerId = "guid:1", name = "A Traders", alias = "9876543210")),
        ).single()
        val b = repo.reconcilePartiesFromEligibleLedgers(
            "co-B",
            listOf(seed(ledgerId = "guid:2", name = "B Traders", alias = "9876543210")),
        ).single()

        assertEquals("9876543210", a.primaryPhone)
        assertEquals("9876543210", b.primaryPhone)
        assertNotEquals(a.partyId, b.partyId)
    }

    // ============================== PHONE / ALIAS SEEDING ==============================

    @Test
    fun `exactly 10-digit valid alias seeds the phone as confirmed from Tally`() = runTest(dispatcher) {
        val repo = repository()
        val party = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(alias = "9876543210"))).single()

        assertEquals("9876543210", party.primaryPhone)
        assertEquals("9876543210", party.primaryPhoneNormalized)
        val provenance = fieldProvenanceDao.findField("co-1", party.partyId, PartyFieldNames.PRIMARY_PHONE)
        assertEquals("confirmed_from_tally", provenance?.state)
        assertEquals("9876543210", provenance?.tallyValue)
    }

    @Test
    fun `9-digit alias is not seeded`() = runTest(dispatcher) {
        val party = repository().reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(alias = "987654321"))).single()
        assertNull(party.primaryPhone)
    }

    @Test
    fun `11-digit alias is not seeded`() = runTest(dispatcher) {
        val party = repository().reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(alias = "98765432101"))).single()
        assertNull(party.primaryPhone)
    }

    @Test
    fun `alphabetic alias is not seeded`() = runTest(dispatcher) {
        val party = repository().reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(alias = "98A7654321"))).single()
        assertNull(party.primaryPhone)
    }

    @Test
    fun `formatted alias is not seeded`() = runTest(dispatcher) {
        val party = repository().reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(alias = "98765-43210"))).single()
        assertNull(party.primaryPhone)
    }

    @Test
    fun `existing phone is preserved when a later alias would seed a different value`() = runTest(dispatcher) {
        val repo = repository()
        repo.updateBudcomOnlyField("co-1", partyIdFor(repo, "guid:cash-customer"), PartyFieldNames.PRIMARY_PHONE, "9111111111")
        val party = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(alias = "9876543210"))).single()

        assertEquals("9111111111", party.primaryPhone)
    }

    @Test
    fun `a different alias value than the existing phone surfaces as conflict, never silently overwritten`() = runTest(dispatcher) {
        val repo = repository()
        val party1 = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(alias = "9876543210"))).single()
        val party2 = repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(alias = "9111111111"))).single()

        assertEquals(party1.partyId, party2.partyId)
        assertEquals("9876543210", party2.primaryPhone) // unchanged
        val provenance = fieldProvenanceDao.findField("co-1", party2.partyId, PartyFieldNames.PRIMARY_PHONE)
        assertEquals("conflict", provenance?.state)
        assertEquals("9111111111", provenance?.tallyValue)
    }

    // ============================== PROVENANCE ==============================

    @Test
    fun `BUDCOM-entered field is pending, never confirmed`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        val state = repo.updateBudcomOnlyField("co-1", partyId, PartyFieldNames.PRIMARY_EMAIL, "owner@example.com")

        assertEquals(FieldProvenanceState.BudcomOnlyPending, state)
        assertEquals("owner@example.com", repo.getPartyById("co-1", partyId)?.primaryEmail)
    }

    @Test
    fun `Tally exact-match confirmation promotes a field with no pending value to confirmed`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")

        val state = repo.confirmFieldFromTally("co-1", partyId, PartyFieldNames.GSTIN, "22AAAAA0000A1Z5")

        assertEquals(FieldProvenanceState.ConfirmedFromTally, state)
        assertEquals("22AAAAA0000A1Z5", repo.getPartyById("co-1", partyId)?.gstin)
    }

    @Test
    fun `Tally value that mismatches a pending BUDCOM value remains conflict, not silently confirmed`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        repo.updateBudcomOnlyField("co-1", partyId, PartyFieldNames.GSTIN, "22BBBBB0000B1Z5")

        val state = repo.confirmFieldFromTally("co-1", partyId, PartyFieldNames.GSTIN, "22AAAAA0000A1Z5")

        assertEquals(FieldProvenanceState.Conflict, state)
        val provenance = repo.getFieldProvenance("co-1", partyId).single { it.fieldName == PartyFieldNames.GSTIN }
        assertEquals("22BBBBB0000B1Z5", provenance.budcomValue)
        assertEquals("22AAAAA0000A1Z5", provenance.tallyValue)
    }

    @Test
    fun `Tally value matching the pending BUDCOM value confirms it`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        repo.updateBudcomOnlyField("co-1", partyId, PartyFieldNames.GSTIN, "22AAAAA0000A1Z5")

        val state = repo.confirmFieldFromTally("co-1", partyId, PartyFieldNames.GSTIN, "22AAAAA0000A1Z5")

        assertEquals(FieldProvenanceState.ConfirmedFromTally, state)
    }

    @Test
    fun `exported state round-trips through the repository read path`() = runTest(dispatcher) {
        // 1.1-A models but does not implement the XML export workflow (1.1-D scope) -- this
        // proves the state itself is representable and correctly surfaced via getFieldProvenance.
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        fieldProvenanceDao.upsert(
            PartyFieldProvenanceEntity(
                companyId = "co-1", partyId = partyId, fieldName = PartyFieldNames.ADDRESS_CITY,
                state = "exported", tallyValue = null, budcomValue = "Hyderabad",
                lastConfirmedAt = null, lastExportedAt = time.now, updatedAt = time.now,
            ),
        )
        val provenance = repo.getFieldProvenance("co-1", partyId).single { it.fieldName == PartyFieldNames.ADDRESS_CITY }
        assertEquals(FieldProvenanceState.Exported, provenance.state)
    }

    // ============================== CONTACT PERSONS (read path) ==============================

    @Test
    fun `getContactPersons returns multiple contacts with primary ordered first`() = runTest(dispatcher) {
        val repo = repository()
        val partyId = partyIdFor(repo, "guid:cash-customer")
        contactPersonDao.upsert(contact(partyId, "cp-1", "Accounts Person", isPrimary = false))
        contactPersonDao.upsert(contact(partyId, "cp-2", "Owner", isPrimary = true))

        val contacts = repo.getContactPersons("co-1", partyId)
        assertEquals(2, contacts.size)
        assertTrue(contacts.first().isPrimary)
        assertEquals("Owner", contacts.first().name)
    }

    // ============================== helpers ==============================

    private suspend fun partyIdFor(repo: com.budcom.android.feature.party.domain.repository.PartyRepository, ledgerId: String): String =
        repo.reconcilePartiesFromEligibleLedgers("co-1", listOf(seed(ledgerId = ledgerId))).single().partyId

    private fun contact(partyId: String, contactPersonId: String, name: String, isPrimary: Boolean) = PartyContactPersonEntity(
        companyId = "co-1",
        contactPersonId = contactPersonId,
        partyId = partyId,
        name = name,
        designation = null,
        mobile = null,
        mobileNormalized = null,
        whatsappNumber = null,
        email = null,
        isPrimary = isPrimary,
        provenance = "budcom_only",
        createdAt = time.now,
        updatedAt = time.now,
    )
}

private class FakeTimeProvider(var now: Long = 1_000_000L) : TimeProvider {
    override fun nowEpochMillis(): Long = now
}

private class FakePartyDao : PartyDao {
    val store = mutableMapOf<Pair<String, String>, PartyEntity>()
    private fun key(companyId: String, partyId: String) = companyId to partyId

    override suspend fun countForCompany(companyId: String): Int = store.values.count { it.companyId == companyId }
    override suspend fun findById(companyId: String, partyId: String): PartyEntity? = store[key(companyId, partyId)]
    override suspend fun upsert(entity: PartyEntity) {
        store[key(entity.companyId, entity.partyId)] = entity
    }
    override suspend fun upsertAll(entities: List<PartyEntity>) {
        entities.forEach { upsert(it) }
    }
    override suspend fun pageByClassification(companyId: String, classification: String, limit: Int, offset: Int): List<PartyEntity> =
        store.values.filter { it.companyId == companyId && it.classification == classification }
            .sortedBy { it.displayName.lowercase() }.drop(offset).take(limit)
    override suspend fun countByClassification(companyId: String, classification: String): Int =
        store.values.count { it.companyId == companyId && it.classification == classification }
    override suspend fun search(companyId: String, query: String, limit: Int, offset: Int): List<PartyEntity> =
        store.values.filter {
            it.companyId == companyId &&
                (it.displayName.contains(query, ignoreCase = true) || it.primaryPhoneNormalized?.contains(query) == true)
        }.sortedBy { it.displayName.lowercase() }.drop(offset).take(limit)
    override suspend fun countSearch(companyId: String, query: String): Int =
        store.values.count {
            it.companyId == companyId &&
                (it.displayName.contains(query, ignoreCase = true) || it.primaryPhoneNormalized?.contains(query) == true)
        }
}

private class FakePartySourceLinkDao : PartySourceLinkDao {
    val store = mutableMapOf<Triple<String, String, String>, PartySourceLinkEntity>()
    private fun key(companyId: String, sourceType: String, externalEntityId: String) = Triple(companyId, sourceType, externalEntityId)

    override suspend fun findByExternalKey(companyId: String, sourceType: String, externalEntityId: String): PartySourceLinkEntity? =
        store[key(companyId, sourceType, externalEntityId)]
    override suspend fun findByPartyId(companyId: String, partyId: String): List<PartySourceLinkEntity> =
        store.values.filter { it.companyId == companyId && it.partyId == partyId }
    override suspend fun upsert(entity: PartySourceLinkEntity) {
        store[key(entity.companyId, entity.sourceType, entity.externalEntityId)] = entity
    }
}

private class FakePartyFieldProvenanceDao : PartyFieldProvenanceDao {
    val store = mutableMapOf<Triple<String, String, String>, PartyFieldProvenanceEntity>()
    private fun key(companyId: String, partyId: String, fieldName: String) = Triple(companyId, partyId, fieldName)

    override suspend fun findField(companyId: String, partyId: String, fieldName: String): PartyFieldProvenanceEntity? =
        store[key(companyId, partyId, fieldName)]
    override suspend fun findAllForParty(companyId: String, partyId: String): List<PartyFieldProvenanceEntity> =
        store.values.filter { it.companyId == companyId && it.partyId == partyId }
    override suspend fun upsert(entity: PartyFieldProvenanceEntity) {
        store[key(entity.companyId, entity.partyId, entity.fieldName)] = entity
    }
}

private class FakePartyContactPersonDao : PartyContactPersonDao {
    val store = mutableMapOf<Pair<String, String>, PartyContactPersonEntity>()
    private fun key(companyId: String, contactPersonId: String) = companyId to contactPersonId

    override suspend fun findAllForParty(companyId: String, partyId: String): List<PartyContactPersonEntity> =
        store.values.filter { it.companyId == companyId && it.partyId == partyId }
            .sortedWith(compareByDescending<PartyContactPersonEntity> { it.isPrimary }.thenBy { it.name.lowercase() })
    override suspend fun findById(companyId: String, contactPersonId: String): PartyContactPersonEntity? =
        store[key(companyId, contactPersonId)]
    override suspend fun upsert(entity: PartyContactPersonEntity) {
        store[key(entity.companyId, entity.contactPersonId)] = entity
    }
}

private class FakeTagDao : TagDao {
    val tags = mutableMapOf<String, TagEntity>()
    val assignments = mutableSetOf<Triple<String, String, String>>()

    override suspend fun findById(tagId: String): TagEntity? = tags[tagId]
    override suspend fun findByNameUnderParent(name: String, parentTagId: String?): TagEntity? =
        tags.values.firstOrNull { it.name == name && it.parentTagId == parentTagId }
    override suspend fun findChildren(parentTagId: String?): List<TagEntity> = tags.values.filter { it.parentTagId == parentTagId }
    override suspend fun upsert(entity: TagEntity) {
        tags[entity.tagId] = entity
    }
    override suspend fun assign(entity: PartyTagCrossRefEntity) {
        assignments += Triple(entity.companyId, entity.partyId, entity.tagId)
    }
    override suspend fun unassign(companyId: String, partyId: String, tagId: String) {
        assignments -= Triple(companyId, partyId, tagId)
    }
    override suspend fun findTagsForParty(companyId: String, partyId: String): List<TagEntity> =
        assignments.filter { it.first == companyId && it.second == partyId }.mapNotNull { tags[it.third] }
    override suspend fun findPartyIdsForTag(companyId: String, tagId: String): List<String> =
        assignments.filter { it.first == companyId && it.third == tagId }.map { it.second }
}
