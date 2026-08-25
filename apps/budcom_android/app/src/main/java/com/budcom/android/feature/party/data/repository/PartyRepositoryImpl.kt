package com.budcom.android.feature.party.data.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.core.util.AliasSearchClassifier
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.core.util.PhoneNumberNormalizer
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerContactDetails
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import com.budcom.android.feature.masterdata.ledger.domain.port.SearchLedgersPort
import com.budcom.android.feature.party.data.local.PartyContactPersonDao
import com.budcom.android.feature.party.data.local.PartyDao
import com.budcom.android.feature.party.data.local.PartyEntity
import com.budcom.android.feature.party.data.local.PartyContactPersonEntity
import com.budcom.android.feature.party.data.local.PartyExportEventDao
import com.budcom.android.feature.party.data.local.PartyExportEventEntity
import com.budcom.android.feature.party.data.local.PartyFieldProvenanceDao
import com.budcom.android.feature.party.data.local.PartyFieldProvenanceEntity
import com.budcom.android.feature.party.data.local.PartyIssueDao
import com.budcom.android.feature.party.data.local.PartyIssueEntity
import com.budcom.android.feature.party.data.local.PartyNoteDao
import com.budcom.android.feature.party.data.local.PartyNoteEntity
import com.budcom.android.feature.party.data.local.PartySourceLinkDao
import com.budcom.android.feature.party.data.local.PartySourceLinkEntity
import com.budcom.android.feature.party.data.local.PartyTagAssignmentRow
import com.budcom.android.feature.party.data.local.PartyTagCrossRefEntity
import com.budcom.android.feature.party.data.local.PartyTimelineDao
import com.budcom.android.feature.party.data.local.TagDao
import com.budcom.android.feature.party.data.local.TagEntity
import com.budcom.android.feature.party.data.local.asColumn
import com.budcom.android.feature.party.data.local.toDomain
import com.budcom.android.feature.party.data.local.toFieldProvenanceState
import com.budcom.android.feature.party.domain.model.BulkContactSeedResult
import com.budcom.android.feature.party.domain.model.EligibleLedgerSeed
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.IssueActivitySummary
import com.budcom.android.feature.party.domain.model.IssueStatus
import com.budcom.android.feature.party.domain.model.LedgerIdentitySource
import com.budcom.android.feature.party.domain.model.NoteType
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyClassification
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyExportEvent
import com.budcom.android.feature.party.domain.model.PartyFieldNames
import com.budcom.android.feature.party.domain.model.PartyFieldProvenance
import com.budcom.android.feature.party.domain.model.PartyIssue
import com.budcom.android.feature.party.domain.model.PartyNote
import com.budcom.android.feature.party.domain.model.PartyNotePage
import com.budcom.android.feature.party.domain.model.PartyPage
import com.budcom.android.feature.party.domain.model.PartySourceLink
import com.budcom.android.feature.party.domain.model.ProspectDraft
import com.budcom.android.feature.party.domain.model.Tag
import com.budcom.android.feature.party.domain.model.TallyExportFieldMapping
import com.budcom.android.feature.party.domain.model.TallyFieldExportCandidate
import com.budcom.android.feature.party.domain.model.TimelineEntryPage
import com.budcom.android.feature.party.domain.repository.PartyRepository
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val SOURCE_TYPE_TALLY_LEDGER = "tally_ledger"
private const val GUID_PREFIX = "guid:"

private enum class ContactFieldSeedOutcome { Filled, Confirmed, Conflicted, Skipped }

@Singleton
class PartyRepositoryImpl @Inject constructor(
    private val partyDao: PartyDao,
    private val sourceLinkDao: PartySourceLinkDao,
    private val fieldProvenanceDao: PartyFieldProvenanceDao,
    private val contactPersonDao: PartyContactPersonDao,
    private val tagDao: TagDao,
    private val noteDao: PartyNoteDao,
    private val exportEventDao: PartyExportEventDao,
    private val issueDao: PartyIssueDao,
    private val timelineDao: PartyTimelineDao,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
    private val searchLedgersPort: SearchLedgersPort,
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
        val (total, items) = partyDao.pageWithCountByClassification(companyId, column, safeSize, (safePage - 1) * safeSize)
        PartyPage(items.map { it.toDomain() }, safePage, safeSize, total)
    }

    override suspend fun searchParties(
        companyId: String,
        query: String,
        classification: PartyClassification?,
        page: Int,
        pageSize: Int,
    ): PartyPage = withContext(dispatchers.io) {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 200)
        val normalized = query.trim()
        val column = classification?.asColumn()
        val (total, items) = partyDao.pageWithCountSearch(companyId, normalized, column, safeSize, (safePage - 1) * safeSize)
        val domainItems = items.map { it.toDomain() }

        // Part B (Connect Alias Intelligence): a 1-5 digit numeric query is a ledger-lookup
        // shortcut, not a phone/name search -- Party itself stores no Alias column (see
        // PartyModels.kt), so an exact match is resolved via the existing SearchLedgersPort +
        // PartySourceLink chain (the same cross-feature port ReconcilePartiesFromLedgersUseCase's
        // sibling code already uses) rather than duplicating Alias data onto Party or introducing
        // a cross-table SQL JOIN, which this DAO deliberately avoids elsewhere (see
        // PartyDao.findByIds's own doc comment). Only attempted for the first page: this is a
        // "jump to that ledger's Party" shortcut, not a general ranking signal for later pages.
        if (safePage != 1 || !AliasSearchClassifier.isShortNumericAlias(normalized)) {
            return@withContext PartyPage(domainItems, safePage, safeSize, total)
        }
        val shortcutParty = resolveAliasShortcutParty(companyId, normalized, column)
            ?: return@withContext PartyPage(domainItems, safePage, safeSize, total)

        val merged = (listOf(shortcutParty) + domainItems).distinctBy { it.partyId }
        val addedCount = merged.size - domainItems.size
        PartyPage(merged.take(safeSize), safePage, safeSize, total + addedCount)
    }

    /**
     * Resolves the single Party, if any, whose linked Tally ledger has an Alias exactly equal to
     * [normalizedQuery] -- company-scoped throughout ([searchLedgersPort] itself reads only the
     * currently-selected company's Room cache; [sourceLinkDao]/[partyDao] are both explicitly
     * scoped by [companyId]). Returns null on no match, no source link (a ledger not yet eligible
     * as a Party -- see [com.budcom.android.feature.party.domain.model.LedgerPartyEligibilityPolicy]),
     * or a classification mismatch, so the shortcut never surfaces a Party the caller's own filter
     * would otherwise exclude.
     */
    private suspend fun resolveAliasShortcutParty(
        companyId: String,
        normalizedQuery: String,
        classificationColumn: String?,
    ): Party? {
        val ledgerResult = searchLedgersPort.search(LedgerQuery(text = normalizedQuery))
        val exactLedger = (ledgerResult as? AppResult.Success)?.value?.items
            ?.firstOrNull { it.alias == normalizedQuery }
            ?: return null
        val link = sourceLinkDao.findByExternalKey(companyId, SOURCE_TYPE_TALLY_LEDGER, exactLedger.id) ?: return null
        val party = partyDao.findById(companyId, link.partyId)?.toDomain() ?: return null
        if (classificationColumn != null && party.classification.asColumn() != classificationColumn) return null
        return party
    }

    override suspend fun getContactPersons(companyId: String, partyId: String): List<PartyContactPerson> =
        withContext(dispatchers.io) { contactPersonDao.findAllForParty(companyId, partyId).map { it.toDomain() } }

    override suspend fun getTagsForParty(companyId: String, partyId: String): List<Tag> =
        withContext(dispatchers.io) { tagDao.findTagsForParty(companyId, partyId).map { it.toDomain() } }

    override suspend fun getSourceLinksForCompany(companyId: String): List<PartySourceLink> =
        withContext(dispatchers.io) { sourceLinkDao.findAllForCompany(companyId).map { it.toDomain() } }

    override suspend fun getTagsForCompany(companyId: String): Map<String, List<Tag>> =
        withContext(dispatchers.io) {
            tagDao.findTagsForCompany(companyId)
                .groupBy(keySelector = { it.partyId }, valueTransform = { it.toTagDomain() })
        }

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
        seeds.mapIndexed { index, seed ->
            if (index % 200 == 0) {
                timber.log.Timber.tag("TD041").d("reconcile progress $index/${seeds.size} at=${System.currentTimeMillis()}")
            }
            reconcileOne(companyId, seed)
        }
    }

    /**
     * Field-generic version of [applyAliasPhoneSeeding]'s exact fill-if-empty/re-confirm-if-same/
     * flag-conflict-if-different-and-leave-untouched pattern, applied across every ledger fetched
     * in one bulk contact-details pass. Uses [canonicalizeForComparison] for equality (unlike
     * phone, email/state/GSTIN legitimately vary in case/formatting between Tally and BUDCOM
     * without being a real conflict).
     */
    override suspend fun applyLedgerContactDetailsBulk(
        companyId: String,
        items: List<LedgerContactDetails>,
    ): BulkContactSeedResult = withContext(dispatchers.io) {
        val partyIdByLedgerId = sourceLinkDao.findAllForCompany(companyId)
            .filter { it.sourceType == SOURCE_TYPE_TALLY_LEDGER }
            .associate { it.externalEntityId to it.partyId }

        var matched = 0
        var unmatched = 0
        var filled = 0
        var confirmed = 0
        var conflicted = 0

        items.forEach { item ->
            val partyId = partyIdByLedgerId[item.ledgerId]
            if (partyId == null) {
                unmatched++
                return@forEach
            }
            matched++
            val now = timeProvider.nowEpochMillis()
            val fieldsToSeed = listOf(
                PartyFieldNames.PRIMARY_EMAIL to item.email,
                PartyFieldNames.ADDRESS_LINE1 to item.address,
                PartyFieldNames.ADDRESS_STATE to item.state,
                PartyFieldNames.ADDRESS_PINCODE to item.pincode,
                PartyFieldNames.GSTIN to item.gstin,
            )
            fieldsToSeed.forEach { (fieldName, tallyValue) ->
                when (applyContactFieldSeed(companyId, partyId, fieldName, tallyValue, now)) {
                    ContactFieldSeedOutcome.Filled -> filled++
                    ContactFieldSeedOutcome.Confirmed -> confirmed++
                    ContactFieldSeedOutcome.Conflicted -> conflicted++
                    ContactFieldSeedOutcome.Skipped -> Unit
                }
            }
        }

        BulkContactSeedResult(
            matchedLedgers = matched,
            unmatchedLedgers = unmatched,
            fieldsFilled = filled,
            fieldsConfirmed = confirmed,
            fieldsConflicted = conflicted,
        )
    }

    private suspend fun applyContactFieldSeed(
        companyId: String,
        partyId: String,
        fieldName: String,
        tallyValue: String?,
        now: Long,
    ): ContactFieldSeedOutcome {
        if (tallyValue.isNullOrBlank()) return ContactFieldSeedOutcome.Skipped
        val party = partyDao.findById(companyId, partyId) ?: return ContactFieldSeedOutcome.Skipped
        val currentValue = party.currentValueFor(fieldName)
        val existing = fieldProvenanceDao.findField(companyId, partyId, fieldName)

        return when {
            currentValue.isNullOrBlank() -> {
                fieldProvenanceDao.upsert(
                    baseProvenance(existing, companyId, partyId, fieldName).copy(
                        state = FieldProvenanceState.ConfirmedFromTally.asColumn(),
                        tallyValue = tallyValue,
                        lastConfirmedAt = now,
                        updatedAt = now,
                    ),
                )
                applyEffectiveFieldValue(companyId, partyId, fieldName, tallyValue, now)
                ContactFieldSeedOutcome.Filled
            }

            canonicalizeForComparison(fieldName, currentValue) == canonicalizeForComparison(fieldName, tallyValue) -> {
                fieldProvenanceDao.upsert(
                    baseProvenance(existing, companyId, partyId, fieldName).copy(
                        state = FieldProvenanceState.ConfirmedFromTally.asColumn(),
                        tallyValue = tallyValue,
                        lastConfirmedAt = now,
                        updatedAt = now,
                    ),
                )
                ContactFieldSeedOutcome.Confirmed
            }

            else -> {
                // A different value is already effective -- never overwrite silently, matching
                // applyAliasPhoneSeeding's own rule.
                fieldProvenanceDao.upsert(
                    baseProvenance(existing, companyId, partyId, fieldName).copy(
                        state = FieldProvenanceState.Conflict.asColumn(),
                        tallyValue = tallyValue,
                        updatedAt = now,
                    ),
                )
                ContactFieldSeedOutcome.Conflicted
            }
        }
    }

    private fun PartyEntity.currentValueFor(fieldName: String): String? = when (fieldName) {
        PartyFieldNames.PRIMARY_EMAIL -> primaryEmail
        PartyFieldNames.ADDRESS_LINE1 -> addressLine1
        PartyFieldNames.ADDRESS_STATE -> addressState
        PartyFieldNames.ADDRESS_PINCODE -> addressPincode
        PartyFieldNames.GSTIN -> gstin
        else -> null
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

    // ---- MVP-1.1-C: Prospects, contact persons, tags, notes ----

    override suspend fun createProspect(companyId: String, draft: ProspectDraft): Party = withContext(dispatchers.io) {
        val now = timeProvider.nowEpochMillis()
        val partyId = UUID.randomUUID().toString()

        partyDao.upsert(
            PartyEntity(
                companyId = companyId,
                partyId = partyId,
                displayName = draft.displayName,
                classification = PartyClassification.Prospect.asColumn(),
                primaryPhone = draft.phone,
                primaryPhoneNormalized = PhoneNumberNormalizer.normalizeForSearch(draft.phone),
                primaryEmail = draft.email,
                addressLine1 = draft.addressLine1,
                addressCity = draft.addressCity,
                addressState = draft.addressState,
                addressPincode = draft.addressPincode,
                gstin = null,
                createdAt = now,
                updatedAt = now,
            ),
        )

        // No Tally source link is created — a Prospect is BUDCOM-native by definition (spec
        // §4.1). Every provided Tally-compatible field still gets a provenance row so a future
        // Tally link (architecture §27/§8) has a coherent pending starting point rather than an
        // untracked one.
        listOfNotNull(
            draft.phone?.let { PartyFieldNames.PRIMARY_PHONE to it },
            draft.email?.let { PartyFieldNames.PRIMARY_EMAIL to it },
            draft.addressLine1?.let { PartyFieldNames.ADDRESS_LINE1 to it },
            draft.addressCity?.let { PartyFieldNames.ADDRESS_CITY to it },
            draft.addressState?.let { PartyFieldNames.ADDRESS_STATE to it },
            draft.addressPincode?.let { PartyFieldNames.ADDRESS_PINCODE to it },
        ).forEach { (fieldName, value) ->
            fieldProvenanceDao.upsert(
                baseProvenance(null, companyId, partyId, fieldName).copy(
                    state = FieldProvenanceState.BudcomOnlyPending.asColumn(),
                    budcomValue = value,
                    updatedAt = now,
                ),
            )
        }

        draft.tagIds.forEach { tagId -> tagDao.assign(PartyTagCrossRefEntity(companyId, partyId, tagId, now)) }
        draft.note?.takeIf(String::isNotBlank)?.let { body ->
            noteDao.upsert(PartyNoteEntity(companyId, UUID.randomUUID().toString(), partyId, body, null, now, now))
        }

        partyDao.findById(companyId, partyId)!!.toDomain()
    }

    override suspend fun getSourceLinkForParty(companyId: String, partyId: String): PartySourceLink? =
        withContext(dispatchers.io) { sourceLinkDao.findByPartyId(companyId, partyId).firstOrNull()?.toDomain() }

    // ---- Transaction Mode integration point (docs/architecture/BUDCOM-TRANSACTION-MODE-ARCHITECTURE.md §6) ----

    override suspend fun promoteProspectToCustomer(companyId: String, partyId: String): Party? = withContext(dispatchers.io) {
        val existing = partyDao.findById(companyId, partyId) ?: return@withContext null
        if (existing.classification != PartyClassification.Prospect.asColumn()) {
            // Already Customer/Supplier/Other — idempotent no-op, not an error (see interface doc).
            return@withContext existing.toDomain()
        }
        val updated = existing.copy(
            classification = PartyClassification.Customer.asColumn(),
            updatedAt = timeProvider.nowEpochMillis(),
        )
        partyDao.upsert(updated)
        updated.toDomain()
    }

    override suspend fun upsertContactPerson(
        companyId: String,
        partyId: String,
        contactPersonId: String?,
        name: String,
        designation: String?,
        mobile: String?,
        whatsappNumber: String?,
        email: String?,
        isPrimary: Boolean,
    ): PartyContactPerson = withContext(dispatchers.io) {
        val now = timeProvider.nowEpochMillis()
        val id = contactPersonId ?: UUID.randomUUID().toString()
        val existing = contactPersonId?.let { contactPersonDao.findById(companyId, it) }

        if (isPrimary) {
            // At most one primary contact per Party — demote every other primary first, never
            // silently leaving two.
            contactPersonDao.findAllForParty(companyId, partyId)
                .filter { it.isPrimary && it.contactPersonId != id }
                .forEach { contactPersonDao.upsert(it.copy(isPrimary = false, updatedAt = now)) }
        }

        val entity = PartyContactPersonEntity(
            companyId = companyId,
            contactPersonId = id,
            partyId = partyId,
            name = name,
            designation = designation,
            mobile = mobile,
            mobileNormalized = PhoneNumberNormalizer.normalizeForSearch(mobile),
            whatsappNumber = whatsappNumber,
            email = email,
            isPrimary = isPrimary,
            provenance = FieldProvenanceState.BudcomOnlyPending.asColumn(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        contactPersonDao.upsert(entity)
        entity.toDomain()
    }

    override suspend fun deleteContactPerson(companyId: String, contactPersonId: String) =
        withContext(dispatchers.io) { contactPersonDao.delete(companyId, contactPersonId) }

    override suspend fun getAllTags(): List<Tag> =
        withContext(dispatchers.io) { tagDao.findAll().map { it.toDomain() } }

    override suspend fun createOrGetTag(name: String, parentTagId: String?): Tag = withContext(dispatchers.io) {
        tagDao.findByNameUnderParent(name, parentTagId)?.toDomain()?.let { return@withContext it }
        val parentPath = parentTagId?.let { tagDao.findById(it)?.path }
        val path = if (parentPath != null) "$parentPath/$name" else name
        val entity = TagEntity(
            tagId = UUID.randomUUID().toString(),
            parentTagId = parentTagId,
            name = name,
            path = path,
            createdAt = timeProvider.nowEpochMillis(),
        )
        tagDao.upsert(entity)
        entity.toDomain()
    }

    override suspend fun assignTag(companyId: String, partyId: String, tagId: String) =
        withContext(dispatchers.io) {
            tagDao.assign(PartyTagCrossRefEntity(companyId, partyId, tagId, timeProvider.nowEpochMillis()))
        }

    override suspend fun unassignTag(companyId: String, partyId: String, tagId: String) =
        withContext(dispatchers.io) { tagDao.unassign(companyId, partyId, tagId) }

    override suspend fun addNote(
        companyId: String,
        partyId: String,
        body: String,
        linkedVoucherId: String?,
        type: NoteType,
        dueAt: Long?,
        issueId: String?,
    ): PartyNote = withContext(dispatchers.io) {
        val now = timeProvider.nowEpochMillis()
        val entity = PartyNoteEntity(
            companyId = companyId,
            noteId = UUID.randomUUID().toString(),
            partyId = partyId,
            body = body,
            linkedVoucherId = linkedVoucherId,
            createdAt = now,
            updatedAt = now,
            type = type.asColumn(),
            dueAt = dueAt,
            completedAt = null,
            issueId = issueId,
        )
        noteDao.upsert(entity)
        entity.toDomain()
    }

    override suspend fun editNote(
        companyId: String,
        noteId: String,
        body: String,
        type: NoteType,
        dueAt: Long?,
        issueId: String?,
    ): PartyNote? = withContext(dispatchers.io) {
        val existing = noteDao.findById(companyId, noteId) ?: return@withContext null
        val updated = existing.copy(
            body = body,
            type = type.asColumn(),
            dueAt = dueAt,
            issueId = issueId,
            updatedAt = timeProvider.nowEpochMillis(),
        )
        noteDao.upsert(updated)
        updated.toDomain()
    }

    override suspend fun setNoteCompletion(companyId: String, noteId: String, completedAt: Long?): PartyNote? =
        withContext(dispatchers.io) {
            val existing = noteDao.findById(companyId, noteId) ?: return@withContext null
            val updated = existing.copy(completedAt = completedAt, updatedAt = timeProvider.nowEpochMillis())
            noteDao.upsert(updated)
            updated.toDomain()
        }

    override suspend fun deleteNote(companyId: String, noteId: String) =
        withContext(dispatchers.io) { noteDao.delete(companyId, noteId) }

    override suspend fun getNotesForParty(companyId: String, partyId: String, page: Int, pageSize: Int): PartyNotePage =
        withContext(dispatchers.io) {
            val safePage = page.coerceAtLeast(1)
            val safeSize = pageSize.coerceIn(1, 100)
            val total = noteDao.countForParty(companyId, partyId)
            val items = noteDao.pageForParty(companyId, partyId, safeSize, (safePage - 1) * safeSize)
            PartyNotePage(items.map { it.toDomain() }, safePage, safeSize, total)
        }

    // ---- MVP-1.2-B: Relationship Timeline ----

    override suspend fun getTimelineForParty(
        companyId: String,
        partyId: String,
        page: Int,
        pageSize: Int,
        issueId: String?,
    ): TimelineEntryPage = withContext(dispatchers.io) {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 100)
        val total = timelineDao.countTimelineForParty(companyId, partyId, issueId)
        val items = timelineDao.pageTimelineForParty(companyId, partyId, issueId, safeSize, (safePage - 1) * safeSize)
        TimelineEntryPage(items.map { it.toDomain() }, safePage, safeSize, total)
    }

    // ---- MVP-1.2-A: party issues ----

    override suspend fun createIssue(companyId: String, partyId: String, title: String): PartyIssue =
        withContext(dispatchers.io) {
            val now = timeProvider.nowEpochMillis()
            val entity = PartyIssueEntity(
                companyId = companyId,
                issueId = UUID.randomUUID().toString(),
                partyId = partyId,
                title = title,
                status = IssueStatus.Open.asColumn(),
                createdAt = now,
                resolvedAt = null,
                updatedAt = now,
            )
            issueDao.upsert(entity)
            entity.toDomain()
        }

    override suspend fun resolveIssue(companyId: String, issueId: String): PartyIssue? = withContext(dispatchers.io) {
        val existing = issueDao.findById(companyId, issueId) ?: return@withContext null
        val now = timeProvider.nowEpochMillis()
        val updated = existing.copy(status = IssueStatus.Resolved.asColumn(), resolvedAt = now, updatedAt = now)
        issueDao.upsert(updated)
        updated.toDomain()
    }

    override suspend fun reopenIssue(companyId: String, issueId: String): PartyIssue? = withContext(dispatchers.io) {
        val existing = issueDao.findById(companyId, issueId) ?: return@withContext null
        val updated = existing.copy(status = IssueStatus.Open.asColumn(), resolvedAt = null, updatedAt = timeProvider.nowEpochMillis())
        issueDao.upsert(updated)
        updated.toDomain()
    }

    override suspend fun getIssuesForParty(companyId: String, partyId: String): List<PartyIssue> =
        withContext(dispatchers.io) { issueDao.findAllForParty(companyId, partyId).map { it.toDomain() } }

    // ---- MVP-1.2-C: Issue History ----

    override suspend fun getIssueActivitySummary(companyId: String, partyId: String): Map<String, IssueActivitySummary> =
        withContext(dispatchers.io) {
            noteDao.issueActivitySummary(companyId, partyId)
                .associate { it.issueId to IssueActivitySummary(noteCount = it.noteCount, latestNoteAt = it.latestNoteAt) }
        }

    // ---- MVP-1.1-D: Tally XML enrichment round-trip ----

    override suspend fun getExportCandidates(companyId: String, partyId: String): List<TallyFieldExportCandidate> =
        withContext(dispatchers.io) {
            val provenanceByField = fieldProvenanceDao.findAllForParty(companyId, partyId).associateBy { it.fieldName }
            TallyExportFieldMapping.ELIGIBLE_FIELDS.map { fieldName ->
                val row = provenanceByField[fieldName]
                TallyFieldExportCandidate(
                    fieldName = fieldName,
                    label = TallyExportFieldMapping.labelFor(fieldName),
                    tallyValue = row?.tallyValue,
                    budcomValue = row?.budcomValue,
                    state = row?.state?.toFieldProvenanceState() ?: FieldProvenanceState.EmptyUnknown,
                )
            }
        }

    override suspend fun recordExport(
        companyId: String,
        partyId: String,
        outputFileName: String,
        fieldNames: List<String>,
    ): PartyExportEvent = withContext(dispatchers.io) {
        val eligible = fieldNames.filter { TallyExportFieldMapping.isEligible(it) }.distinct()
        require(eligible.isNotEmpty()) { "At least one Tally-eligible field is required to record an export." }
        val now = timeProvider.nowEpochMillis()

        eligible.forEach { fieldName ->
            val existing = fieldProvenanceDao.findField(companyId, partyId, fieldName)
            fieldProvenanceDao.upsert(
                baseProvenance(existing, companyId, partyId, fieldName).copy(
                    state = FieldProvenanceState.Exported.asColumn(),
                    lastExportedAt = now,
                    updatedAt = now,
                ),
            )
        }

        val event = PartyExportEventEntity(
            companyId = companyId,
            exportId = UUID.randomUUID().toString(),
            partyId = partyId,
            createdAt = now,
            outputFileName = outputFileName,
            fieldNamesCsv = eligible.joinToString(","),
        )
        exportEventDao.insert(event)
        event.toDomain()
    }

    override suspend fun reconcileExportedFieldFromTally(
        companyId: String,
        partyId: String,
        fieldName: String,
        tallyRawValue: String?,
    ): FieldProvenanceState = withContext(dispatchers.io) {
        val existing = fieldProvenanceDao.findField(companyId, partyId, fieldName)
        val now = timeProvider.nowEpochMillis()

        // TD-027 honesty: no fresh Tally value yet is "not re-synced," not "failed" — never
        // downgrade an already-tracked field to EmptyUnknown just because this read-back was empty.
        if (tallyRawValue.isNullOrBlank()) {
            return@withContext existing?.state?.toFieldProvenanceState() ?: FieldProvenanceState.EmptyUnknown
        }

        val canonicalTally = canonicalizeForComparison(fieldName, tallyRawValue)
        val canonicalBudcom = canonicalizeForComparison(fieldName, existing?.budcomValue)
        val newState = if (canonicalBudcom != null && canonicalBudcom != canonicalTally) {
            FieldProvenanceState.Conflict
        } else {
            FieldProvenanceState.ConfirmedFromTally
        }

        fieldProvenanceDao.upsert(
            baseProvenance(existing, companyId, partyId, fieldName).copy(
                state = newState.asColumn(),
                tallyValue = tallyRawValue,
                lastConfirmedAt = if (newState == FieldProvenanceState.ConfirmedFromTally) now else existing?.lastConfirmedAt,
                updatedAt = now,
            ),
        )
        if (newState == FieldProvenanceState.ConfirmedFromTally) {
            applyEffectiveFieldValue(companyId, partyId, fieldName, tallyRawValue, now)
        }
        newState
    }

    override suspend fun getExportHistory(companyId: String, partyId: String, limit: Int): List<PartyExportEvent> =
        withContext(dispatchers.io) {
            exportEventDao.recentForParty(companyId, partyId, limit.coerceIn(1, 100)).map { it.toDomain() }
        }

    /** Field-appropriate canonical comparison for re-sync confirmation — never raw string
     * equality, so a merely-differently-formatted match (e.g. phone spacing, email case) is not
     * misreported as a conflict. Address is deliberately compared post-trim only, never
     * over-normalized (architecture §18.7). */
    private fun canonicalizeForComparison(fieldName: String, value: String?): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (fieldName) {
            PartyFieldNames.PRIMARY_PHONE -> PhoneNumberNormalizer.normalizeForSearch(trimmed) ?: trimmed
            PartyFieldNames.PRIMARY_EMAIL -> trimmed.lowercase()
            PartyFieldNames.GSTIN -> trimmed.uppercase()
            PartyFieldNames.ADDRESS_STATE -> trimmed.lowercase()
            else -> trimmed
        }
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

private fun PartyTagAssignmentRow.toTagDomain(): Tag = Tag(
    tagId = tagId,
    parentTagId = parentTagId,
    name = name,
    path = path,
    createdAt = createdAt,
)
