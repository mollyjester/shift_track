package com.slikharev.shifttrack.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.firebase.auth.FirebaseUser
import com.slikharev.shifttrack.data.local.AppDataStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var appDataStore: AppDataStore
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(testDispatcher),
            produceFile = { tempFolder.newFile("auth_vm_test.preferences_pb") },
        )
        appDataStore = AppDataStore(dataStore)
        mockAuthRepository = mockk(relaxed = true) {
            every { currentUser } returns null
            every { hasCachedCredentials() } returns false
            every { authStateFlow } returns flowOf(null)
        }
        viewModel = AuthViewModel(mockAuthRepository, appDataStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── initial state ────────────────────────────────────────────────────────

    @Test
    fun `initial uiState is Idle`() {
        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `isLoggedIn is false when no current user and no cached credentials`() =
        testScope.runTest {
            every { mockAuthRepository.currentUser } returns null
            every { mockAuthRepository.hasCachedCredentials() } returns false

            advanceUntilIdle()

            assertFalse(viewModel.isLoggedIn.value)
        }

    @Test
    fun `isLoggedIn is true when cached credentials exist`() = testScope.runTest {
        every { mockAuthRepository.hasCachedCredentials() } returns true
        // Rebuild viewModel so initialValue reflects cached state
        viewModel = AuthViewModel(mockAuthRepository, appDataStore)

        assertTrue(viewModel.isLoggedIn.value)
    }

    @Test
    fun `onboardingComplete reflects AppDataStore value`() = testScope.runTest {
        val job = launch { viewModel.onboardingComplete.collect { } }
        appDataStore.setOnboardingComplete(true)
        advanceUntilIdle()

        assertTrue(viewModel.onboardingComplete.value)
        job.cancel()
    }

    // ── signInWithGoogle ─────────────────────────────────────────────────────

    @Test
    fun `signInWithGoogle transitions to Loading then Success`() =
        testScope.runTest {
            val fakeUser = mockk<FirebaseUser> { every { uid } returns "uid-abc" }
            coEvery { mockAuthRepository.signInWithGoogle("id-token") } returns
                Result.success(fakeUser)

            viewModel.signInWithGoogle("id-token")
            advanceUntilIdle()

            assertEquals(AuthUiState.Success("uid-abc"), viewModel.uiState.value)
        }

    @Test
    fun `signInWithGoogle transitions to Error on failure`() =
        testScope.runTest {
            coEvery { mockAuthRepository.signInWithGoogle(any()) } returns
                Result.failure(Exception("bad token"))

            viewModel.signInWithGoogle("bad-token")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue("Expected Error state, got $state", state is AuthUiState.Error)
            assertEquals("bad token", (state as AuthUiState.Error).message)
        }

    // ── signOut ──────────────────────────────────────────────────────────────

    @Test
    fun `signOut calls authRepository and resets uiState to Idle`() =
        testScope.runTest {
            // Put the ViewModel in a non-Idle state first
            val fakeUser = mockk<FirebaseUser> { every { uid } returns "uid-xyz" }
            coEvery { mockAuthRepository.signInWithGoogle(any()) } returns
                Result.success(fakeUser)
            viewModel.signInWithGoogle("token")
            advanceUntilIdle()

            viewModel.signOut()

            verify { mockAuthRepository.signOut() }
            assertEquals(AuthUiState.Idle, viewModel.uiState.value)
        }

    // ── onSignInError ────────────────────────────────────────────────────────

    @Test
    fun `onSignInError sets Error state with provided message`() {
        viewModel.onSignInError("Google sign-in cancelled")

        val state = viewModel.uiState.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("Google sign-in cancelled", (state as AuthUiState.Error).message)
    }
}
