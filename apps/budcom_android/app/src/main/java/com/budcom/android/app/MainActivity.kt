package com.budcom.android.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.budcom.android.navigation.BudcomNavHost
import com.budcom.android.ui.theme.BudcomTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for Jetpack Compose navigation.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BudcomTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BudcomNavHost()
                }
            }
        }
    }
}
