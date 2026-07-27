package com.budcom.android.core.database

/**
 * Room database identity constants.
 *
 * [AppDatabase] and entities are introduced with the first offline-backed feature.
 * Room is on the classpath and ready; inventing empty entities would violate the
 * scaffold constraint against placeholder domain models.
 */
object DatabaseConstants {
    const val NAME = "budcom.db"
    const val VERSION = 1
}
