package com.slikharev.shifttrack.invite

import androidx.lifecycle.SavedStateHandle
import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.remote.InviteDocument
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class InviteViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeInviteRepository: FakeInviteRepository
    private lateinit var viewModel: InviteViewModel

    private val loggedInSession = object : UserSession { override val currentUserId = "guest-uid" }
    private val loggedOutSession = object : UserSession { override val currentUserId = null }

    private val mockAppDataStore = mockk<AppDataStore>(relaxed = true)

    private fun buildViewModel(
        token: String = "test-token-abc",
        userSession: UserSession = loggedInSession,
    ): InviteViewModel = InviteViewModel(
        savedStateHandle = SavedStateHandle(mapOf("token" to token)),
        inviteRepository = fakeInviteRepository,
        userSession = userSession,
        appDataStore = mockAppDataStore,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeInviteRepository = FakeInviteRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── loadInvite ────────────────────────────────────────────────────────

    @Test
    fun `loadInvite transitions to Valid when invite exists and is not claimed or expired`() =
        testScope.runTest {
            val invite = validInvite()
            fakeInviteRepository.invite = invite

            viewModel = buildViewModel()
            advanceUntilIdle()

            assertEquals(InviteUiState.Valid(invite), viewModel.uiState.value)
        }

    @Test
    fun `loadInvite transitions to NotFound when getInvite returns null`() =
        testScope.runTest {
            fakeInviteRepository.invite = null

            viewModel = buildViewModel()
            advanceUntilIdle()

            assertEquals(InviteUiState.NotFound, viewModel.uiState.value)
        }

    @Test
    fun `loadInvite transitions to Expired when invite is expired`() =
        testScope.runTest {
            fakeInviteRepository.invite = validInvite(expiresAt = System.currentTimeMillis() - 1_000L)

            viewModel = buildViewModel()
            advanceUntilIdle()

            assertEquals(InviteUiState.Expired, viewModel.uiState.value)
        }

    @Test
    fun `loadInvite transitions to AlreadyClaimed when invite is claimed`() =
        testScope.runTest {
            fakeInviteRepository.invite = validInvite(claimed = true)

            viewModel = buildViewModel()
            advanceUntilIdle()

            assertEquals(InviteUiState.AlreadyClaimed, viewModel.uiState.value)
        }

    @Test
    fun `loadInvite transitions to Error when repository throws`() =
        testScope.runTest {
            fakeInviteRepository.throwOnGet = true

            viewModel = buildViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue("Expected Error state, got $state", state is InviteUiState.Error)
        }

    // ── accept ────────────────────────────────────────────────────────────

    @Test
    fun `accept transitions to Success with host display name`() =
        testScope.runTest {
            val invite = validInvite(hostDisplayName = "Alice")
            fakeInviteRepository.invite = invite
            fakeInviteRepository.redeemResult = RedeemResult.Success

            viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.accept()
            advanceUntilIdle()

            assertEquals(InviteUiState.Success("Alice"), viewModel.uiState.value)
        }

    @Test
    fun `accept transitions to AlreadyClaimed on race condition`() =
        testScope.runTest {
            fakeInviteRepository.invite = validInvite()
            fakeInviteRepository.redeemResult = RedeemResult.AlreadyClaimed

            viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.accept()
            advanceUntilIdle()

            assertEquals(InviteUiState.AlreadyClaimed, viewModel.uiState.value)
        }

    @Test
    fun `accept transitions to Error when user is not logged in`() =
        testScope.runTest {
            fakeInviteRepository.invite = validInvite()

            viewModel = buildViewModel(userSession = loggedOutSession)
            advanceUntilIdle()

            viewModel.accept()
            // No coroutine needed — state is set synchronously
            val state = viewModel.uiState.value
            assertTrue("Expected Error state, got $state", state is InviteUiState.Error)
        }

    @Test
    fun `accept is a no-op when state is not Valid`() =
        testScope.runTest {
            fakeInviteRepository.invite = null // → NotFound

            viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.accept()
            advanceUntilIdle()

            // State should remain NotFound; redeemInvite should not be called
            assertEquals(InviteUiState.NotFound, viewModel.uiState.value)
            assertEquals(0, fakeInviteRepository.redeemCallCount)
        }

    // ── retry ─────────────────────────────────────────────────────────────

    @Test
    fun `retry re-fetches invite and transitions to Valid`() =
        testScope.runTest {
            fakeInviteRepository.throwOnGet = true

            viewModel = buildViewModel()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is InviteUiState.Error)

            // Fix the repository and retry
            fakeInviteRepository.throwOnGet = false
            fakeInviteRepository.invite = validInvite()

            viewModel.retry()
            advanceUntilIdle()

            assertTrue(
                "Expected Valid state after retry, got ${viewModel.uiState.value}",
                viewModel.uiState.value is InviteUiState.Valid,
            )
        }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun validInvite(
    token: String = "test-token-abc",
    hostDisplayName: String = "Bob",
    claimed: Boolean = false,
    expiresAt: Long = System.currentTimeMillis() + 86_400_000L,
) = InviteDocument(
    token = token,
    hostUid = "host-uid",
    hostDisplayName = hostDisplayName,
    createdAt = System.currentTimeMillis(),
    expiresAt = expiresAt,
    claimed = claimed,
)

private class FakeInviteRepository : InviteRepository {
    var invite: InviteDocument? = null
    var redeemResult: RedeemResult = RedeemResult.Success
    var throwOnGet = false
    var redeemCallCount = 0

    override suspend fun createInvite(hostUid: String, hostDisplayName: String): String =
        "generated-token"

    override suspend fun getInvite(token: String): InviteDocument? {
        if (throwOnGet) throw Exception("Network error")
        return invite
    }

    override suspend fun redeemInvite(token: String, guestUid: String): RedeemResult {
        redeemCallCount++
        return redeemResult
    }
}
