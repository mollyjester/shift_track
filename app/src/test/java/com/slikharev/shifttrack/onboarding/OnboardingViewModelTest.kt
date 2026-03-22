package com.slikharev.shifttrack.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.local.db.dao.LeaveBalanceDao
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

/**
 * State-transition tests for [OnboardingViewModel].
 *
 * Infrastructure:
 * - Real [AppDataStore] backed by a temp-file [PreferenceDataStoreFactory] (avoids mocking
 *   the DataStore internals which are final).
 * - Fake [LeaveBalanceDao] — simple in-memory map; no Room dependency required for unit tests.
 * - Fake [AuthRepository] via a thin subclass-free anonymous object trick — only [currentUser]
 *   is needed and can return null safely.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var appDataStore: AppDataStore
    private lateinit var fakeLeaveBalanceDao: FakeLeaveBalanceDao
    private val fakeUserSession = object : UserSession { override val currentUserId = "test-uid" }
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(testDispatcher),
            produceFile = { tempFolder.newFile("test_prefs.preferences_pb") },
        )
        appDataStore = AppDataStore(dataStore)
        fakeLeaveBalanceDao = FakeLeaveBalanceDao()
        viewModel = OnboardingViewModel(appDataStore, fakeLeaveBalanceDao, fakeUserSession)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── selectCycleIndex ──────────────────────────────────────────────────────

    @Test
    fun `selectCycleIndex updates selectedCycleIndex`() {
        viewModel.selectCycleIndex(2)
        assertEquals(2, viewModel.uiState.value.selectedCycleIndex)
    }

    @Test
    fun `selectCycleIndex clears error`() {
        // Force an error first
        viewModel.nextStep() // no index selected → error
        assertNotNull(viewModel.uiState.value.error)
        viewModel.selectCycleIndex(0)
        assertNull(viewModel.uiState.value.error)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `selectCycleIndex rejects index 5`() {
        viewModel.selectCycleIndex(5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `selectCycleIndex rejects negative index`() {
        viewModel.selectCycleIndex(-1)
    }

    // ── setLeaveAllowanceDays ─────────────────────────────────────────────────

    @Test
    fun `setLeaveAllowanceDays updates value`() {
        viewModel.setLeaveAllowanceDays(30)
        assertEquals(30, viewModel.uiState.value.leaveAllowanceDays)
    }

    @Test
    fun `setLeaveAllowanceDays clamps to minimum of 1`() {
        viewModel.setLeaveAllowanceDays(0)
        assertEquals(1, viewModel.uiState.value.leaveAllowanceDays)
    }

    @Test
    fun `setLeaveAllowanceDays clamps to maximum of 365`() {
        viewModel.setLeaveAllowanceDays(400)
        assertEquals(365, viewModel.uiState.value.leaveAllowanceDays)
    }

    // ── shiftAnchorForward / shiftAnchorBack ──────────────────────────────────

    @Test
    fun `shiftAnchorForward adds one day`() {
        val before = viewModel.uiState.value.anchorDate
        viewModel.shiftAnchorForward()
        assertEquals(before.plusDays(1), viewModel.uiState.value.anchorDate)
    }

    @Test
    fun `shiftAnchorBack subtracts one day`() {
        val before = viewModel.uiState.value.anchorDate
        viewModel.shiftAnchorBack()
        assertEquals(before.minusDays(1), viewModel.uiState.value.anchorDate)
    }

    // ── nextStep without selection ────────────────────────────────────────────

    @Test
    fun `nextStep on SHIFT_PICKER without selection sets error and returns false`() {
        val advanced = viewModel.nextStep()
        assertFalse(advanced)
        assertEquals(OnboardingStep.SHIFT_PICKER, viewModel.uiState.value.step)
        assertNotNull(viewModel.uiState.value.error)
    }

    // ── nextStep with valid selection ─────────────────────────────────────────

    @Test
    fun `nextStep on SHIFT_PICKER with selection advances to LEAVE_SETUP`() {
        viewModel.selectCycleIndex(0)
        val advanced = viewModel.nextStep()
        assertTrue(advanced)
        assertEquals(OnboardingStep.LEAVE_SETUP, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `nextStep on LEAVE_SETUP advances to CONFIRM`() {
        viewModel.selectCycleIndex(1)
        viewModel.nextStep()
        val advanced = viewModel.nextStep()
        assertTrue(advanced)
        assertEquals(OnboardingStep.CONFIRM, viewModel.uiState.value.step)
    }

    @Test
    fun `nextStep on LEAVE_SETUP populates previewDays`() {
        viewModel.selectCycleIndex(0)
        viewModel.nextStep() // → LEAVE_SETUP
        viewModel.nextStep() // → CONFIRM
        assertEquals(7, viewModel.uiState.value.previewDays.size)
    }

    @Test
    fun `nextStep on CONFIRM returns false (use completeOnboarding instead)`() {
        viewModel.selectCycleIndex(0)
        viewModel.nextStep()
        viewModel.nextStep()
        assertEquals(OnboardingStep.CONFIRM, viewModel.uiState.value.step)
        val advanced = viewModel.nextStep()
        assertFalse(advanced)
        // Still on CONFIRM
        assertEquals(OnboardingStep.CONFIRM, viewModel.uiState.value.step)
    }

    // ── prevStep ──────────────────────────────────────────────────────────────

    @Test
    fun `prevStep from LEAVE_SETUP goes back to SHIFT_PICKER`() {
        viewModel.selectCycleIndex(0)
        viewModel.nextStep()
        viewModel.prevStep()
        assertEquals(OnboardingStep.SHIFT_PICKER, viewModel.uiState.value.step)
    }

    @Test
    fun `prevStep from CONFIRM goes back to LEAVE_SETUP`() {
        viewModel.selectCycleIndex(0)
        viewModel.nextStep()
        viewModel.nextStep()
        viewModel.prevStep()
        assertEquals(OnboardingStep.LEAVE_SETUP, viewModel.uiState.value.step)
    }

    @Test
    fun `prevStep on SHIFT_PICKER is a no-op`() {
        viewModel.prevStep()
        assertEquals(OnboardingStep.SHIFT_PICKER, viewModel.uiState.value.step)
    }

    // ── full wizard forward journey ───────────────────────────────────────────

    @Test
    fun `full wizard navigation sequence is SHIFT_PICKER → LEAVE_SETUP → CONFIRM`() {
        assertEquals(OnboardingStep.SHIFT_PICKER, viewModel.uiState.value.step)
        viewModel.selectCycleIndex(3)
        viewModel.nextStep()
        assertEquals(OnboardingStep.LEAVE_SETUP, viewModel.uiState.value.step)
        viewModel.setLeaveAllowanceDays(25)
        viewModel.nextStep()
        assertEquals(OnboardingStep.CONFIRM, viewModel.uiState.value.step)
        assertEquals(25, viewModel.uiState.value.leaveAllowanceDays)
        assertEquals(3, viewModel.uiState.value.selectedCycleIndex)
    }

    // ── completeOnboarding ────────────────────────────────────────────────────

    @Test
    fun `completeOnboarding saves anchor and leave balance to DataStore and DAO`() = testScope.runTest {
        viewModel.selectCycleIndex(2)
        viewModel.setLeaveAllowanceDays(30)
        viewModel.nextStep() // → LEAVE_SETUP
        viewModel.nextStep() // → CONFIRM

        var callbackCalled = false
        viewModel.completeOnboarding { callbackCalled = true }
        // Advance coroutines
        testScheduler.advanceUntilIdle()

        assertTrue(callbackCalled)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(viewModel.uiState.value.error)

        // DataStore should have the anchor and onboarding-complete flag
        val savedAnchorDate = appDataStore.anchorDate.first()
        val savedCycleIndex = appDataStore.anchorCycleIndex.first()
        val savedOnboarding = appDataStore.onboardingComplete.first()

        assertEquals(viewModel.uiState.value.anchorDate.toString(), savedAnchorDate)
        assertEquals(2, savedCycleIndex)
        assertTrue(savedOnboarding)

        // Leave balance DAO should have one entry
        assertEquals(1, fakeLeaveBalanceDao.upserted.size)
        assertEquals(30f, fakeLeaveBalanceDao.upserted[0].totalDays)
        assertEquals(LocalDate.now().year, fakeLeaveBalanceDao.upserted[0].year)
    }

    @Test
    fun `completeOnboarding is no-op when cycle index not selected`() = testScope.runTest {
        viewModel.completeOnboarding { }
        testScheduler.advanceUntilIdle()
        assertEquals(0, fakeLeaveBalanceDao.upserted.size)
    }
}

// ── Fake collaborators ────────────────────────────────────────────────────────

private class FakeLeaveBalanceDao : LeaveBalanceDao {
    val upserted = mutableListOf<LeaveBalanceEntity>()
    override suspend fun getBalanceForYear(userId: String, year: Int) = null
    override fun observeBalanceForYear(userId: String, year: Int) = kotlinx.coroutines.flow.flowOf(null)
    override suspend fun upsert(balance: LeaveBalanceEntity): Long {
        upserted.add(balance)
        return upserted.size.toLong()
    }
    override suspend fun update(balance: LeaveBalanceEntity) {}
    override suspend fun delete(balance: LeaveBalanceEntity) {}
    override suspend fun deleteAllForUser(userId: String) {}
}
