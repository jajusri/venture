package com.budcom.android.feature.transaction.data.di

import com.budcom.android.BuildConfig
import com.budcom.android.core.security.AndroidVartalapDeviceKeyStore
import com.budcom.android.feature.transaction.data.TransactionClockImpl
import com.budcom.android.feature.transaction.data.port.LocalTransactionSubmissionPort
import com.budcom.android.feature.transaction.data.relay.HttpRelayClient
import com.budcom.android.feature.transaction.data.relay.HttpRelayStructuredTransport
import com.budcom.android.feature.transaction.data.relay.DefaultRelayOutboxDispatcher
import com.budcom.android.feature.transaction.data.relay.DefaultRelayRecipientInboxIngester
import com.budcom.android.feature.transaction.data.repository.StructuredRecipientInboxRepositoryImpl
import com.budcom.android.feature.transaction.domain.port.RelayRecipientInboxIngester
import com.budcom.android.feature.transaction.domain.repository.StructuredRecipientInboxRepository
import com.budcom.android.feature.transaction.data.relay.KeystoreRelayEnvelopeAuthenticator
import com.budcom.android.feature.transaction.domain.port.OrderSentFromRelayEvidence
import com.budcom.android.feature.transaction.domain.port.RelayOutboxDispatcher
import com.budcom.android.feature.transaction.data.repository.TransactionRepositoryImpl
import com.budcom.android.feature.transaction.domain.model.TransactionClock
import com.budcom.android.feature.transaction.domain.port.ConfiguredRelayEndpointProvider
import com.budcom.android.feature.transaction.domain.port.EmptyRelayEndpointProvider
import com.budcom.android.core.security.AuthorityEpochCache
import com.budcom.android.core.security.CachedTransportCredentialVerifier
import com.budcom.android.core.security.IssuerVerificationKeyCache
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityResolver
import com.budcom.android.feature.transaction.domain.model.CommercialTrustCredentialSource
import com.budcom.android.feature.transaction.domain.model.EmptyCommercialTrustCredentialSource
import com.budcom.android.feature.transaction.domain.model.TrustVerifiedCommercialActionAuthorityResolver
import com.budcom.android.feature.transaction.domain.port.TransportCredentialVerifier
import com.budcom.android.feature.transaction.domain.port.RelayCredentialSource
import com.budcom.android.feature.transaction.domain.port.RelayEndpointProvider
import com.budcom.android.feature.transaction.domain.port.RelayEnvelopeAuthenticator
import com.budcom.android.feature.transaction.domain.port.StructuredBusinessTransport
import com.budcom.android.feature.transaction.domain.port.NoOpTransactionReminderScheduler
import com.budcom.android.feature.transaction.domain.port.TransactionReminderScheduler
import com.budcom.android.feature.transaction.domain.port.TransactionSubmissionPort
import com.budcom.android.feature.transaction.domain.port.VartalapDeviceKeyStore
import com.budcom.android.feature.transaction.domain.repository.TransactionRepository
import com.budcom.android.feature.transaction.sharing.AndroidTransactionShareCoordinator
import com.budcom.android.feature.transaction.sharing.TransactionShareCoordinator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * [bindTransactionSubmissionPort] and [bindTransactionReminderScheduler] are the two bindings that
 * matter most here: swapping either to a real implementation later (a genuine cross-company
 * transport, a real WorkManager-based scheduler) is a one-line change to this module, with zero
 * change anywhere in [TransactionRepositoryImpl] or the domain layer — that is the entire point of
 * routing both through ports (architecture Findings 1/2).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TransactionBindModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindTransactionClock(impl: TransactionClockImpl): TransactionClock

    @Binds
    @Singleton
    abstract fun bindTransactionSubmissionPort(impl: LocalTransactionSubmissionPort): TransactionSubmissionPort

    @Binds
    @Singleton
    abstract fun bindTransactionReminderScheduler(impl: NoOpTransactionReminderScheduler): TransactionReminderScheduler

    @Binds
    @Singleton
    abstract fun bindTransactionShareCoordinator(impl: AndroidTransactionShareCoordinator): TransactionShareCoordinator

    @Binds
    @Singleton
    abstract fun bindVartalapDeviceKeyStore(impl: AndroidVartalapDeviceKeyStore): VartalapDeviceKeyStore

    @Binds
    @Singleton
    abstract fun bindRelayEnvelopeAuthenticator(impl: KeystoreRelayEnvelopeAuthenticator): RelayEnvelopeAuthenticator

    @Binds
    @Singleton
    abstract fun bindRelayOutboxDispatcher(impl: DefaultRelayOutboxDispatcher): RelayOutboxDispatcher

    @Binds
    @Singleton
    abstract fun bindOrderSentFromRelayEvidence(impl: TransactionRepositoryImpl): OrderSentFromRelayEvidence

    @Binds
    @Singleton
    abstract fun bindStructuredRecipientInboxRepository(impl: StructuredRecipientInboxRepositoryImpl): StructuredRecipientInboxRepository

    @Binds
    @Singleton
    abstract fun bindRelayRecipientInboxIngester(impl: DefaultRelayRecipientInboxIngester): RelayRecipientInboxIngester

    @Binds
    @Singleton
    abstract fun bindStructuredBusinessTransport(impl: HttpRelayStructuredTransport): StructuredBusinessTransport

    @Binds
    @Singleton
    abstract fun bindCommercialDbTransaction(impl: com.budcom.android.feature.transaction.data.local.RoomCommercialDbTransaction): com.budcom.android.feature.transaction.data.local.CommercialDbTransaction
}

@Module
@InstallIn(SingletonComponent::class)
object RelayTransportModule {
    @Provides
    @Singleton
    fun provideRelayEndpointProvider(): RelayEndpointProvider {
        val configured = BuildConfig.RELAY_DEFAULT_BASE_URL
        return if (configured.isBlank()) EmptyRelayEndpointProvider else ConfiguredRelayEndpointProvider(configured)
    }

    @Provides
    @Singleton
    fun provideRelayCredentialSource(): RelayCredentialSource = RelayCredentialSource { _, _ -> null }

    @Provides
    @Singleton
    fun provideHttpRelayClient(endpoint: RelayEndpointProvider): HttpRelayClient = HttpRelayClient(endpoint)

    @Provides
    @Singleton
    fun provideCommercialTrustCredentialSource(): CommercialTrustCredentialSource = EmptyCommercialTrustCredentialSource

    @Provides
    @Singleton
    fun provideIssuerVerificationKeyCache(): IssuerVerificationKeyCache = IssuerVerificationKeyCache { _, _ -> null }

    @Provides
    @Singleton
    fun provideAuthorityEpochCache(): AuthorityEpochCache = AuthorityEpochCache { _, _, _ -> null }

    @Provides
    @Singleton
    fun provideTransportCredentialVerifier(
        keys: IssuerVerificationKeyCache,
        epochs: AuthorityEpochCache,
    ): TransportCredentialVerifier = CachedTransportCredentialVerifier(keys, epochs)

    @Provides
    @Singleton
    fun provideCommercialActionAuthorityResolver(
        credentials: CommercialTrustCredentialSource,
        verifier: TransportCredentialVerifier,
        keyStore: VartalapDeviceKeyStore,
        epochs: AuthorityEpochCache,
    ): CommercialActionAuthorityResolver = TrustVerifiedCommercialActionAuthorityResolver(
        credentials,
        verifier,
        keyStore,
        epochs,
    )
}
