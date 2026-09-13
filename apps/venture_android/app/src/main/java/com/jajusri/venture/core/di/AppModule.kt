package com.jajusri.venture.core.di

import com.jajusri.venture.core.util.DefaultDeviceEnvironment
import com.jajusri.venture.core.util.DefaultDispatcherProvider
import com.jajusri.venture.core.util.DeviceEnvironment
import com.jajusri.venture.core.util.DispatcherProvider
import com.jajusri.venture.core.util.SystemTimeProvider
import com.jajusri.venture.core.util.TimeProvider
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

    @Binds
    @Singleton
    abstract fun bindDeviceEnvironment(
        impl: DefaultDeviceEnvironment,
    ): DeviceEnvironment
}

@Module
@InstallIn(SingletonComponent::class)
object AppProvideModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
