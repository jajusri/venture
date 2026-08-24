package com.budcom.android.feature.catalogue.domain.model

/**
 * Locked lifecycle transitions (architecture §7): Draft → Review → Publish → Archive.
 *
 * Deliberately a pure function returning `null` for any invalid transition rather than throwing or
 * silently no-op'ing — callers (use cases) turn `null` into an honest, user-visible rejection. This
 * matches the "invalid transitions must be rejected, never silently accepted" requirement.
 *
 * Design note on "editing an already-Published product creates a new pending Draft copy, never an
 * in-place mutation of the live Published record" (architecture §7): rather than forking a second
 * `productId` that must later be merged back (a mechanism the architecture document itself never
 * fully specifies), [ReopenForEdit] moves the *same* product's working row back to [Draft] while
 * [com.budcom.android.feature.catalogue.domain.model.CataloguePublishedSnapshot] — a separate table
 * — is left completely untouched until the next successful [Publish] atomically overwrites it. The
 * customer-visible "live Published record" is the snapshot, not the working row, so this satisfies
 * the same guarantee (the live published version stays stable while an edit is in progress) with a
 * smaller, simpler mechanism.
 */
enum class CatalogueLifecycleAction { SubmitForReview, Publish, Archive, Unarchive, ReopenForEdit }

object CatalogueLifecycleTransitions {

    /** Review is intentionally not a stricter edit-lock than Draft (architecture §7/§22 item 5 —
     * proposed default, no stricter rule was ever locked). */
    fun canEdit(state: CatalogueLifecycleState): Boolean =
        state == CatalogueLifecycleState.Draft || state == CatalogueLifecycleState.Review

    /**
     * @param isOwner Owner-level access — required for [CatalogueLifecycleAction.Publish]/
     *   [CatalogueLifecycleAction.Archive]/[CatalogueLifecycleAction.Unarchive] (already locked:
     *   "only Owner-level access can Publish — no separate Reviewer role"; Archive is
     *   "owner-initiated only"). [CatalogueLifecycleAction.SubmitForReview] and
     *   [CatalogueLifecycleAction.ReopenForEdit] are draft-authoring actions any authorized
     *   staff/owner may perform, mirroring "staff prepares Drafts."
     * @return the new state, or `null` if [action] is not valid from [current] (or requires Owner
     *   access [isOwner] does not grant) — callers must treat `null` as a rejected transition.
     */
    fun transition(
        current: CatalogueLifecycleState,
        action: CatalogueLifecycleAction,
        isOwner: Boolean,
    ): CatalogueLifecycleState? = when (action) {
        CatalogueLifecycleAction.SubmitForReview ->
            CatalogueLifecycleState.Review.takeIf { current == CatalogueLifecycleState.Draft }

        CatalogueLifecycleAction.Publish ->
            CatalogueLifecycleState.Published.takeIf {
                isOwner && (current == CatalogueLifecycleState.Draft || current == CatalogueLifecycleState.Review)
            }

        CatalogueLifecycleAction.Archive ->
            CatalogueLifecycleState.Archived.takeIf { isOwner && current == CatalogueLifecycleState.Published }

        CatalogueLifecycleAction.Unarchive ->
            CatalogueLifecycleState.Draft.takeIf { isOwner && current == CatalogueLifecycleState.Archived }

        CatalogueLifecycleAction.ReopenForEdit ->
            CatalogueLifecycleState.Draft.takeIf { current == CatalogueLifecycleState.Published }
    }
}
