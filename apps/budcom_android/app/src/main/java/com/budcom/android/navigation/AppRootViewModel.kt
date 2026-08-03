package com.budcom.android.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.connection.ConnectorEnrolmentGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Decides the nav graph's start destination once, at process start: first-install Connector
 * discovery/enrolment when nothing is paired yet on a real device, Dashboard otherwise.
 *
 * This is the one place that keeps [com.budcom.android.feature.dashboard.presentation.DashboardViewModel]
 * (and the business API calls its init block fires) from ever composing before a Connector is
 * paired — see [ConnectorEnrolmentGate].
 */
@HiltViewModel
class AppRootViewModel @Inject constructor(
    private val enrolmentGate: ConnectorEnrolmentGate,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            _startDestination.value = if (enrolmentGate.needsEnrolment()) {
                Routes.CONNECTOR_DISCOVERY
            } else {
                Routes.HOME
            }
        }
    }
}
