package com.budcom.android.feature.settings.data.local

import android.content.Context
import com.budcom.android.BuildConfig
import com.budcom.android.feature.settings.domain.model.ApplicationInformation
import com.budcom.android.feature.settings.domain.port.ApplicationIdentityPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuildConfigApplicationIdentityPort @Inject constructor(
    @ApplicationContext private val context: Context,
) : ApplicationIdentityPort {
    override fun read(): ApplicationInformation = ApplicationInformation(
        appName = BuildConfig.APP_NAME,
        packageName = context.packageName,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        buildTypeLabel = if (BuildConfig.DEBUG) "Debug" else "Release",
    )
}
