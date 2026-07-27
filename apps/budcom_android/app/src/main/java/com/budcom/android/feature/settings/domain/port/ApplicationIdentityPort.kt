package com.budcom.android.feature.settings.domain.port

import com.budcom.android.feature.settings.domain.model.ApplicationInformation

/**
 * Reads confirmed local application identity. Does not invent build metadata.
 */
fun interface ApplicationIdentityPort {
    fun read(): ApplicationInformation
}
