package com.jajusri.venture.app

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
import com.jajusri.venture.feature.settings.domain.model.ThemePreference
import com.jajusri.venture.feature.settings.domain.repository.ThemeObservation
import com.jajusri.venture.feature.settings.domain.repository.ThemePreferencesRepository
import com.jajusri.venture.navigation.VentureNavHost
import com.jajusri.venture.ui.theme.VentureTheme
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
            VentureTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VentureNavHost()
                }
            }
        }
    }
}
