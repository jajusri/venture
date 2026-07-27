package com.budcom.android.core.network

import javax.inject.Qualifier

/**
 * Qualifier for the long-timeout OkHttp/Retrofit stack used by blocking Connector sync POSTs.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SyncHttp
