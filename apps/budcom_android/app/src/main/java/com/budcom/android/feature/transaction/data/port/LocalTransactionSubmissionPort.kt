package com.budcom.android.feature.transaction.data.port

import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.transaction.data.local.SellerInboxEntryDao
import com.budcom.android.feature.transaction.data.local.SellerInboxEntryEntity
import com.budcom.android.feature.transaction.domain.model.SellerInboxEntry
import com.budcom.android.feature.transaction.domain.model.SellerInboxState
import com.budcom.android.feature.transaction.domain.model.TransactionClock
import com.budcom.android.feature.transaction.domain.port.TransactionSubmissionPort
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only [TransactionSubmissionPort] implementation authorized this session — see that
 * interface's own doc comment for the full boundary. Handles exactly the same-company case: the
 * submission and the seller inbox it needs to appear in already live in the same local database,
 * so "delivery" is simply creating the [SellerInboxEntryEntity] row directly. No network call, no
 * cross-company assumption of any kind.
 */
@Singleton
class LocalTransactionSubmissionPort @Inject constructor(
    private val sellerInboxEntryDao: SellerInboxEntryDao,
    private val clock: TransactionClock,
    private val dispatchers: DispatcherProvider,
) : TransactionSubmissionPort {

    override suspend fun deliverToSellerInbox(companyId: String, estimatePoId: String): SellerInboxEntry =
        withContext(dispatchers.io) {
            val existing = sellerInboxEntryDao.findByEstimatePoId(companyId, estimatePoId)
            if (existing != null) return@withContext existing.toDomain()

            val now = clock.now()
            val entity = SellerInboxEntryEntity(
                companyId = companyId,
                inboxEntryId = UUID.randomUUID().toString(),
                estimatePoId = estimatePoId,
                state = SellerInboxState.New.columnValue,
                acknowledgedAt = null,
                acknowledgedAtSource = null,
                changeRequestNote = null,
                respondedAt = null,
                respondedAtSource = null,
                convertedTransactionId = null,
            )
            sellerInboxEntryDao.upsert(entity)
            entity.toDomain()
        }
}

internal fun SellerInboxEntryEntity.toDomain(): SellerInboxEntry = SellerInboxEntry(
    companyId = companyId,
    inboxEntryId = inboxEntryId,
    estimatePoId = estimatePoId,
    state = SellerInboxState.fromColumn(state),
    acknowledgedAt = com.budcom.android.feature.transaction.data.repository.toTimestampOrNull(acknowledgedAt, acknowledgedAtSource),
    changeRequestNote = changeRequestNote,
    respondedAt = com.budcom.android.feature.transaction.data.repository.toTimestampOrNull(respondedAt, respondedAtSource),
    convertedTransactionId = convertedTransactionId,
)
