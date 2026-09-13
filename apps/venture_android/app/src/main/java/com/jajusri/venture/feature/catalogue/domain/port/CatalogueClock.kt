package com.jajusri.venture.feature.catalogue.domain.port

import com.jajusri.venture.feature.catalogue.domain.model.CatalogueTimestamp

/**
 * Resolves the timestamp a Catalogue write should be stamped with (architecture §15, LOCKED:
 * "Conflict/publication decisions must not blindly depend on a device-local clock"). See
 * [com.jajusri.venture.feature.catalogue.data.CatalogueClockImpl] for how this is actually
 * resolved, and its own doc comment for the one genuinely unresolved edge case (an offline Draft
 * edit) the architecture document explicitly declined to invent a mechanism for (§15/§22 item 2).
 */
fun interface CatalogueClock {
    suspend fun now(): CatalogueTimestamp
}
