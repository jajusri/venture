package com.jajusri.venture.feature.catalogue.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mid-MVP-1.4 product lock: "Price is optional, but the price state is not." These tests pin the
 * three-way distinction the lock requires -- an actual price, an honest "no price supplied yet,"
 * and the seller's deliberate "Contact for price" -- and specifically guard against the pre-lock
 * bug where an Open-mode product with no amount entered was silently rendered identically to
 * Contact-for-price (see [com.jajusri.venture.feature.catalogue.sharing.CatalogueShareTextRendererTest]).
 */
class CataloguePriceStateTest {

    @Test
    fun `Open mode with a numeric amount resolves to ActualPrice`() {
        val state = resolveCataloguePriceState(PriceDisplayMode.Open, "499", "INR")
        assertEquals(CataloguePriceState.ActualPrice("499", "INR"), state)
    }

    @Test
    fun `Open mode with no amount resolves to NoPriceSupplied, never ContactForPrice`() {
        val state = resolveCataloguePriceState(PriceDisplayMode.Open, null, null)
        assertEquals(CataloguePriceState.NoPriceSupplied, state)
    }

    @Test
    fun `Open mode with a blank or whitespace-only amount also resolves to NoPriceSupplied`() {
        assertEquals(CataloguePriceState.NoPriceSupplied, resolveCataloguePriceState(PriceDisplayMode.Open, "", null))
        assertEquals(CataloguePriceState.NoPriceSupplied, resolveCataloguePriceState(PriceDisplayMode.Open, "   ", null))
    }

    @Test
    fun `ContactForPrice mode always resolves to ContactForPrice, even if an amount happens to be present`() {
        // Example C: a seller's deliberate Contact-for-Price choice is never overridden by a
        // leftover/stale amount value -- mode is authoritative, not amount presence.
        val state = resolveCataloguePriceState(PriceDisplayMode.ContactForPrice, "999", "INR")
        assertEquals(CataloguePriceState.ContactForPrice, state)
    }

    @Test
    fun `an amount with surrounding whitespace is trimmed in ActualPrice`() {
        val state = resolveCataloguePriceState(PriceDisplayMode.Open, "  499  ", "INR")
        assertEquals(CataloguePriceState.ActualPrice("499", "INR"), state)
    }

    @Test
    fun `ActualPrice preserves a null currency code rather than substituting one`() {
        val state = resolveCataloguePriceState(PriceDisplayMode.Open, "499", null)
        assertEquals(CataloguePriceState.ActualPrice("499", null), state)
    }
}
