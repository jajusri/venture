package com.budcom.android.feature.dincharya.presentation

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.dincharya.domain.model.DincharyaGroup
import com.budcom.android.feature.dincharya.domain.model.DincharyaItem
import com.budcom.android.feature.dincharya.domain.model.FollowUpUrgency
import com.budcom.android.feature.dincharya.domain.repository.DincharyaRepository
import com.budcom.android.feature.dincharya.domain.usecase.GetDincharyaSnapshotUseCase
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.party.domain.model.NoteType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DincharyaViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(repository: FakeDincharyaRepository, company: FakeCompanySession): DincharyaViewModel =
        DincharyaViewModel(GetDincharyaSnapshotUseCase(repository), company)

    @Test
    fun `no company selected shows a message error state, never a crash`() = runTest(dispatcher) {
        val viewModel = createViewModel(FakeDincharyaRepository(), FakeCompanySession(null))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isInitialLoading)
        assertTrue(state.error is MasterDataUiError.Message)
        assertFalse(state.hasAnyContent)
    }

    @Test
    fun `successful load populates all three groups`() = runTest(dispatcher) {
        val repo = FakeDincharyaRepository()
        repo.followUps["co-a"] = DincharyaGroup(listOf(followUp("n1", "p1")), totalItems = 1)
        repo.confirmations["co-a"] = DincharyaGroup(listOf(confirmation("p2")), totalItems = 1)
        repo.contacts["co-a"] = DincharyaGroup(listOf(contact("p3")), totalItems = 1)

        val viewModel = createViewModel(repo, FakeCompanySession("co-a"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isInitialLoading)
        assertNull(state.error)
        assertEquals(1, state.followUps.size)
        assertEquals(1, state.pendingConfirmations.size)
        assertEquals(1, state.pendingContactCompletions.size)
        assertTrue(state.hasAnyContent)
    }

    @Test
    fun `an empty snapshot is a genuine empty state, not an error`() = runTest(dispatcher) {
        val viewModel = createViewModel(FakeDincharyaRepository(), FakeCompanySession("co-a"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasAnyContent)
        assertNull(state.error)
        assertFalse(state.isInitialLoading)
    }

    @Test
    fun `moreCount surfaces through to the UI state for the N-more disclosure`() = runTest(dispatcher) {
        val repo = FakeDincharyaRepository()
        repo.followUps["co-a"] = DincharyaGroup(listOf(followUp("n1", "p1")), totalItems = 5)

        val viewModel = createViewModel(repo, FakeCompanySession("co-a"))
        advanceUntilIdle()

        assertEquals(4, viewModel.uiState.value.followUpsMoreCount)
    }

    @Test
    fun `a repository failure surfaces an error state without crashing`() = runTest(dispatcher) {
        val repo = FakeDincharyaRepository().apply { shouldThrow = true }
        val viewModel = createViewModel(repo, FakeCompanySession("co-a"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val unexpected = state.error as MasterDataUiError.Unexpected
        assertEquals(com.budcom.android.core.common.UserVisibleErrorText.UNEXPECTED, unexpected.message)
        assertFalse(unexpected.message.contains("simulated"))
        assertFalse(state.isInitialLoading)
    }

    @Test
    fun `retry re-issues the load and clears a prior error once it succeeds`() = runTest(dispatcher) {
        val repo = FakeDincharyaRepository().apply { shouldThrow = true }
        val viewModel = createViewModel(repo, FakeCompanySession("co-a"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.error != null)

        repo.shouldThrow = false
        repo.followUps["co-a"] = DincharyaGroup(listOf(followUp("n1", "p1")), totalItems = 1)
        viewModel.onEvent(DincharyaEvent.Retry)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.error)
        assertEquals(1, state.followUps.size)
    }

    @Test
    fun `refresh reloads without leaving the initial-loading flag set`() = runTest(dispatcher) {
        val repo = FakeDincharyaRepository()
        val viewModel = createViewModel(repo, FakeCompanySession("co-a"))
        advanceUntilIdle()

        repo.followUps["co-a"] = DincharyaGroup(listOf(followUp("n1", "p1")), totalItems = 1)
        viewModel.onEvent(DincharyaEvent.Refresh)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRefreshing)
        assertFalse(state.isInitialLoading)
        assertEquals(1, state.followUps.size)
    }

    @Test
    fun `an item tap emits an OpenPartyDetail effect carrying that item's partyId`() = runTest(dispatcher) {
        val viewModel = createViewModel(FakeDincharyaRepository(), FakeCompanySession("co-a"))
        advanceUntilIdle()
        val emitted = mutableListOf<DincharyaEffect>()
        val job = launch { viewModel.effects.collect { emitted.add(it) } }
        advanceUntilIdle()

        viewModel.onEvent(DincharyaEvent.ItemTapped("party-42"))
        advanceUntilIdle()

        assertEquals(listOf<DincharyaEffect>(DincharyaEffect.OpenPartyDetail("party-42")), emitted)
        job.cancel()
    }

    @Test
    fun `switching companies triggers a fresh load scoped to the new company, never mixing data`() = runTest(dispatcher) {
        val repo = FakeDincharyaRepository()
        repo.followUps["co-a"] = DincharyaGroup(listOf(followUp("n-a", "p1", body = "Company A item")), 1)
        repo.followUps["co-b"] = DincharyaGroup(listOf(followUp("n-b", "p1", body = "Company B item")), 1)
        val company = FakeCompanySession("co-a")
        val viewModel = createViewModel(repo, company)
        advanceUntilIdle()
        assertEquals("Company A item", viewModel.uiState.value.followUps.single().body)

        company.selected.value = "co-b"
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("co-b", state.companyId)
        assertEquals(1, state.followUps.size)
        assertEquals("Company B item", state.followUps.single().body)
    }

    private fun followUp(noteId: String, partyId: String, body: String = "Follow-up body") = DincharyaItem.FollowUp(
        noteId = noteId,
        partyId = partyId,
        partyDisplayName = "Party $partyId",
        noteType = NoteType.FollowUp,
        body = body,
        dueAt = 1_000L,
        urgency = FollowUpUrgency.Overdue,
    )

    private fun confirmation(partyId: String) = DincharyaItem.PendingTallyConfirmation(
        partyId = partyId,
        partyDisplayName = "Party $partyId",
        pendingFieldLabels = listOf("Phone"),
        earliestExportedAt = 1_000L,
    )

    private fun contact(partyId: String) = DincharyaItem.PendingContactCompletion(
        partyId = partyId,
        partyDisplayName = "Party $partyId",
    )
}

private class FakeDincharyaRepository : DincharyaRepository {
    val followUps = mutableMapOf<String, DincharyaGroup<DincharyaItem.FollowUp>>()
    val confirmations = mutableMapOf<String, DincharyaGroup<DincharyaItem.PendingTallyConfirmation>>()
    val contacts = mutableMapOf<String, DincharyaGroup<DincharyaItem.PendingContactCompletion>>()
    var shouldThrow = false

    override suspend fun getFollowUps(companyId: String, limit: Int): DincharyaGroup<DincharyaItem.FollowUp> {
        if (shouldThrow) error("simulated repository failure")
        return followUps[companyId] ?: DincharyaGroup(emptyList(), 0)
    }

    override suspend fun getPendingTallyConfirmations(companyId: String, limit: Int): DincharyaGroup<DincharyaItem.PendingTallyConfirmation> {
        if (shouldThrow) error("simulated repository failure")
        return confirmations[companyId] ?: DincharyaGroup(emptyList(), 0)
    }

    override suspend fun getPendingContactCompletions(companyId: String, limit: Int): DincharyaGroup<DincharyaItem.PendingContactCompletion> {
        if (shouldThrow) error("simulated repository failure")
        return contacts[companyId] ?: DincharyaGroup(emptyList(), 0)
    }
}

private class FakeCompanySession(initial: String?) : CompanySessionPort {
    val selected = MutableStateFlow(initial)
    override fun observeSelectedCompanyId(): Flow<String?> = selected
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(selected.value, selected.value))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> =
        AppResult.Failure(AppError.Message("unused"))
}
