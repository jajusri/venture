package com.budcom.android.feature.party.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budcom.android.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Covers [PartySourceLinkDao], [PartyFieldProvenanceDao] and [PartyContactPersonDao] together
 * since each is small and they share the same real-Room-in-memory setup. */
@RunWith(AndroidJUnit4::class)
class PartyRelatedDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var sourceLinkDao: PartySourceLinkDao
    private lateinit var fieldProvenanceDao: PartyFieldProvenanceDao
    private lateinit var contactPersonDao: PartyContactPersonDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        sourceLinkDao = db.partySourceLinkDao()
        fieldProvenanceDao = db.partyFieldProvenanceDao()
        contactPersonDao = db.partyContactPersonDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---------------- PartySourceLinkDao ----------------

    @Test
    fun sourceLink_naturalKeyResolvesDeterministicallyToTheSamePartyId() = runBlocking {
        sourceLinkDao.upsert(link("co-a", "guid:abc", "party-1", "ABC Traders"))
        assertEquals("party-1", sourceLinkDao.findByExternalKey("co-a", "tally_ledger", "guid:abc")?.partyId)

        // Simulated rename: same external key, different display name -> same partyId preserved
        // by upserting with the same natural key (companyId, sourceType, externalEntityId).
        sourceLinkDao.upsert(link("co-a", "guid:abc", "party-1", "ABC Traders Renamed"))
        val resolved = sourceLinkDao.findByExternalKey("co-a", "tally_ledger", "guid:abc")
        assertEquals("party-1", resolved?.partyId)
        assertEquals("ABC Traders Renamed", resolved?.externalDisplayName)
        assertEquals(1, sourceLinkDao.findByPartyId("co-a", "party-1").size)
    }

    @Test
    fun sourceLink_sameExternalIdInDifferentCompaniesDoesNotCollide() = runBlocking {
        sourceLinkDao.upsert(link("co-a", "guid:same", "party-a", "Same"))
        sourceLinkDao.upsert(link("co-b", "guid:same", "party-b", "Same"))

        assertEquals("party-a", sourceLinkDao.findByExternalKey("co-a", "tally_ledger", "guid:same")?.partyId)
        assertEquals("party-b", sourceLinkDao.findByExternalKey("co-b", "tally_ledger", "guid:same")?.partyId)
    }

    @Test
    fun sourceLink_unknownKeyReturnsNull() = runBlocking {
        assertNull(sourceLinkDao.findByExternalKey("co-a", "tally_ledger", "guid:missing"))
    }

    private fun link(companyId: String, externalId: String, partyId: String, displayName: String) = PartySourceLinkEntity(
        companyId = companyId,
        sourceType = "tally_ledger",
        externalEntityId = externalId,
        partyId = partyId,
        sourceInstanceId = companyId,
        externalDisplayName = displayName,
        identitySource = "guid",
        lastConfirmedAt = 1_000L,
    )

    // ---------------- PartyFieldProvenanceDao ----------------

    @Test
    fun fieldProvenance_everyRequiredStateRoundTrips() = runBlocking {
        val states = listOf("confirmed_from_tally", "budcom_only_pending", "export_ready", "exported", "conflict", "empty_unknown")
        states.forEachIndexed { index, state ->
            fieldProvenanceDao.upsert(provenance("co-a", "party-1", "field$index", state))
        }
        states.forEachIndexed { index, state ->
            assertEquals(state, fieldProvenanceDao.findField("co-a", "party-1", "field$index")?.state)
        }
        assertEquals(states.size, fieldProvenanceDao.findAllForParty("co-a", "party-1").size)
    }

    @Test
    fun fieldProvenance_upsertReplacesOnSameCompositeKey() = runBlocking {
        fieldProvenanceDao.upsert(provenance("co-a", "party-1", "primaryPhone", "budcom_only_pending", budcomValue = "9111111111"))
        fieldProvenanceDao.upsert(provenance("co-a", "party-1", "primaryPhone", "confirmed_from_tally", tallyValue = "9876543210"))

        val result = fieldProvenanceDao.findField("co-a", "party-1", "primaryPhone")
        assertEquals("confirmed_from_tally", result?.state)
        assertEquals("9876543210", result?.tallyValue)
        assertEquals(1, fieldProvenanceDao.findAllForParty("co-a", "party-1").size)
    }

    private fun provenance(
        companyId: String,
        partyId: String,
        fieldName: String,
        state: String,
        tallyValue: String? = null,
        budcomValue: String? = null,
    ) = PartyFieldProvenanceEntity(
        companyId = companyId,
        partyId = partyId,
        fieldName = fieldName,
        state = state,
        tallyValue = tallyValue,
        budcomValue = budcomValue,
        lastConfirmedAt = if (state == "confirmed_from_tally") 1_000L else null,
        lastExportedAt = if (state == "exported") 1_000L else null,
        updatedAt = 1_000L,
    )

    // ---------------- PartyContactPersonDao ----------------

    @Test
    fun contactPersons_multiplePeopleWithPrimaryOrderedFirst() = runBlocking {
        contactPersonDao.upsert(contact("co-a", "cp-1", "party-1", "Purchase Person", isPrimary = false))
        contactPersonDao.upsert(contact("co-a", "cp-2", "party-1", "Owner", isPrimary = true))
        contactPersonDao.upsert(contact("co-a", "cp-3", "party-1", "Accounts", isPrimary = false, provenance = "budcom_only"))

        val all = contactPersonDao.findAllForParty("co-a", "party-1")
        assertEquals(3, all.size)
        assertTrue(all.first().isPrimary)
        assertEquals("Owner", all.first().name)
        assertTrue(all.any { it.provenance == "budcom_only" })
    }

    @Test
    fun contactPersons_areCompanyAndPartyScoped() = runBlocking {
        contactPersonDao.upsert(contact("co-a", "cp-1", "party-1", "Owner A", isPrimary = true))
        contactPersonDao.upsert(contact("co-b", "cp-2", "party-1", "Owner B", isPrimary = true))
        contactPersonDao.upsert(contact("co-a", "cp-3", "party-2", "Owner C", isPrimary = true))

        assertEquals(1, contactPersonDao.findAllForParty("co-a", "party-1").size)
        assertEquals(1, contactPersonDao.findAllForParty("co-b", "party-1").size)
        assertEquals(1, contactPersonDao.findAllForParty("co-a", "party-2").size)
    }

    private fun contact(
        companyId: String,
        contactPersonId: String,
        partyId: String,
        name: String,
        isPrimary: Boolean,
        provenance: String = "confirmed_from_tally",
    ) = PartyContactPersonEntity(
        companyId = companyId,
        contactPersonId = contactPersonId,
        partyId = partyId,
        name = name,
        designation = null,
        mobile = null,
        mobileNormalized = null,
        whatsappNumber = null,
        email = null,
        isPrimary = isPrimary,
        provenance = provenance,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )
}
