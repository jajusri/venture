package com.budcom.android.feature.party.domain.usecase

import com.budcom.android.feature.masterdata.ledger.domain.model.AmountSide
import com.budcom.android.feature.masterdata.ledger.domain.model.Ledger
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerDataQuality
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatus
import com.budcom.android.feature.masterdata.ledger.domain.model.MoneyAmount
import com.budcom.android.feature.masterdata.ledger.domain.port.LedgerSnapshotPort
import com.budcom.android.feature.party.domain.model.EligibleLedgerSeed
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyClassification
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyFieldProvenance
import com.budcom.android.feature.party.domain.model.PartyPage
import com.budcom.android.feature.party.domain.model.Tag
import com.budcom.android.feature.party.domain.repository.PartyRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcilePartiesFromLedgersUseCaseTest {

    private fun ledger(id: String, name: String, parentGroup: String?, alias: String? = null) = Ledger(
        id = id,
        name = name,
        alias = alias,
        parentGroup = parentGroup,
        status = LedgerStatus.Active,
        closingBalance = MoneyAmount("100", "INR", AmountSide.Dr),
        dataQuality = LedgerDataQuality.Complete,
        syncedAt = "t",
    )

    @Test
    fun `only ledgers under an eligible group are handed to the repository`() = runTest {
        val port = FakeLedgerSnapshotPort(
            listOf(
                ledger("guid:1", "ABC Traders", "Sundry Debtors"),
                ledger("guid:2", "XYZ Suppliers", "Sundry Creditors"),
                ledger("guid:3", "Cash", "Current Assets"),
                ledger("guid:4", "Bank OD", "Bank Accounts"),
            ),
        )
        val repository = FakePartyRepository()
        val useCase = ReconcilePartiesFromLedgersUseCase(port, repository)

        useCase("co-1")

        assertEquals(2, repository.lastSeeds?.size)
        assertEquals(
            setOf("guid:1" to PartyClassification.Customer, "guid:2" to PartyClassification.Supplier),
            repository.lastSeeds!!.map { it.ledgerId to it.classification }.toSet(),
        )
    }

    @Test
    fun `no eligible ledgers never calls the repository`() = runTest {
        val port = FakeLedgerSnapshotPort(listOf(ledger("guid:1", "Cash", "Current Assets")))
        val repository = FakePartyRepository()

        val result = ReconcilePartiesFromLedgersUseCase(port, repository)("co-1")

        assertTrue(result.isEmpty())
        assertEquals(null, repository.lastSeeds)
    }

    @Test
    fun `alias is passed through unchanged to the repository for the Alias-phone rule`() = runTest {
        val port = FakeLedgerSnapshotPort(listOf(ledger("guid:1", "ABC Traders", "Sundry Debtors", alias = "9876543210")))
        val repository = FakePartyRepository()

        ReconcilePartiesFromLedgersUseCase(port, repository)("co-1")

        assertEquals("9876543210", repository.lastSeeds!!.single().alias)
    }
}

private class FakeLedgerSnapshotPort(private val ledgers: List<Ledger>) : LedgerSnapshotPort {
    override suspend fun getCachedLedgers(companyId: String): List<Ledger> = ledgers
}

private class FakePartyRepository : PartyRepository {
    var lastSeeds: List<EligibleLedgerSeed>? = null

    override suspend fun getPartyById(companyId: String, partyId: String): Party? = null
    override suspend fun getPartyForLedger(companyId: String, ledgerId: String): Party? = null
    override suspend fun listByClassification(companyId: String, classification: PartyClassification, page: Int, pageSize: Int) =
        PartyPage(emptyList(), page, pageSize, 0)
    override suspend fun searchParties(companyId: String, query: String, page: Int, pageSize: Int) =
        PartyPage(emptyList(), page, pageSize, 0)
    override suspend fun getContactPersons(companyId: String, partyId: String): List<PartyContactPerson> = emptyList()
    override suspend fun getTagsForParty(companyId: String, partyId: String): List<Tag> = emptyList()
    override suspend fun getFieldProvenance(companyId: String, partyId: String): List<PartyFieldProvenance> = emptyList()
    override suspend fun updateBudcomOnlyField(companyId: String, partyId: String, fieldName: String, value: String?) =
        FieldProvenanceState.BudcomOnlyPending
    override suspend fun confirmFieldFromTally(companyId: String, partyId: String, fieldName: String, tallyValue: String?) =
        FieldProvenanceState.ConfirmedFromTally

    override suspend fun reconcilePartiesFromEligibleLedgers(companyId: String, seeds: List<EligibleLedgerSeed>): List<Party> {
        lastSeeds = seeds
        return seeds.map {
            Party(
                companyId = companyId,
                partyId = "party-${it.ledgerId}",
                displayName = it.ledgerName,
                classification = it.classification,
                primaryPhone = null,
                primaryPhoneNormalized = null,
                primaryEmail = null,
                addressLine1 = null,
                addressCity = null,
                addressState = null,
                addressPincode = null,
                gstin = null,
                createdAt = 0L,
                updatedAt = 0L,
            )
        }
    }
}
