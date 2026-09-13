package com.jajusri.venture.core.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clock abstraction for deterministic tests.
 */
fun interface TimeProvider {
    fun nowEpochMillis(): Long
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
