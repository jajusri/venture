package com.budcom.android.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Decides the nav graph's start destination once, at process start, from the single authoritative
 * [StartupRoutingState] resolved by [ResolveStartupRoutingState]. This is the one place that keeps
 * [com.budcom.android.feature.dashboard.presentation.DashboardViewModel] (and the business API
 * calls its init block fires) from ever composing before that classification completes, and the
 * one place that keeps a mandatory secure-pairing state ([StartupRoutingState.PairingRequired],
 * [StartupRoutingState.PairingPending]) from ever being bypassed by routing straight to Dashboard.
 */
@HiltViewModel
class AppRootViewModel @Inject constructor(
    private val resolveStartupRoutingState: ResolveStartupRoutingState,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            _startDestination.value = when (resolveStartupRoutingState()) {
                StartupRoutingState.PairingRequired,
                StartupRoutingState.PairingPending,
                -> Routes.SECURE_PAIRING

                StartupRoutingState.LegacyEligible,
                StartupRoutingState.SecureActive,
                StartupRoutingState.RePairRequired,
                StartupRoutingState.SecureCredentialUnavailable,
                -> Routes.HOME
            }
        }
    }
}
