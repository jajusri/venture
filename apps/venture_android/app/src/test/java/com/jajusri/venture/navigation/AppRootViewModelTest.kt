package com.jajusri.venture.navigation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppRootViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `routes to secure pairing when PairingRequired`() = runTest(dispatcher) {
        val viewModel = AppRootViewModel(FakeResolveStartupRoutingState(StartupRoutingState.PairingRequired))

        advanceUntilIdle()

        assertEquals(Routes.SECURE_PAIRING, viewModel.startDestination.value)
    }

    @Test
    fun `routes to secure pairing when PairingPending`() = runTest(dispatcher) {
        val viewModel = AppRootViewModel(FakeResolveStartupRoutingState(StartupRoutingState.PairingPending))

        advanceUntilIdle()

        assertEquals(Routes.SECURE_PAIRING, viewModel.startDestination.value)
    }

    @Test
    fun `routes to Dashboard when LegacyEligible`() = runTest(dispatcher) {
        val viewModel = AppRootViewModel(FakeResolveStartupRoutingState(StartupRoutingState.LegacyEligible))

        advanceUntilIdle()

        assertEquals(Routes.HOME, viewModel.startDestination.value)
    }

    @Test
    fun `routes to Dashboard when SecureActive`() = runTest(dispatcher) {
        val viewModel = AppRootViewModel(FakeResolveStartupRoutingState(StartupRoutingState.SecureActive))

        advanceUntilIdle()

        assertEquals(Routes.HOME, viewModel.startDestination.value)
    }

    @Test
    fun `routes to Dashboard when RePairRequired — cached data must remain reachable`() = runTest(dispatcher) {
        val viewModel = AppRootViewModel(FakeResolveStartupRoutingState(StartupRoutingState.RePairRequired))

        advanceUntilIdle()

        assertEquals(Routes.HOME, viewModel.startDestination.value)
    }

    @Test
    fun `routes to Dashboard when SecureCredentialUnavailable — cached data must remain reachable`() = runTest(dispatcher) {
        val viewModel = AppRootViewModel(FakeResolveStartupRoutingState(StartupRoutingState.SecureCredentialUnavailable))

        advanceUntilIdle()

        assertEquals(Routes.HOME, viewModel.startDestination.value)
    }

    @Test
    fun `start destination is null until resolution completes, never defaulting early`() = runTest(dispatcher) {
        val viewModel = AppRootViewModel(FakeResolveStartupRoutingState(StartupRoutingState.PairingRequired))

        assertEquals(null, viewModel.startDestination.value)

        advanceUntilIdle()
        assertEquals(Routes.SECURE_PAIRING, viewModel.startDestination.value)
    }
}

private class FakeResolveStartupRoutingState(
    private val state: StartupRoutingState,
) : ResolveStartupRoutingState {
    override suspend fun invoke(): StartupRoutingState = state
}
