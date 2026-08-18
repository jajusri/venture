package com.budcom.android.feature.dincharya.domain.usecase

import com.budcom.android.feature.dincharya.domain.model.DincharyaSnapshot
import com.budcom.android.feature.dincharya.domain.repository.DincharyaRepository
import javax.inject.Inject

/** Default cap per Dincharya group — matches this codebase's established `pageSize` convention
 * (e.g. [com.budcom.android.feature.party.domain.usecase.GetTimelineForPartyUseCase]) and the
 * "N more" bounded-disclosure requirement (architecture §10): never an infinite scroll. */
const val DINCHARYA_DEFAULT_GROUP_LIMIT = 20

/** Combines all three deterministic item types into one [DincharyaSnapshot] — the single entry
 * point the presentation layer uses, so "load Dincharya" is always all three groups together,
 * never a partially-loaded screen. */
class GetDincharyaSnapshotUseCase @Inject constructor(private val repository: DincharyaRepository) {
    suspend operator fun invoke(companyId: String, groupLimit: Int = DINCHARYA_DEFAULT_GROUP_LIMIT): DincharyaSnapshot =
        DincharyaSnapshot(
            followUps = repository.getFollowUps(companyId, groupLimit),
            pendingConfirmations = repository.getPendingTallyConfirmations(companyId, groupLimit),
            pendingContactCompletions = repository.getPendingContactCompletions(companyId, groupLimit),
        )
}
