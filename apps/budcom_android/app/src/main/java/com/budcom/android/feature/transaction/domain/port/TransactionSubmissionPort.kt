package com.budcom.android.feature.transaction.domain.port

import com.budcom.android.feature.transaction.domain.model.SellerInboxEntry

/**
 * HARD ARCHITECTURAL BOUNDARY (docs/architecture/BUDCOM-TRANSACTION-MODE-ARCHITECTURE.md Finding 1
 * / §16 Risk 1): how a buyer's `IN_APP_SUBMITTED` Estimate/PO physically reaches a *different*
 * company's seller inbox. This codebase has no cross-company transport of any kind — it is
 * LAN-local with no cloud backend (see that document's Finding 1 for the full evidence trail).
 *
 * This interface is the seam that keeps [com.budcom.android.feature.transaction.data.repository.TransactionRepositoryImpl]
 * completely ignorant of how (or whether) delivery happens. A real buyer-installation ->
 * seller-installation adapter is **deliberately not implemented here** — building one requires a
 * separate, explicit product/infrastructure decision this session is not authorized to make.
 *
 * [LocalTransactionSubmissionPort] is the only implementation this session builds: it handles
 * exactly the one case this architecture can support safely today — a submission whose seller
 * inbox lives in the *same* local company database as the submission itself (a seller drafting an
 * Estimate/PO on behalf of a walk-in/phone buyer they're serving directly, or a same-device
 * test/demo scenario). It does **not** simulate, fake, or approximate cross-company delivery by
 * any means — no LAN polling of another company's database, no shared-database assumption, no
 * hidden backend call. When a real transport adapter is authorized and built, it is a drop-in
 * replacement for this binding (see `TransactionModule`) with zero change to repository, DAO, or
 * state-machine code.
 */
interface TransactionSubmissionPort {
    /**
     * Delivers an already-persisted, [com.budcom.android.feature.transaction.domain.model.TransactionDeliveryChannel.InAppSubmitted]
     * Estimate/PO into its seller's inbox, creating the [SellerInboxEntry] row.
     *
     * @throws TransportUnavailableException if this adapter cannot actually perform delivery for
     *   this submission (e.g. a future adapter asked to cross a real company boundary it doesn't
     *   support yet). Callers must treat this as an infrastructure gate, not a data error.
     */
    suspend fun deliverToSellerInbox(companyId: String, estimatePoId: String): SellerInboxEntry
}

/** Thrown by a [TransactionSubmissionPort] implementation that cannot perform delivery for the
 * requested submission — an infrastructure gap, never a data-validity problem. */
class TransportUnavailableException(message: String) : Exception(message)
