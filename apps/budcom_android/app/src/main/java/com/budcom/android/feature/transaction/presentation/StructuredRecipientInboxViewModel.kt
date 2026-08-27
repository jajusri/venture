package com.budcom.android.feature.transaction.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.transaction.domain.model.StructuredRecipientInboxEntry
import com.budcom.android.feature.transaction.domain.repository.StructuredRecipientInboxRepository
import com.budcom.android.navigation.ReceivedStructuredNavigation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StructuredInboxItemUi(
    val envelopeId: String,
    val senderBusinessId: String,
    val orderId: String,
    val orderVersion: Int,
    val label: String,
    val entry: StructuredRecipientInboxEntry,
)

data class StructuredRecipientInboxUiState(
    val isLoading: Boolean = true,
    val items: List<StructuredInboxItemUi> = emptyList(),
)

sealed interface StructuredRecipientInboxEvent {
    data class OpenItem(val envelopeId: String) : StructuredRecipientInboxEvent
}

sealed interface StructuredRecipientInboxNavigation {
    data class OpenRoute(val route: String) : StructuredRecipientInboxNavigation
}

@HiltViewModel
class StructuredRecipientInboxViewModel @Inject constructor(
    private val inboxRepository: StructuredRecipientInboxRepository,
    private val companySession: CompanySessionPort,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StructuredRecipientInboxUiState())
    val uiState: StateFlow<StructuredRecipientInboxUiState> = _uiState.asStateFlow()

    private val navigationChannel = Channel<StructuredRecipientInboxNavigation>(Channel.BUFFERED)
    val navigation = navigationChannel.receiveAsFlow()

    private var entriesByEnvelope: Map<String, StructuredRecipientInboxEntry> = emptyMap()

    init {
        viewModelScope.launch { refresh() }
    }

    fun onEvent(event: StructuredRecipientInboxEvent) {
        when (event) {
            is StructuredRecipientInboxEvent.OpenItem -> openItem(event.envelopeId)
        }
    }

    private fun openItem(envelopeId: String) {
        val entry = entriesByEnvelope[envelopeId] ?: return
        val route = ReceivedStructuredNavigation.routeFor(entry) ?: return
        viewModelScope.launch {
            navigationChannel.send(StructuredRecipientInboxNavigation.OpenRoute(route))
        }
    }

    private suspend fun refresh() {
        val companyId = companySession.observeSelectedCompanyId().first() ?: run {
            _uiState.update { it.copy(isLoading = false, items = emptyList()) }
            return
        }
        val entries = inboxRepository.findAll(companyId)
            .filter { ReceivedStructuredNavigation.kindFor(it) != ReceivedStructuredNavigation.Kind.Unsupported }
        entriesByEnvelope = entries.associateBy { it.envelopeId }
        _uiState.update {
            it.copy(
                isLoading = false,
                items = entries.map { entry -> entry.toUi() },
            )
        }
    }

    private fun StructuredRecipientInboxEntry.toUi(): StructuredInboxItemUi {
        val label = when (ReceivedStructuredNavigation.kindFor(this)) {
            ReceivedStructuredNavigation.Kind.ReceivedRevision -> "Revised order v$objectVersion"
            ReceivedStructuredNavigation.Kind.ReceivedOrder -> "Received order"
            ReceivedStructuredNavigation.Kind.Unsupported -> "Unsupported item"
        }
        return StructuredInboxItemUi(
            envelopeId = envelopeId,
            senderBusinessId = senderBusinessId,
            orderId = objectId,
            orderVersion = objectVersion,
            label = label,
            entry = this,
        )
    }
}
