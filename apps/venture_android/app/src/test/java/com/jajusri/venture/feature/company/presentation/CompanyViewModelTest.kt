package com.jajusri.venture.feature.company.presentation

import androidx.lifecycle.SavedStateHandle
import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.company.domain.model.CompanyDiscoverySnapshot
import com.jajusri.venture.feature.company.domain.model.CompanySelectionOutcome
import com.jajusri.venture.feature.company.domain.model.ConnectorCompany
import com.jajusri.venture.feature.company.domain.model.ConnectorSessionSnapshot
import com.jajusri.venture.feature.company.domain.model.SessionSelectedCompany
import com.jajusri.venture.feature.company.domain.model.SessionValidationOutcome
import com.jajusri.venture.feature.company.domain.repository.CompanyRepository
import com.jajusri.venture.feature.company.domain.usecase.LoadCompaniesUseCase
import com.jajusri.venture.feature.company.domain.usecase.RestoreCompanySelectionUseCase
import com.jajusri.venture.feature.company.domain.usecase.SelectCompanyUseCase
import com.jajusri.venture.feature.company.domain.usecase.ValidateSessionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanyViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeCompanyRepository
    private lateinit var viewModel: CompanyViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeCompanyRepository()
        viewModel = CompanyViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repository,
            loadCompanies = LoadCompaniesUseCase(repository),
            restoreSelection = RestoreCompanySelectionUseCase(repository),
            selectCompany = SelectCompanyUseCase(repository),
            validateSession = ValidateSessionUseCase(repository),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads companies and applies local search filtering`() = runTest(dispatcher) {
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.companies.size)
        assertEquals(2, viewModel.uiState.value.filteredCompanies.size)

        viewModel.onEvent(CompanyEvent.SearchChanged("estima"))
        assertEquals(1, viewModel.uiState.value.filteredCompanies.size)
        assertEquals("estimation", viewModel.uiState.value.filteredCompanies.first().id)
    }

    @Test
    fun `restores persisted selected company on startup`() = runTest(dispatcher) {
        repository.selectedIdFlow.value = "estimation"
        advanceUntilIdle()
        assertEquals("estimation", viewModel.uiState.value.selectedCompanyId)
    }

    @Test
    fun `offline load maps to offline error state`() = runTest(dispatcher) {
        repository.loadResult = AppResult.Failure(AppError.Offline())
        viewModel.onEvent(CompanyEvent.Refresh)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.error is CompanyUiError.Offline)
    }

    @Test
    fun `select company validates and marks session validated`() = runTest(dispatcher) {
        viewModel.onEvent(CompanyEvent.SelectCompany("estimation"))
        advanceUntilIdle()
        assertEquals("estimation", viewModel.uiState.value.selectedCompanyId)
        assertTrue(viewModel.uiState.value.sessionValidated)
    }

    @Test
    fun `search query is written to saved state`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val vm = CompanyViewModel(
            savedStateHandle = handle,
            repository = repository,
            loadCompanies = LoadCompaniesUseCase(repository),
            restoreSelection = RestoreCompanySelectionUseCase(repository),
            selectCompany = SelectCompanyUseCase(repository),
            validateSession = ValidateSessionUseCase(repository),
        )
        vm.onEvent(CompanyEvent.SearchChanged("estima"))
        assertEquals("estima", handle.get<String>(com.jajusri.venture.navigation.Routes.QUERY_ARG))
    }
}

private class FakeCompanyRepository : CompanyRepository {
    val selectedIdFlow = MutableStateFlow<String?>(null)
    var loadResult: AppResult<CompanyDiscoverySnapshot> = AppResult.Success(
        CompanyDiscoverySnapshot(
            items = listOf(
                ConnectorCompany("estimation", "ESTIMATION", null, null, "INR"),
                ConnectorCompany("abc-trading", "ABC TRADING", null, null, "INR"),
            ),
            schemaVersion = "1.0.0",
            dataFreshnessAt = "2026-01-01T00:00:00Z",
            contractVersion = "1",
            status = "SUCCESS",
            tallyReachable = true,
            dataQualityStatus = null,
            dataQualityReason = null,
            reason = null,
        ),
    )
    var restoreResult: AppResult<SessionValidationOutcome?> = AppResult.Success(null)
    var selectResult: AppResult<SessionValidationOutcome> = AppResult.Success(
        SessionValidationOutcome(
            status = "SUCCESS",
            session = sampleSession("estimation"),
            reason = null,
            companyId = "estimation",
            companyName = "ESTIMATION",
            httpStatus = 200,
        ),
    )
    var validateResult: AppResult<SessionValidationOutcome> = selectResult

    override fun observeSelectedCompanyId(): Flow<String?> = selectedIdFlow

    override suspend fun loadCompanies(): AppResult<CompanyDiscoverySnapshot> = loadResult

    override suspend fun refreshCompanies(): AppResult<CompanyDiscoverySnapshot> = loadResult

    override suspend fun getSession(): AppResult<ConnectorSessionSnapshot> =
        AppResult.Success(sampleSession(selectedIdFlow.value))

    override suspend fun restoreSelection(): AppResult<SessionValidationOutcome?> = restoreResult

    override suspend fun selectCompany(companyId: String): AppResult<SessionValidationOutcome> {
        selectedIdFlow.value = companyId
        return selectResult
    }

    override suspend fun validateSession(): AppResult<SessionValidationOutcome> = validateResult

    override suspend fun clearSelection(): AppResult<Unit> {
        selectedIdFlow.value = null
        return AppResult.Success(Unit)
    }
}

private fun sampleSession(selectedId: String?): ConnectorSessionSnapshot = ConnectorSessionSnapshot(
    sessionId = "s1",
    selectedCompany = selectedId?.let { SessionSelectedCompany(id = it, name = "ESTIMATION") },
    connectionStatus = "connected",
    connectorVersion = "0.4.0",
    erpType = "tally",
    selectedAt = null,
    lastValidatedAt = null,
    createdAt = "2026-01-01T00:00:00Z",
    contractVersion = "1",
)
