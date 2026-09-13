package com.jajusri.venture.feature.settings.data.di

import com.jajusri.venture.feature.settings.data.local.BuildConfigApplicationIdentityPort
import com.jajusri.venture.feature.settings.data.local.ThemePreferencesLocalDataSource
import com.jajusri.venture.feature.settings.data.local.ThemePreferencesStore
import com.jajusri.venture.feature.settings.data.repository.ThemePreferencesRepositoryImpl
import com.jajusri.venture.feature.settings.domain.port.ApplicationIdentityPort
import com.jajusri.venture.feature.settings.domain.repository.ThemePreferencesRepository
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
