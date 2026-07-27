package com.budcom.android.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budcom.android.feature.settings.domain.model.ThemePreference
import com.budcom.android.feature.settings.domain.repository.ThemeObservation
import com.budcom.android.feature.settings.domain.repository.ThemePreferencesRepository
import com.budcom.android.navigation.BudcomNavHost
import com.budcom.android.ui.theme.BudcomTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity host for Jetpack Compose navigation.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themePreferences: ThemePreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeObservation by themePreferences.observeTheme()
                .collectAsStateWithLifecycle(initialValue = ThemeObservation.Available(ThemePreference.System))
            val darkTheme = when (val observation = themeObservation) {
                is ThemeObservation.Available -> when (observation.preference) {
                    ThemePreference.System -> isSystemInDarkTheme()
                    ThemePreference.Light -> false
                    ThemePreference.Dark -> true
                }
                is ThemeObservation.Invalid -> isSystemInDarkTheme()
            }
            BudcomTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BudcomNavHost()
                }
            }
        }
    }
}
