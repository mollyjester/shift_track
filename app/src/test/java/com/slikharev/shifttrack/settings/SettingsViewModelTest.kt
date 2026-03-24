package com.slikharev.shifttrack.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.slikharev.shifttrack.auth.AuthRepository
import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.local.db.dao.LeaveBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.LeaveDao
import com.slikharev.shifttrack.data.local.db.dao.OvertimeBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.OvertimeDao
import com.slikharev.shifttrack.data.local.db.dao.ShiftDao
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.OvertimeBalanceEntity
import com.slikharev.shifttrack.data.remote.InviteDocument
import com.slikharev.shifttrack.invite.InviteRepository
import com.slikharev.shifttrack.invite.RedeemResult
import com.slikharev.shifttrack.widget.ShiftWidgetUpdater
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
    private val mockWidgetUpdater = mockk<ShiftWidgetUpdater>(relaxed = true)
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
            shiftDao = mockk(relaxed = true),
            leaveDao = mockk(relaxed = true),
            leaveBalanceDao = fakeLeaveBalanceDao,
            overtimeDao = mockk(relaxed = true),
            overtimeBalanceDao = fakeOvertimeBalanceDao,
            authRepository = mockAuthRepository,
            userSession = fakeUserSession,
            inviteRepository = fakeInviteRepository,
            widgetUpdater = mockWidgetUpdater,
            firestoreUserDataSource = mockk(relaxed = true),
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

    // ── updateLeaveTotalDaysByType ─────────────────────────────────────────

    @Test
    fun `updateLeaveTotalDaysByType creates new balance row when none exists`() = testScope.runTest {
        viewModel.updateLeaveTotalDaysByType("ANNUAL", 28f)
        testScheduler.advanceUntilIdle()

        val stored = fakeLeaveBalanceDao.storeByYearType["${LocalDate.now().year}-ANNUAL"]
        assertEquals(28f, stored?.totalDays)
        assertEquals(LocalDate.now().year, stored?.year)
        assertEquals("uid-test", stored?.userId)
    }

    @Test
    fun `updateLeaveTotalDaysByType updates existing balance row`() = testScope.runTest {
        val year = LocalDate.now().year
        fakeLeaveBalanceDao.storeByYearType["$year-ANNUAL"] = LeaveBalanceEntity(
            id = 1L, year = year, leaveType = "ANNUAL", totalDays = 20f, usedDays = 5f, userId = "uid-test",
        )

        viewModel.updateLeaveTotalDaysByType("ANNUAL", 30f)
        testScheduler.advanceUntilIdle()

        val stored = fakeLeaveBalanceDao.storeByYearType["$year-ANNUAL"]
        assertEquals(30f, stored?.totalDays)
        // usedDays should be preserved from the existing row
        assertEquals(5f, stored?.usedDays)
    }

    @Test
    fun `updateLeaveTotalDaysByType sets savedMessage on success`() = testScope.runTest {
        viewModel.updateLeaveTotalDaysByType("ANNUAL", 25f)
        testScheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.savedMessage)
        assertNull(viewModel.uiState.value.error)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `updateLeaveTotalDaysByType rejects negative`() {
        viewModel.updateLeaveTotalDaysByType("ANNUAL", -5f)
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
        viewModel.updateLeaveTotalDaysByType("ANNUAL", 20f)
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

        assertEquals("https://mollyjester.github.io/shift_track/invite.html?token=fake-token-123", viewModel.pendingInviteLink.value)
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
    val storeByYearType = mutableMapOf<String, LeaveBalanceEntity>() // key = "$year-$leaveType"
    private val flow = MutableStateFlow<List<LeaveBalanceEntity>>(emptyList())

    private fun key(year: Int, leaveType: String) = "$year-$leaveType"

    override suspend fun getBalanceForYearAndType(userId: String, year: Int, leaveType: String): LeaveBalanceEntity? =
        storeByYearType[key(year, leaveType)]?.takeIf { it.userId == userId }

    override fun observeBalanceForYearAndType(userId: String, year: Int, leaveType: String): Flow<LeaveBalanceEntity?> =
        flow.map { list -> list.firstOrNull { it.userId == userId && it.year == year && it.leaveType == leaveType } }

    override fun observeAllBalancesForYear(userId: String, year: Int): Flow<List<LeaveBalanceEntity>> =
        flow.map { list -> list.filter { it.userId == userId && it.year == year } }

    override suspend fun getAllBalancesForYear(userId: String, year: Int): List<LeaveBalanceEntity> =
        storeByYearType.values.filter { it.userId == userId && it.year == year }

    override suspend fun upsert(balance: LeaveBalanceEntity): Long {
        val updated = balance.copy(id = 1L)
        storeByYearType[key(balance.year, balance.leaveType)] = updated
        flow.value = storeByYearType.values.toList()
        return 1L
    }

    override suspend fun update(balance: LeaveBalanceEntity) {
        storeByYearType[key(balance.year, balance.leaveType)] = balance
        flow.value = storeByYearType.values.toList()
    }

    override suspend fun delete(balance: LeaveBalanceEntity) {
        storeByYearType.remove(key(balance.year, balance.leaveType))
        flow.value = storeByYearType.values.toList()
    }

    override suspend fun deleteAllForUser(userId: String) {
        storeByYearType.entries.removeIf { it.value.userId == userId }
        flow.value = storeByYearType.values.toList()
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

    override suspend fun deleteAllForUser(userId: String) {
        if (stored?.userId == userId) { stored = null; flow.value = null }
    }
}
