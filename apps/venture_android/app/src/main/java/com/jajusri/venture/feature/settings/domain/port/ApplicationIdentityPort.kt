package com.jajusri.venture.feature.settings.domain.port

import com.jajusri.venture.feature.settings.domain.model.ApplicationInformation

/**
 * Reads confirmed local application identity. Does not invent build metadata.
 */
fun interface ApplicationIdentityPort {
    fun read(): ApplicationInformation
}
