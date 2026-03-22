package com.slikharev.shifttrack.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.slikharev.shifttrack.auth.AuthRepository
import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.local.db.dao.LeaveBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.OvertimeBalanceDao
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.OvertimeBalanceEntity
import com.slikharev.shifttrack.data.remote.InviteDocument
import com.slikharev.shifttrack.invite.InviteRepository
import com.slikharev.shifttrack.invite.RedeemResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var appDataStore: AppDataStore
    private lateinit var fakeLeaveBalanceDao: FakeLeaveBalanceDao
    private lateinit var fakeOvertimeBalanceDao: FakeOvertimeBalanceDao
    private val fakeUserSession = object : UserSession { override val currentUserId = "uid-test" }
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var fakeInviteRepository: FakeInviteRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(testDispatcher),
            produceFile = { tempFolder.newFile("settings_test_prefs.preferences_pb") },
        )
        appDataStore = AppDataStore(dataStore)
        fakeLeaveBalanceDao = FakeLeaveBalanceDao()
        fakeOvertimeBalanceDao = FakeOvertimeBalanceDao()
        fakeInviteRepository = FakeInviteRepository()
        mockAuthRepository = mockk(relaxed = true) {
            every { currentUser } returns null
        }
        viewModel = SettingsViewModel(
            appDataStore = appDataStore,
            leaveBalanceDao = fakeLeaveBalanceDao,
            overtimeBalanceDao = fakeOvertimeBalanceDao,
            authRepository = mockAuthRepository,
            userSession = fakeUserSession,
            inviteRepository = fakeInviteRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── updateAnchor ─────────────────────────────────────────────────────────

    @Test
    fun `updateAnchor persists date and cycle index to AppDataStore`() = testScope.runTest {
        val date = LocalDate.of(2025, 3, 15)

        viewModel.updateAnchor(date, 2)
        testScheduler.advanceUntilIdle()

        assertEquals("2025-03-15", appDataStore.anchorDate.first())
        assertEquals(2, appDataStore.anchorCycleIndex.first())
    }

    @Test
    fun `updateAnchor sets savedMessage on success`() = testScope.runTest {
        viewModel.updateAnchor(LocalDate.now(), 0)
        testScheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.savedMessage)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `updateAnchor clears isSaving after completion`() = testScope.runTest {
        viewModel.updateAnchor(LocalDate.now(), 1)
        testScheduler.advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isSaving)
    }

    // ── updateLeaveTotalDays ───────────────────────────────────────────────

    @Test
    fun `updateLeaveTotalDays creates new balance row when none exists`() = testScope.runTest {
        viewModel.updateLeaveTotalDays(28f)
        testScheduler.advanceUntilIdle()

        assertEquals(28f, fakeLeaveBalanceDao.stored?.totalDays)
        assertEquals(LocalDate.now().year, fakeLeaveBalanceDao.stored?.year)
        assertEquals("uid-test", fakeLeaveBalanceDao.stored?.userId)
    }

    @Test
    fun `updateLeaveTotalDays updates existing balance row`() = testScope.runTest {
        val year = LocalDate.now().year
        fakeLeaveBalanceDao.stored = LeaveBalanceEntity(id = 1L, year = year, totalDays = 20f, usedDays = 5f, userId = "uid-test")

        viewModel.updateLeaveTotalDays(30f)
        testScheduler.advanceUntilIdle()

        assertEquals(30f, fakeLeaveBalanceDao.stored?.totalDays)
        // usedDays should be preserved from the existing row
        assertEquals(5f, fakeLeaveBalanceDao.stored?.usedDays)
    }

    @Test
    fun `updateLeaveTotalDays sets savedMessage on success`() = testScope.runTest {
        viewModel.updateLeaveTotalDays(25f)
        testScheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.savedMessage)
        assertNull(viewModel.uiState.value.error)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `updateLeaveTotalDays rejects zero`() {
        viewModel.updateLeaveTotalDays(0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `updateLeaveTotalDays rejects negative`() {
        viewModel.updateLeaveTotalDays(-5f)
    }

    // ── updateCompensatedOvertimeHours ────────────────────────────────────

    @Test
    fun `updateCompensatedOvertimeHours updates existing balance`() = testScope.runTest {
        val year = LocalDate.now().year
        fakeOvertimeBalanceDao.stored = OvertimeBalanceEntity(
            id = 1L, year = year, totalHours = 10f, compensatedHours = 0f, userId = "uid-test"
        )

        viewModel.updateCompensatedOvertimeHours(4f)
        testScheduler.advanceUntilIdle()

        assertEquals(4f, fakeOvertimeBalanceDao.stored?.compensatedHours)
        // totalHours should be unchanged
        assertEquals(10f, fakeOvertimeBalanceDao.stored?.totalHours)
    }

    @Test
    fun `updateCompensatedOvertimeHours is no-op when no balance row exists`() = testScope.runTest {
        viewModel.updateCompensatedOvertimeHours(2f)
        testScheduler.advanceUntilIdle()

        assertNull(fakeOvertimeBalanceDao.stored)
        // No error — just silent no-op
        assertNull(viewModel.uiState.value.error)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `updateCompensatedOvertimeHours rejects negative`() {
        viewModel.updateCompensatedOvertimeHours(-1f)
    }

    // ── todayShiftLabel ───────────────────────────────────────────────────

    @Test
    fun `todayShiftLabel emits null when anchor not set`() = testScope.runTest {
        val emitted = mutableListOf<String?>()
        val job = launch { viewModel.todayShiftLabel.collect { emitted.add(it) } }
        testScheduler.advanceUntilIdle()

        // First emission should be null since no anchor is configured
        assertEquals(null, emitted.firstOrNull())
        job.cancel()
    }

    @Test
    fun `todayShiftLabel emits non-null once anchor is saved`() = testScope.runTest {
        val emitted = mutableListOf<String?>()
        val job = launch { viewModel.todayShiftLabel.collect { emitted.add(it) } }

        viewModel.updateAnchor(LocalDate.now(), 0)
        testScheduler.advanceUntilIdle()

        assertNotNull(emitted.filterNotNull().firstOrNull())
        job.cancel()
    }

    // ── signOut ───────────────────────────────────────────────────────────

    @Test
    fun `signOut calls authRepository signOut and invokes callback`() {
        var called = false
        viewModel.signOut { called = true }

        verify { mockAuthRepository.signOut() }
        assertEquals(true, called)
    }

    // ── clearMessage ──────────────────────────────────────────────────────

    @Test
    fun `clearMessage clears savedMessage`() = testScope.runTest {
        viewModel.updateLeaveTotalDays(20f)
        testScheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.savedMessage)

        viewModel.clearMessage()

        assertNull(viewModel.uiState.value.savedMessage)
        assertNull(viewModel.uiState.value.error)
    }

    // ── generateInvite ────────────────────────────────────────────────────

    @Test
    fun `generateInvite sets pendingInviteLink with deep link`() = testScope.runTest {
        viewModel.generateInvite()
        testScheduler.advanceUntilIdle()

        assertEquals("shiftapp://invite/fake-token-123", viewModel.pendingInviteLink.value)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `generateInvite on repository failure sets error message`() = testScope.runTest {
        fakeInviteRepository.shouldThrow = true
        viewModel.generateInvite()
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.pendingInviteLink.value)
        assertNotNull(viewModel.uiState.value.error)
    }
}

private class FakeInviteRepository : InviteRepository {
    var nextToken = "fake-token-123"
    var shouldThrow = false

    override suspend fun createInvite(hostUid: String, hostDisplayName: String): String {
        if (shouldThrow) throw Exception("Network error")
        return nextToken
    }

    override suspend fun getInvite(token: String): InviteDocument? = null

    override suspend fun redeemInvite(token: String, guestUid: String): RedeemResult =
        RedeemResult.Success
}

// ── Fake DAOs ────────────────────────────────────────────────────────────────

private class FakeLeaveBalanceDao : LeaveBalanceDao {
    var stored: LeaveBalanceEntity? = null
    private val flow = MutableStateFlow<LeaveBalanceEntity?>(null)

    override suspend fun getBalanceForYear(userId: String, year: Int): LeaveBalanceEntity? =
        stored?.takeIf { it.userId == userId && it.year == year }

    override fun observeBalanceForYear(userId: String, year: Int): Flow<LeaveBalanceEntity?> =
        flow.map { it?.takeIf { b -> b.userId == userId && b.year == year } }

    override suspend fun upsert(balance: LeaveBalanceEntity): Long {
        stored = balance.copy(id = 1L)
        flow.value = stored
        return 1L
    }

    override suspend fun update(balance: LeaveBalanceEntity) {
        stored = balance
        flow.value = stored
    }

    override suspend fun delete(balance: LeaveBalanceEntity) {
        stored = null
        flow.value = null
    }
}

private class FakeOvertimeBalanceDao : OvertimeBalanceDao {
    var stored: OvertimeBalanceEntity? = null
    private val flow = MutableStateFlow<OvertimeBalanceEntity?>(null)

    override suspend fun getBalanceForYear(userId: String, year: Int): OvertimeBalanceEntity? =
        stored?.takeIf { it.userId == userId && it.year == year }

    override fun observeBalanceForYear(userId: String, year: Int): Flow<OvertimeBalanceEntity?> =
        flow.map { it?.takeIf { b -> b.userId == userId && b.year == year } }

    override suspend fun upsert(balance: OvertimeBalanceEntity): Long {
        stored = balance.copy(id = 1L)
        flow.value = stored
        return 1L
    }

    override suspend fun update(balance: OvertimeBalanceEntity) {
        stored = balance
        flow.value = stored
    }

    override suspend fun delete(balance: OvertimeBalanceEntity) {
        stored = null
        flow.value = null
    }
}
