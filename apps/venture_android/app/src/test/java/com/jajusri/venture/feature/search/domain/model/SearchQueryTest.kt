package com.jajusri.venture.feature.search.domain.model

import com.jajusri.venture.feature.search.domain.UniversalSearchDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchQueryTest {
    @Test
    fun blankQueryReturnsNull() {
        assertNull(SearchQuery("").normalized())
        assertNull(SearchQuery("   ").normalized())
    }

    @Test
    fun trimsAndCapsLength() {
        assertEquals("cash", SearchQuery("  cash  ").normalized())
        val long = "a".repeat(UniversalSearchDefaults.MAX_QUERY_LENGTH + 20)
        assertEquals(
            UniversalSearchDefaults.MAX_QUERY_LENGTH,
            SearchQuery(long).normalized()!!.length,
        )
    }

    @Test
    fun minimumLengthIsOneNonBlankCharacter() {
        assertEquals("a", SearchQuery("a").normalized())
        assertEquals(UniversalSearchDefaults.MIN_QUERY_LENGTH, 1)
    }
}
