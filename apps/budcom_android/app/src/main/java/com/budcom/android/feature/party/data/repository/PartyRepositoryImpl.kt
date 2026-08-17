package com.budcom.android.feature.party.data.repository

import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.core.util.PhoneNumberNormalizer
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.party.data.local.PartyContactPersonDao
import com.budcom.android.feature.party.data.local.PartyDao
import com.budcom.android.feature.party.data.local.PartyEntity
import com.budcom.android.feature.party.data.local.PartyFieldProvenanceDao
import com.budcom.android.feature.party.data.local.PartyFieldProvenanceEntity
import com.budcom.android.feature.party.data.local.PartySourceLinkDao
import com.budcom.android.feature.party.data.local.PartySourceLinkEntity
import com.budcom.android.feature.party.data.local.TagDao
import com.budcom.android.feature.party.data.local.asColumn
import com.budcom.android.feature.party.data.local.toDomain
import com.budcom.android.feature.party.domain.model.EligibleLedgerSeed
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.LedgerIdentitySource
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyClassification
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyFieldNames
import com.budcom.android.feature.party.domain.model.PartyFieldProvenance
import com.budcom.android.feature.party.domain.model.PartyPage
import com.budcom.android.feature.party.domain.model.Tag
import com.budcom.android.feature.party.domain.repository.PartyRepository
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val SOURCE_TYPE_TALLY_LEDGER = "tally_ledger"
private const val GUID_PREFIX = "guid:"

@Singleton
class PartyRepositoryImpl @Inject constructor(
    private val partyDao: PartyDao,
    private val sourceLinkDao: PartySourceLinkDao,
    private val fieldProvenanceDao: PartyFieldProvenanceDao,
    private val contactPersonDao: PartyContactPersonDao,
    private val tagDao: TagDao,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
) : PartyRepository {

    override suspend fun getPartyById(companyId: String, partyId: String): Party? =
        withContext(dispatchers.io) { partyDao.findById(companyId, partyId)?.toDomain() }

    override suspend fun getPartyForLedger(companyId: String, ledgerId: String): Party? =
        withContext(dispatchers.io) {
            val link = sourceLinkDao.findByExternalKey(companyId, SOURCE_TYPE_TALLY_LEDGER, ledgerId) ?: return@withContext null
            partyDao.findById(companyId, link.partyId)?.toDomain()
        }

    override suspend fun listByClassification(
        companyId: String,
        classification: PartyClassification,
        page: Int,
        pageSize: Int,
    ): PartyPage = withContext(dispatchers.io) {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 200)
        val column = classification.asColumn()
        val total = partyDao.countByClassification(companyId, column)
        val items = partyDao.pageByClassification(companyId, column, safeSize, (safePage - 1) * safeSize)
        PartyPage(items.map { it.toDomain() }, safePage, safeSize, total)
    }

    override suspend fun searchParties(companyId: String, query: String, page: Int, pageSize: Int): PartyPage =
        withContext(dispatchers.io) {
            val safePage = page.coerceAtLeast(1)
            val safeSize = pageSize.coerceIn(1, 200)
            val normalized = query.trim()
            val total = partyDao.countSearch(companyId, normalized)
            val items = partyDao.search(companyId, normalized, safeSize, (safePage - 1) * safeSize)
            PartyPage(items.map { it.toDomain() }, safePage, safeSize, total)
        }

    override suspend fun getContactPersons(companyId: String, partyId: String): List<PartyContactPerson> =
        withContext(dispatchers.io) { contactPersonDao.findAllForParty(companyId, partyId).map { it.toDomain() } }

    override suspend fun getTagsForParty(companyId: String, partyId: String): List<Tag> =
        withContext(dispatchers.io) { tagDao.findTagsForParty(companyId, partyId).map { it.toDomain() } }

    override suspend fun getFieldProvenance(companyId: String, partyId: String): List<PartyFieldProvenance> =
        withContext(dispatchers.io) { fieldProvenanceDao.findAllForParty(companyId, partyId).map { it.toDomain() } }

    override suspend fun updateBudcomOnlyField(
        companyId: String,
        partyId: String,
        fieldName: String,
        value: String?,
    ): FieldProvenanceState = withContext(dispatchers.io) {
        val now = timeProvider.nowEpochMillis()
        val existing = fieldProvenanceDao.findField(companyId, partyId, fieldName)
        val newState = if (value.isNullOrBlank()) FieldProvenanceState.EmptyUnknown else FieldProvenanceState.BudcomOnlyPending
        fieldProvenanceDao.upsert(
            baseProvenance(existing, companyId, partyId, fieldName).copy(
                state = newState.asColumn(),
                budcomValue = value,
                updatedAt = now,
            ),
        )
        applyEffectiveFieldValue(companyId, partyId, fieldName, value, now)
        newState
    }

    override suspend fun confirmFieldFromTally(
        companyId: String,
        partyId: String,
        fieldName: String,
        tallyValue: String?,
    ): FieldProvenanceState = withContext(dispatchers.io) {
        val now = timeProvider.nowEpochMillis()
        val existing = fieldProvenanceDao.findField(companyId, partyId, fieldName)
        val pendingBudcomValue = existing?.budcomValue
        val newState = when {
            tallyValue.isNullOrBlank() -> FieldProvenanceState.EmptyUnknown
            pendingBudcomValue != null && pendingBudcomValue != tallyValue -> FieldProvenanceState.Conflict
            else -> FieldProvenanceState.ConfirmedFromTally
        }
        fieldProvenanceDao.upsert(
            baseProvenance(existing, companyId, partyId, fieldName).copy(
                state = newState.asColumn(),
                tallyValue = tallyValue,
                lastConfirmedAt = if (newState == FieldProvenanceState.ConfirmedFromTally) now else existing?.lastConfirmedAt,
                updatedAt = now,
            ),
        )
        if (newState == FieldProvenanceState.ConfirmedFromTally) {
            applyEffectiveFieldValue(companyId, partyId, fieldName, tallyValue, now)
        }
        newState
    }

    override suspend fun reconcilePartiesFromEligibleLedgers(
        companyId: String,
        seeds: List<EligibleLedgerSeed>,
    ): List<Party> = withContext(dispatchers.io) {
        seeds.map { seed -> reconcileOne(companyId, seed) }
    }

    private suspend fun reconcileOne(companyId: String, seed: EligibleLedgerSeed): Party {
        val now = timeProvider.nowEpochMillis()
        val identitySource = if (seed.ledgerId.startsWith(GUID_PREFIX)) LedgerIdentitySource.Guid else LedgerIdentitySource.Name

        val existingLink = sourceLinkDao.findByExternalKey(companyId, SOURCE_TYPE_TALLY_LEDGER, seed.ledgerId)
        val partyId = existingLink?.partyId ?: UUID.randomUUID().toString()
        val existingParty = partyDao.findById(companyId, partyId)

        partyDao.upsert(
            PartyEntity(
                companyId = companyId,
                partyId = partyId,
                displayName = seed.ledgerName,
                classification = seed.classification.asColumn(),
                primaryPhone = existingParty?.primaryPhone,
                primaryPhoneNormalized = existingParty?.primaryPhoneNormalized,
                primaryEmail = existingParty?.primaryEmail,
                addressLine1 = existingParty?.addressLine1,
                addressCity = existingParty?.addressCity,
                addressState = existingParty?.addressState,
                addressPincode = existingParty?.addressPincode,
                gstin = existingParty?.gstin,
                createdAt = existingParty?.createdAt ?: now,
                updatedAt = now,
            ),
        )

        sourceLinkDao.upsert(
            PartySourceLinkEntity(
                companyId = companyId,
                sourceType = SOURCE_TYPE_TALLY_LEDGER,
                externalEntityId = seed.ledgerId,
                partyId = partyId,
                sourceInstanceId = companyId,
                externalDisplayName = seed.ledgerName,
                identitySource = identitySource.asColumn(),
                lastConfirmedAt = now,
            ),
        )

        applyAliasPhoneSeeding(companyId, partyId, seed.alias, now)

        return partyDao.findById(companyId, partyId)!!.toDomain()
    }

    /**
     * Locked Alias-phone rule (architecture §8, spec §7.2): a Tally Alias that passes the strict
     * Indian-mobile validation may seed the BUDCOM phone field, but only non-destructively — an
     * existing different phone value is never silently overwritten, only flagged as a conflict.
     */
    private suspend fun applyAliasPhoneSeeding(companyId: String, partyId: String, alias: String?, now: Long) {
        val normalized = PhoneNumberNormalizer.normalizeIndianMobile(alias) ?: return
        val searchKey = PhoneNumberNormalizer.normalizeForSearch(normalized) ?: normalized.removePrefix("+91")
        val party = partyDao.findById(companyId, partyId) ?: return
        val existing = fieldProvenanceDao.findField(companyId, partyId, PartyFieldNames.PRIMARY_PHONE)

        when {
            party.primaryPhone.isNullOrBlank() -> {
                partyDao.upsert(party.copy(primaryPhone = alias, primaryPhoneNormalized = searchKey, updatedAt = now))
                fieldProvenanceDao.upsert(
                    baseProvenance(existing, companyId, partyId, PartyFieldNames.PRIMARY_PHONE).copy(
                        state = FieldProvenanceState.ConfirmedFromTally.asColumn(),
                        tallyValue = alias,
                        lastConfirmedAt = now,
                        updatedAt = now,
                    ),
                )
            }

            party.primaryPhone == alias -> {
                // Same value already effective: a later Tally sync returning the same value is
                // exactly the confirmation trigger, so promote pending/exported to confirmed.
                fieldProvenanceDao.upsert(
                    baseProvenance(existing, companyId, partyId, PartyFieldNames.PRIMARY_PHONE).copy(
                        state = FieldProvenanceState.ConfirmedFromTally.asColumn(),
                        tallyValue = alias,
                        lastConfirmedAt = now,
                        updatedAt = now,
                    ),
                )
            }

            else -> {
                // A different phone value is already effective -- never overwrite silently.
                fieldProvenanceDao.upsert(
                    baseProvenance(existing, companyId, partyId, PartyFieldNames.PRIMARY_PHONE).copy(
                        state = FieldProvenanceState.Conflict.asColumn(),
                        tallyValue = alias,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    private suspend fun applyEffectiveFieldValue(companyId: String, partyId: String, fieldName: String, value: String?, now: Long) {
        val party = partyDao.findById(companyId, partyId) ?: return
        val updated = when (fieldName) {
            PartyFieldNames.PRIMARY_PHONE -> party.copy(
                primaryPhone = value,
                primaryPhoneNormalized = PhoneNumberNormalizer.normalizeForSearch(value),
                updatedAt = now,
            )
            PartyFieldNames.PRIMARY_EMAIL -> party.copy(primaryEmail = value, updatedAt = now)
            PartyFieldNames.ADDRESS_LINE1 -> party.copy(addressLine1 = value, updatedAt = now)
            PartyFieldNames.ADDRESS_CITY -> party.copy(addressCity = value, updatedAt = now)
            PartyFieldNames.ADDRESS_STATE -> party.copy(addressState = value, updatedAt = now)
            PartyFieldNames.ADDRESS_PINCODE -> party.copy(addressPincode = value, updatedAt = now)
            PartyFieldNames.GSTIN -> party.copy(gstin = value, updatedAt = now)
            else -> return
        }
        partyDao.upsert(updated)
    }

    private fun baseProvenance(
        existing: PartyFieldProvenanceEntity?,
        companyId: String,
        partyId: String,
        fieldName: String,
    ): PartyFieldProvenanceEntity = existing ?: PartyFieldProvenanceEntity(
        companyId = companyId,
        partyId = partyId,
        fieldName = fieldName,
        state = FieldProvenanceState.EmptyUnknown.asColumn(),
        tallyValue = null,
        budcomValue = null,
        lastConfirmedAt = null,
        lastExportedAt = null,
        updatedAt = 0L,
    )
}

private fun PartyClassification.asColumn(): String = name.lowercase()
