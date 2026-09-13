package com.jajusri.venture.feature.catalogue.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueLifecycleTransitionsTest {

    @Test
    fun `Draft submit for review by staff succeeds without owner access`() {
        val next = CatalogueLifecycleTransitions.transition(
            CatalogueLifecycleState.Draft, CatalogueLifecycleAction.SubmitForReview, isOwner = false,
        )
        assertEquals(CatalogueLifecycleState.Review, next)
    }

    @Test
    fun `submit for review is rejected from every state other than Draft`() {
        listOf(CatalogueLifecycleState.Review, CatalogueLifecycleState.Published, CatalogueLifecycleState.Archived).forEach { state ->
            assertNull(
                "expected rejection from $state",
                CatalogueLifecycleTransitions.transition(state, CatalogueLifecycleAction.SubmitForReview, isOwner = true),
            )
        }
    }

    @Test
    fun `Publish from Draft requires owner access (solo-business fast path)`() {
        assertNull(CatalogueLifecycleTransitions.transition(CatalogueLifecycleState.Draft, CatalogueLifecycleAction.Publish, isOwner = false))
        assertEquals(
            CatalogueLifecycleState.Published,
            CatalogueLifecycleTransitions.transition(CatalogueLifecycleState.Draft, CatalogueLifecycleAction.Publish, isOwner = true),
        )
    }

    @Test
    fun `Publish from Review requires owner access`() {
        assertNull(CatalogueLifecycleTransitions.transition(CatalogueLifecycleState.Review, CatalogueLifecycleAction.Publish, isOwner = false))
        assertEquals(
            CatalogueLifecycleState.Published,
            CatalogueLifecycleTransitions.transition(CatalogueLifecycleState.Review, CatalogueLifecycleAction.Publish, isOwner = true),
        )
    }

    @Test
    fun `Publish is rejected from Published and Archived regardless of owner access`() {
        listOf(CatalogueLifecycleState.Published, CatalogueLifecycleState.Archived).forEach { state ->
            assertNull(CatalogueLifecycleTransitions.transition(state, CatalogueLifecycleAction.Publish, isOwner = true))
        }
    }

    @Test
    fun `Archive requires owner access and only applies from Published`() {
        assertNull(CatalogueLifecycleTransitions.transition(CatalogueLifecycleState.Published, CatalogueLifecycleAction.Archive, isOwner = false))
        assertEquals(
            CatalogueLifecycleState.Archived,
            CatalogueLifecycleTransitions.transition(CatalogueLifecycleState.Published, CatalogueLifecycleAction.Archive, isOwner = true),
        )
        listOf(CatalogueLifecycleState.Draft, CatalogueLifecycleState.Review, CatalogueLifecycleState.Archived).forEach { state ->
            assertNull(CatalogueLifecycleTransitions.transition(state, CatalogueLifecycleAction.Archive, isOwner = true))
        }
    }

    @Test
    fun `Unarchive requires owner access and returns to Draft`() {
        assertNull(CatalogueLifecycleTransitions.transition(CatalogueLifecycleState.Archived, CatalogueLifecycleAction.Unarchive, isOwner = false))
        assertEquals(
            CatalogueLifecycleState.Draft,
            CatalogueLifecycleTransitions.transition(CatalogueLifecycleState.Archived, CatalogueLifecycleAction.Unarchive, isOwner = true),
        )
    }

    @Test
    fun `ReopenForEdit moves a Published product back to Draft without requiring owner access`() {
        val next = CatalogueLifecycleTransitions.transition(
            CatalogueLifecycleState.Published, CatalogueLifecycleAction.ReopenForEdit, isOwner = false,
        )
        assertEquals(CatalogueLifecycleState.Draft, next)
    }

    @Test
    fun `ReopenForEdit is rejected from every state other than Published`() {
        listOf(CatalogueLifecycleState.Draft, CatalogueLifecycleState.Review, CatalogueLifecycleState.Archived).forEach { state ->
            assertNull(CatalogueLifecycleTransitions.transition(state, CatalogueLifecycleAction.ReopenForEdit, isOwner = true))
        }
    }

    @Test
    fun `canEdit is true only for Draft and Review`() {
        assertTrue(CatalogueLifecycleTransitions.canEdit(CatalogueLifecycleState.Draft))
        assertTrue(CatalogueLifecycleTransitions.canEdit(CatalogueLifecycleState.Review))
        assertFalse(CatalogueLifecycleTransitions.canEdit(CatalogueLifecycleState.Published))
        assertFalse(CatalogueLifecycleTransitions.canEdit(CatalogueLifecycleState.Archived))
    }
}
