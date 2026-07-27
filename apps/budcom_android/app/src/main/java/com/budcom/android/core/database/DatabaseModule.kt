package com.budcom.android.core.database

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt bindings for Room.
 *
 * Providers will be added when the first `@Entity` and `AppDatabase` are introduced.
 * Keeping this module present reserves the DI install site without inventing schema.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule
