package com.budcom.android.feature.catalogue.data.di

import com.budcom.android.feature.catalogue.data.CatalogueClockImpl
import com.budcom.android.feature.catalogue.data.local.CatalogueBranchSelectionLocalDataSource
import com.budcom.android.feature.catalogue.data.repository.CatalogueRepositoryImpl
import com.budcom.android.feature.catalogue.domain.port.CatalogueBranchSelectionStore
import com.budcom.android.feature.catalogue.domain.port.CatalogueClock
import com.budcom.android.feature.catalogue.domain.repository.CatalogueRepository
import com.budcom.android.feature.catalogue.sharing.AndroidCatalogueShareCoordinator
import com.budcom.android.feature.catalogue.sharing.CatalogueShareCoordinator
import com.budcom.android.feature.catalogue.storage.AndroidCatalogueAssetStore
import com.budcom.android.feature.catalogue.storage.CatalogueAssetStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogueBindModule {

    @Binds
    @Singleton
    abstract fun bindCatalogueRepository(impl: CatalogueRepositoryImpl): CatalogueRepository

    @Binds
    @Singleton
    abstract fun bindCatalogueClock(impl: CatalogueClockImpl): CatalogueClock

    @Binds
    @Singleton
    abstract fun bindCatalogueAssetStore(impl: AndroidCatalogueAssetStore): CatalogueAssetStore

    @Binds
    @Singleton
    abstract fun bindCatalogueShareCoordinator(impl: AndroidCatalogueShareCoordinator): CatalogueShareCoordinator

    @Binds
    @Singleton
    abstract fun bindCatalogueBranchSelectionStore(impl: CatalogueBranchSelectionLocalDataSource): CatalogueBranchSelectionStore
}
