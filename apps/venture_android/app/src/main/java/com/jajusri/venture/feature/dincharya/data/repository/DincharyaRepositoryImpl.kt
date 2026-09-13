package com.jajusri.venture.feature.dincharya.data.repository

import com.jajusri.venture.core.util.DispatcherProvider
import com.jajusri.venture.core.util.TimeProvider
import com.jajusri.venture.feature.dincharya.domain.model.DincharyaGroup
import com.jajusri.venture.feature.dincharya.domain.model.DincharyaItem
import com.jajusri.venture.feature.dincharya.domain.model.FollowUpUrgency
import com.jajusri.venture.feature.dincharya.domain.repository.DincharyaRepository
import com.jajusri.venture.feature.party.data.local.PartyDao
import com.jajusri.venture.feature.party.data.local.PartyFieldProvenanceDao
import com.jajusri.venture.feature.party.data.local.PartyNoteDao
import com.jajusri.venture.feature.party.data.local.PartyNoteEntity
import com.jajusri.venture.feature.party.data.local.PendingConfirmationRow
import com.jajusri.venture.feature.party.data.local.toNoteType
import com.jajusri.venture.feature.party.domain.model.TallyExportFieldMapping
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_LIMIT = 100

/**
 * Local-first [DincharyaRepository] composing the three bounded, company-wide DAO queries added for
 * MVP-1.2-D (architecture §16 — `LIMIT`/`OFFSET` throughout, never an in-memory filter over a full
 * company load). Display names for the follow-up and pending-confirmation groups are resolved via
 * one bounded [PartyDao.findByIds] bulk lookup per group (never per-row), mirroring
 * [com.jajusri.venture.feature.connect.presentation.ConnectViewModel]'s own bulk-enrichment-read
 * precedent rather than a cross-table SQL JOIN — keeps each DAO single-table-focused.
 */
@Singleton
class DincharyaRepositoryImpl @Inject constructor(
    private val partyDao: PartyDao,
    private val noteDao: PartyNoteDao,
    private val fieldProvenanceDao: PartyFieldProvenanceDao,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
) : DincharyaRepository {

    override suspend fun getFollowUps(companyId: String, limit: Int): DincharyaGroup<DincharyaItem.FollowUp> =
        withContext(dispatchers.io) {
            val safeLimit = limit.coerceIn(1, MAX_LIMIT)
            val total = noteDao.countFollowUpsForCompany(companyId)
            val notes = noteDao.pageFollowUpsForCompany(companyId, safeLimit, 0)
            val namesByPartyId = resolveDisplayNames(companyId, notes.map { it.partyId })
            val now = timeProvider.nowEpochMillis()
            val items = notes.map { note -> note.toFollowUpItem(namesByPartyId, now) }
            DincharyaGroup(items, total)
        }

    override suspend fun getPendingTallyConfirmations(
        companyId: String,
        limit: Int,
    ): DincharyaGroup<DincharyaItem.PendingTallyConfirmation> = withContext(dispatchers.io) {
        val safeLimit = limit.coerceIn(1, MAX_LIMIT)
        val total = fieldProvenanceDao.countPendingConfirmationForCompany(companyId)
        val rows = fieldProvenanceDao.pagePendingConfirmationForCompany(companyId, safeLimit, 0)
        val namesByPartyId = resolveDisplayNames(companyId, rows.map { it.partyId })
        val items = rows.map { row -> row.toPendingConfirmationItem(namesByPartyId) }
        DincharyaGroup(items, total)
    }

    override suspend fun getPendingContactCompletions(
        companyId: String,
        limit: Int,
    ): DincharyaGroup<DincharyaItem.PendingContactCompletion> = withContext(dispatchers.io) {
        val safeLimit = limit.coerceIn(1, MAX_LIMIT)
        val total = partyDao.countMissingContactInfo(companyId)
        val parties = partyDao.pageMissingContactInfo(companyId, safeLimit, 0)
        val items = parties.map { party ->
            DincharyaItem.PendingContactCompletion(partyId = party.partyId, partyDisplayName = party.displayName)
        }
        DincharyaGroup(items, total)
    }

    private suspend fun resolveDisplayNames(companyId: String, partyIds: List<String>): Map<String, String> {
        val distinctIds = partyIds.distinct()
        if (distinctIds.isEmpty()) return emptyMap()
        return partyDao.findByIds(companyId, distinctIds).associate { it.partyId to it.displayName }
    }

    private fun PartyNoteEntity.toFollowUpItem(namesByPartyId: Map<String, String>, nowEpochMillis: Long): DincharyaItem.FollowUp {
        val dueAtMillis = requireNotNull(dueAt) { "pageFollowUpsForCompany only returns notes with a non-null dueAt" }
        return DincharyaItem.FollowUp(
            noteId = noteId,
            partyId = partyId,
            partyDisplayName = namesByPartyId[partyId] ?: partyId,
            noteType = type.toNoteType(),
            body = body,
            dueAt = dueAtMillis,
            urgency = classifyUrgency(dueAtMillis, nowEpochMillis),
        )
    }

    private fun PendingConfirmationRow.toPendingConfirmationItem(namesByPartyId: Map<String, String>): DincharyaItem.PendingTallyConfirmation =
        DincharyaItem.PendingTallyConfirmation(
            partyId = partyId,
            partyDisplayName = namesByPartyId[partyId] ?: partyId,
            pendingFieldLabels = fieldNamesCsv.split(",").filter { it.isNotBlank() }.map { TallyExportFieldMapping.labelFor(it) },
            earliestExportedAt = earliestAt,
        )
}

/** Pure day-boundary comparison in the device's local zone — matches this app's existing due-date
 * display convention (`ZoneId.systemDefault()` at parse/format time). Never an invented urgency
 * score: the classification follows directly from comparing calendar dates. */
private fun classifyUrgency(dueAtEpochMillis: Long, nowEpochMillis: Long): FollowUpUrgency {
    val zone = ZoneId.systemDefault()
    val dueDate = Instant.ofEpochMilli(dueAtEpochMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
    return when {
        dueDate.isBefore(today) -> FollowUpUrgency.Overdue
        dueDate.isEqual(today) -> FollowUpUrgency.DueToday
        else -> FollowUpUrgency.Upcoming
    }
}
