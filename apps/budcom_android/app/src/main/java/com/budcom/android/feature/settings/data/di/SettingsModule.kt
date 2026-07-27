package com.budcom.android.feature.settings.data.di

import com.budcom.android.feature.settings.data.local.BuildConfigApplicationIdentityPort
import com.budcom.android.feature.settings.data.local.ThemePreferencesLocalDataSource
import com.budcom.android.feature.settings.data.local.ThemePreferencesStore
import com.budcom.android.feature.settings.data.repository.ThemePreferencesRepositoryImpl
import com.budcom.android.feature.settings.domain.port.ApplicationIdentityPort
import com.budcom.android.feature.settings.domain.repository.ThemePreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsBindModule {
    @Binds
    @Singleton
    abstract fun bindThemePreferencesStore(
        impl: ThemePreferencesLocalDataSource,
    ): ThemePreferencesStore

    @Binds
    @Singleton
    abstract fun bindApplicationIdentityPort(
        impl: BuildConfigApplicationIdentityPort,
    ): ApplicationIdentityPort

    @Binds
    @Singleton
    abstract fun bindThemePreferencesRepository(
        impl: ThemePreferencesRepositoryImpl,
    ): ThemePreferencesRepository
}
