package com.budcom.android.navigation.di

import com.budcom.android.navigation.DefaultResolveStartupRoutingState
import com.budcom.android.navigation.ResolveStartupRoutingState
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NavigationModule {

    @Binds
    @Singleton
    abstract fun bindResolveStartupRoutingState(
        impl: DefaultResolveStartupRoutingState,
    ): ResolveStartupRoutingState
}
