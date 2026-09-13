package com.jajusri.venture.feature.transaction.domain.port

/**
 * Q14's reminder ladder needs a background scheduler to reach a buyer proactively
 * (docs/architecture/VENTURE-TRANSACTION-MODE-ARCHITECTURE.md Finding 2 / §13). **This codebase has
 * zero notification/scheduling infrastructure of any kind today** — confirmed by exhaustive search
 * during the architecture pass: no `NotificationChannel`, no `Worker`/`CoroutineWorker`
 * implementation anywhere, `WorkManager` wired only for Hilt DI reasons. Catalogue's own five
 * already-locked notification types share this identical gap.
 *
 * This interface exists so [com.jajusri.venture.feature.transaction.data.repository.TransactionRepositoryImpl]
 * never depends on whether a reminder is actually, eventually delivered. [NoOpTransactionReminderScheduler]
 * is the only implementation this session builds — it records nothing and fires nothing, so every
 * caller of [scheduleReminders]/[cancelReminders] behaves correctly regardless of whether a real
 * scheduler ever gets built. The architecture document recommends a real implementation be built
 * once, shared between this feature's Q14 ladder and Catalogue's own five notification types,
 * rather than either feature inventing its own competing mechanism — this session does not build
 * that shared infrastructure (out of scope; would require inventing scheduling infrastructure this
 * session was explicitly told not to do unsafely/untested), it only leaves the seam ready for it.
 */
interface TransactionReminderScheduler {
    /** Called once a transaction reaches [com.jajusri.venture.feature.transaction.domain.model.CommercialTransactionState.Agreed]
     * (the earliest point a due date, if computable, exists at all). A no-op implementation is
     * always safe to call — it simply means no reminder will ever fire, not an error. */
    suspend fun scheduleReminders(companyId: String, transactionId: String)

    /** Called once a transaction reaches [com.jajusri.venture.feature.transaction.domain.model.CommercialTransactionState.Completed]
     * (no further reminders make sense). */
    suspend fun cancelReminders(companyId: String, transactionId: String)
}

/** Intentionally does nothing — see [TransactionReminderScheduler]'s own doc comment for why this
 * is the correct, honest behavior rather than a placeholder to be embarrassed about. */
class NoOpTransactionReminderScheduler @javax.inject.Inject constructor() : TransactionReminderScheduler {
    override suspend fun scheduleReminders(companyId: String, transactionId: String) {
        // Deliberately no-op — no scheduling infrastructure exists to schedule anything on.
    }

    override suspend fun cancelReminders(companyId: String, transactionId: String) {
        // Deliberately no-op.
    }
}
