package com.jajusri.venture.feature.catalogue.data.di

import com.jajusri.venture.feature.catalogue.data.CatalogueClockImpl
import com.jajusri.venture.feature.catalogue.data.local.CatalogueBranchSelectionLocalDataSource
import com.jajusri.venture.feature.catalogue.data.repository.CatalogueRepositoryImpl
import com.jajusri.venture.feature.catalogue.domain.port.CatalogueBranchSelectionStore
import com.jajusri.venture.feature.catalogue.domain.port.CatalogueClock
import com.jajusri.venture.feature.catalogue.domain.repository.CatalogueRepository
import com.jajusri.venture.feature.catalogue.sharing.AndroidCatalogueShareCoordinator
import com.jajusri.venture.feature.catalogue.sharing.CatalogueShareCoordinator
import com.jajusri.venture.feature.catalogue.storage.AndroidCatalogueAssetStore
import com.jajusri.venture.feature.catalogue.storage.CatalogueAssetStore
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
