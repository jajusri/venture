package com.budcom.android.core.di

import com.budcom.android.core.util.DefaultDispatcherProvider
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.core.util.SystemTimeProvider
import com.budcom.android.core.util.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Application-wide Hilt bindings that are not network- or database-specific.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(
        impl: DefaultDispatcherProvider,
    ): DispatcherProvider

    @Binds
    @Singleton
    abstract fun bindTimeProvider(
        impl: SystemTimeProvider,
    ): TimeProvider
}

@Module
@InstallIn(SingletonComponent::class)
object AppProvideModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
