package com.budcom.android.core.network

import com.budcom.android.BuildConfig
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
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Provides OkHttp, Kotlin Serialization, Retrofit, and network infrastructure bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindModule {

    @Binds
    @Singleton
    abstract fun bindErrorMapper(impl: DefaultErrorMapper): ErrorMapper

    @Binds
    @Singleton
    abstract fun bindConnectivityObserver(
        impl: DefaultNetworkConnectivityObserver,
    ): NetworkConnectivityObserver

    @Binds
    @Singleton
    abstract fun bindBaseUrlProvider(
        impl: DefaultConnectorBaseUrlProvider,
    ): ConnectorBaseUrlProvider
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        val interceptor = HttpLoggingInterceptor { message ->
            Timber.tag("OkHttp").d(message)
        }
        // BASIC logs method/URL/status only — never response bodies.
        interceptor.level = if (BuildConfig.NETWORK_LOGGING_ENABLED) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        return interceptor
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor,
        diagnosticsInterceptor: NetworkDiagnosticsInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(NetworkConstants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NetworkConstants.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(NetworkConstants.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(NetworkConstants.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(diagnosticsInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @SyncHttp
    fun provideSyncOkHttpClient(
        dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor,
        diagnosticsInterceptor: NetworkDiagnosticsInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(NetworkConstants.SYNC_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NetworkConstants.SYNC_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(NetworkConstants.SYNC_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(NetworkConstants.SYNC_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(diagnosticsInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            // Placeholder origin; DynamicBaseUrlInterceptor applies the configured host.
            .baseUrl(BuildConfig.CONNECTOR_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    @SyncHttp
    fun provideSyncRetrofit(
        @SyncHttp okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.CONNECTOR_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideDefaultRetryPolicy(): RetryPolicy = RetryPolicy.Default
}
