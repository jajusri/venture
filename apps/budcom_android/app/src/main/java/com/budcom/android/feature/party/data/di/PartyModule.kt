package com.budcom.android.feature.party.data.di

import com.budcom.android.feature.party.data.repository.PartyRepositoryImpl
import com.budcom.android.feature.party.domain.repository.PartyRepository
import com.budcom.android.feature.party.sharing.AndroidPartyXmlExportCoordinator
import com.budcom.android.feature.party.sharing.PartyXmlExportCoordinator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PartyBindModule {

    @Binds
    @Singleton
    abstract fun bindPartyRepository(
        impl: PartyRepositoryImpl,
    ): PartyRepository

    @Binds
    @Singleton
    abstract fun bindPartyXmlExportCoordinator(
        impl: AndroidPartyXmlExportCoordinator,
    ): PartyXmlExportCoordinator
}
