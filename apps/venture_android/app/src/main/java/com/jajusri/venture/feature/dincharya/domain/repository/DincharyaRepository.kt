package com.jajusri.venture.feature.dincharya.domain.repository

import com.jajusri.venture.feature.dincharya.domain.model.DincharyaGroup
import com.jajusri.venture.feature.dincharya.domain.model.DincharyaItem

/**
 * Local-first, bounded, company-wide read surface for Dincharya (MVP-1.2-D, architecture §11 —
 * "new feature/dincharya/ package... a new top-level feature, not a Connect sub-feature, since it
 * is cross-party rather than party-scoped"). Deliberately its own repository rather than an
 * extension of [com.jajusri.venture.feature.party.domain.repository.PartyRepository]: every method
 * on that interface is already party-scoped (one Party or one Party's paged list), Dincharya's three
 * queries are the first genuinely company-wide ones in this feature area, and adding them there
 * would force five unrelated existing `PartyRepository` test fakes
 * (`ConnectViewModelTest`/`PartyXmlExportViewModelTest`/`ProspectCreateViewModelTest`/
 * `ReconcilePartiesFromLedgersUseCaseTest`/`SyncViewModelTest`) to grow stub overrides for a concern
 * none of them touch. Composes the same underlying DAOs `PartyRepositoryImpl` already uses — never a
 * second, competing data path.
 *
 * Every method is local-Room-only — zero network/Connector call, offline-capable by construction
 * (architecture §14).
 */
interface DincharyaRepository {
    /** Type A — active (incomplete) `commitment`/`follow_up` notes, company-wide, `dueAt ASC`
     * (overdue-first, then due-today, then upcoming; PDL-018). Never auto-expires. */
    suspend fun getFollowUps(companyId: String, limit: Int): DincharyaGroup<DincharyaItem.FollowUp>

    /** Type B — Parties with at least one field genuinely awaiting Tally re-sync confirmation. */
    suspend fun getPendingTallyConfirmations(companyId: String, limit: Int): DincharyaGroup<DincharyaItem.PendingTallyConfirmation>

    /** Type C — non-Prospect Parties missing both a valid phone and a valid email (PDL-018). */
    suspend fun getPendingContactCompletions(companyId: String, limit: Int): DincharyaGroup<DincharyaItem.PendingContactCompletion>
}
