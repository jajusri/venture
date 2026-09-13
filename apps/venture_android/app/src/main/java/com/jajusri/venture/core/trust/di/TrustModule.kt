package com.jajusri.venture.core.trust.di

import com.jajusri.venture.core.security.AuthorityEpochCache
import com.jajusri.venture.core.security.IssuerVerificationKeyCache
import com.jajusri.venture.core.trust.data.DefaultTrustEndpointHydrator
import com.jajusri.venture.core.trust.data.DefaultTrustEnrollmentRepository
import com.jajusri.venture.core.trust.data.TrustBackedAuthorityEpochCache
import com.jajusri.venture.core.trust.data.TrustBackedCommercialCredentialSource
import com.jajusri.venture.core.trust.data.TrustBackedIssuerVerificationKeyCache
import com.jajusri.venture.core.trust.data.TrustBackedRelayCredentialSource
import com.jajusri.venture.core.trust.data.TrustEndpointHydrator
import com.jajusri.venture.core.trust.data.TrustEnrollmentRepository
import com.jajusri.venture.core.trust.data.local.DataStoreTrustEndpointLocalStore
import com.jajusri.venture.core.trust.data.local.SecureTrustCredentialStore
import com.jajusri.venture.core.trust.data.local.TrustEndpointLocalStore
import com.jajusri.venture.core.trust.data.remote.DefaultTrustEndpointProvider
import com.jajusri.venture.core.trust.data.remote.TrustApi
import com.jajusri.venture.core.trust.data.remote.TrustDynamicBaseUrlInterceptor
import com.jajusri.venture.core.trust.data.remote.TrustEndpointProvider
import com.jajusri.venture.core.trust.domain.TrustCredentialStore
import com.jajusri.venture.feature.transaction.domain.model.CommercialTrustCredentialSource
import com.jajusri.venture.feature.transaction.domain.port.RelayCredentialSource
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifier for Trust's own OkHttp/Retrofit stack -- kept fully separate from the shared
 * Connector stack (`core/network/NetworkModule.kt`) and from Relay's own client
 * (`HttpRelayClient`, under `feature/transaction`): Trust is a distinct service with its own
 * runtime-configurable endpoint ([TrustEndpointProvider]), and reusing the Connector's
 * `DynamicBaseUrlInterceptor` would silently couple Trust's availability to the Connector's. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class TrustHttp

@Module
@InstallIn(SingletonComponent::class)
abstract class TrustBindModule {
    @Binds
    @Singleton
    abstract fun bindTrustCredentialStore(impl: SecureTrustCredentialStore): TrustCredentialStore

    @Binds
    @Singleton
    abstract fun bindTrustEndpointProvider(impl: DefaultTrustEndpointProvider): TrustEndpointProvider

    @Binds
    @Singleton
    abstract fun bindTrustEndpointLocalStore(impl: DataStoreTrustEndpointLocalStore): TrustEndpointLocalStore

    @Binds
    @Singleton
    abstract fun bindTrustEndpointHydrator(impl: DefaultTrustEndpointHydrator): TrustEndpointHydrator

    @Binds
    @Singleton
    abstract fun bindTrustEnrollmentRepository(impl: DefaultTrustEnrollmentRepository): TrustEnrollmentRepository

    // The four bindings below satisfy protected `feature/transaction` ports from OUTSIDE that
    // package -- see the final report's Gate 5A/5B section. `TransactionModule.kt`'s own previous
    // stub `@Provides` for each of these was removed in the same change that added these bindings,
    // so there is exactly one binding per type in the graph, not two.
    @Binds
    @Singleton
    abstract fun bindRelayCredentialSource(impl: TrustBackedRelayCredentialSource): RelayCredentialSource

    @Binds
    @Singleton
    abstract fun bindCommercialTrustCredentialSource(impl: TrustBackedCommercialCredentialSource): CommercialTrustCredentialSource

    @Binds
    @Singleton
    abstract fun bindIssuerVerificationKeyCache(impl: TrustBackedIssuerVerificationKeyCache): IssuerVerificationKeyCache

    @Binds
    @Singleton
    abstract fun bindAuthorityEpochCache(impl: TrustBackedAuthorityEpochCache): AuthorityEpochCache
}

@Module
@InstallIn(SingletonComponent::class)
object TrustHttpModule {
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 30L
    private const val CALL_TIMEOUT_SECONDS = 60L

    @Provides
    @Singleton
    @TrustHttp
    fun provideTrustOkHttpClient(
        interceptor: TrustDynamicBaseUrlInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(interceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    @Provides
    @Singleton
    @TrustHttp
    fun provideTrustRetrofit(@TrustHttp okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            // Placeholder origin; TrustDynamicBaseUrlInterceptor applies the runtime-configured host,
            // or throws (fail closed) if none is configured yet -- see that interceptor's doc comment.
            .baseUrl("https://trust.invalid/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideTrustApi(@TrustHttp retrofit: Retrofit): TrustApi = retrofit.create(TrustApi::class.java)
}
