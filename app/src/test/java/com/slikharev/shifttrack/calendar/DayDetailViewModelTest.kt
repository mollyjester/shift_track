package com.slikharev.shifttrack.calendar

import androidx.lifecycle.SavedStateHandle
import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.local.db.entity.OvertimeEntity
import com.slikharev.shifttrack.data.repository.LeaveRepository
import com.slikharev.shifttrack.data.repository.OvertimeRepository
import com.slikharev.shifttrack.data.local.AttachmentFileManager
import com.slikharev.shifttrack.data.repository.AttachmentRepository
import com.slikharev.shifttrack.data.repository.ShiftRepository
import com.slikharev.shifttrack.data.repository.SpectatorRepository
import com.slikharev.shifttrack.data.repository.StorageMonitor
import com.slikharev.shifttrack.sync.StorageWarningNotifier
import com.slikharev.shifttrack.model.DayInfo
import com.slikharev.shifttrack.model.LeaveType
import com.slikharev.shifttrack.model.ShiftType
import com.slikharev.shifttrack.widget.ShiftWidgetUpdater
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DayDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val testDate = LocalDate.of(2025, 6, 15)

    private lateinit var mockShiftRepository: ShiftRepository
    private lateinit var mockLeaveRepository: LeaveRepository
    private lateinit var mockOvertimeRepository: OvertimeRepository
    private lateinit var mockSpectatorRepository: SpectatorRepository
    private lateinit var mockUserSession: UserSession
    private lateinit var mockWidgetUpdater: ShiftWidgetUpdater
    private lateinit var mockAttachmentRepository: AttachmentRepository
    private lateinit var mockAttachmentFileManager: AttachmentFileManager
    private lateinit var mockStorageMonitor: StorageMonitor
    private lateinit var mockStorageWarningNotifier: StorageWarningNotifier
    private lateinit var mockAppDataStore: AppDataStore
    private lateinit var viewModel: DayDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockShiftRepository = mockk(relaxed = true)
        mockLeaveRepository = mockk(relaxed = true)
        mockOvertimeRepository = mockk(relaxed = true)
        mockSpectatorRepository = mockk(relaxed = true)
        mockUserSession = mockk {
            every { currentUserId } returns "test-uid"
        }
        mockWidgetUpdater = mockk(relaxed = true)
        mockAttachmentRepository = mockk(relaxed = true)
        mockAttachmentFileManager = mockk(relaxed = true)
        mockStorageMonitor = mockk(relaxed = true)
        mockStorageWarningNotifier = mockk(relaxed = true)
        mockAppDataStore = mockk {
            every { spectatorMode } returns flowOf(false)
            every { selectedHostUid } returns flowOf(null)
        }

        every { mockShiftRepository.getDayInfosForRange(any(), any()) } returns flowOf(emptyList())
        every { mockOvertimeRepository.getOvertimeForRange(any(), any()) } returns
            flowOf<List<OvertimeEntity>>(emptyList())

        viewModel = DayDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("date" to "2025-06-15")),
            shiftRepository = mockShiftRepository,
            leaveRepository = mockLeaveRepository,
            overtimeRepository = mockOvertimeRepository,
            spectatorRepository = mockSpectatorRepository,
            userSession = mockUserSession,
            widgetUpdater = mockWidgetUpdater,
            attachmentRepository = mockAttachmentRepository,
            attachmentFileManager = mockAttachmentFileManager,
            storageMonitor = mockStorageMonitor,
            storageWarningNotifier = mockStorageWarningNotifier,
            appDataStore = mockAppDataStore,
            workedHoursOverrideDao = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── initialization ───────────────────────────────────────────────────────

    @Test
    fun `date is parsed correctly from SavedStateHandle`() {
        assertEquals(testDate, viewModel.date)
    }

    @Test
    fun `dayInfo is null when repository emits empty list`() = testScope.runTest {
        val job = launch { viewModel.dayInfo.collect { } }
        advanceUntilIdle()
        assertNull(viewModel.dayInfo.value)
        job.cancel()
    }

    @Test
    fun `dayInfo emits first entry from repository`() = testScope.runTest {
        val info = DayInfo(date = testDate, shiftType = ShiftType.DAY)
        every { mockShiftRepository.getDayInfosForRange(any(), any()) } returns flowOf(listOf(info))
        viewModel = DayDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("date" to "2025-06-15")),
            shiftRepository = mockShiftRepository,
            leaveRepository = mockLeaveRepository,
            overtimeRepository = mockOvertimeRepository,
            spectatorRepository = mockSpectatorRepository,
            userSession = mockUserSession,
            widgetUpdater = mockWidgetUpdater,
            attachmentRepository = mockAttachmentRepository,
            attachmentFileManager = mockAttachmentFileManager,
            storageMonitor = mockStorageMonitor,
            storageWarningNotifier = mockStorageWarningNotifier,
            appDataStore = mockAppDataStore,
            workedHoursOverrideDao = mockk(relaxed = true),
        )

        val job = launch { viewModel.dayInfo.collect { } }
        advanceUntilIdle()

        assertEquals(info, viewModel.dayInfo.value)
        job.cancel()
    }

    @Test
    fun `overtimeEntry is null when repository emits empty list`() = testScope.runTest {
        val job = launch { viewModel.overtimeEntry.collect { } }
        advanceUntilIdle()
        assertNull(viewModel.overtimeEntry.value)
        job.cancel()
    }

    @Test
    fun `overtimeEntry emits first element from repository`() = testScope.runTest {
        val entry = OvertimeEntity(date = testDate.toString(), hours = 3f, userId = "test-uid")
        every { mockOvertimeRepository.getOvertimeForRange(any(), any()) } returns flowOf(listOf(entry))
        viewModel = DayDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("date" to "2025-06-15")),
            shiftRepository = mockShiftRepository,
            leaveRepository = mockLeaveRepository,
            overtimeRepository = mockOvertimeRepository,
            spectatorRepository = mockSpectatorRepository,
            userSession = mockUserSession,
            widgetUpdater = mockWidgetUpdater,
            attachmentRepository = mockAttachmentRepository,
            attachmentFileManager = mockAttachmentFileManager,
            storageMonitor = mockStorageMonitor,
            storageWarningNotifier = mockStorageWarningNotifier,
            appDataStore = mockAppDataStore,
            workedHoursOverrideDao = mockk(relaxed = true),
        )

        val job = launch { viewModel.overtimeEntry.collect { } }
        advanceUntilIdle()

        assertEquals(entry, viewModel.overtimeEntry.value)
        job.cancel()
    }

    // ── setManualOverride ────────────────────────────────────────────────────

    @Test
    fun `setManualOverride calls shiftRepository with correct arguments`() = testScope.runTest {
        viewModel.setManualOverride(ShiftType.NIGHT)
        advanceUntilIdle()

        coVerify { mockShiftRepository.setManualOverride("test-uid", testDate, ShiftType.NIGHT) }
        assertFalse(viewModel.isSaving.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `setManualOverride sets error message on exception`() = testScope.runTest {
        coEvery {
            mockShiftRepository.setManualOverride(any(), any(), any())
        } throws Exception("DB write failed")

        viewModel.setManualOverride(ShiftType.DAY)
        advanceUntilIdle()

        assertTrue(viewModel.error.value?.contains("DB write failed") == true)
        assertFalse(viewModel.isSaving.value)
    }

    // ── clearManualOverride ──────────────────────────────────────────────────

    @Test
    fun `clearManualOverride calls shiftRepository with userId and date`() = testScope.runTest {
        viewModel.clearManualOverride()
        advanceUntilIdle()

        coVerify { mockShiftRepository.clearManualOverride("test-uid", testDate) }
        assertNull(viewModel.error.value)
    }

    // ── addLeave ─────────────────────────────────────────────────────────────

    @Test
    fun `addLeave calls leaveRepository with correct arguments`() = testScope.runTest {
        viewModel.addLeave(LeaveType.ANNUAL, halfDay = false, note = "public holiday")
        advanceUntilIdle()

        coVerify { mockLeaveRepository.addLeave(testDate, LeaveType.ANNUAL, false, "public holiday") }
        assertNull(viewModel.error.value)
    }

    @Test
    fun `addLeave sets error message on exception`() = testScope.runTest {
        coEvery {
            mockLeaveRepository.addLeave(any(), any(), any(), any())
        } throws Exception("network timeout")

        viewModel.addLeave(LeaveType.SICK, halfDay = false, note = null)
        advanceUntilIdle()

        assertTrue(viewModel.error.value?.contains("network timeout") == true)
        assertFalse(viewModel.isSaving.value)
    }

    // ── removeLeave ──────────────────────────────────────────────────────────

    @Test
    fun `removeLeave calls leaveRepository with correct date`() = testScope.runTest {
        viewModel.removeLeave()
        advanceUntilIdle()

        coVerify { mockLeaveRepository.removeLeave(testDate) }
        assertNull(viewModel.error.value)
    }

    // ── addOvertime ──────────────────────────────────────────────────────────

    @Test
    fun `addOvertime calls overtimeRepository with correct arguments`() = testScope.runTest {
        viewModel.addOvertime(2.5f, note = "extra shift")
        advanceUntilIdle()

        coVerify { mockOvertimeRepository.addOvertime(testDate, 2.5f, "extra shift") }
        assertNull(viewModel.error.value)
    }

    @Test
    fun `addOvertime with null note passes null to repository`() = testScope.runTest {
        viewModel.addOvertime(1.0f)
        advanceUntilIdle()

        coVerify { mockOvertimeRepository.addOvertime(testDate, 1.0f, null) }
    }

    // ── removeOvertime ───────────────────────────────────────────────────────

    @Test
    fun `removeOvertime calls overtimeRepository with correct date`() = testScope.runTest {
        viewModel.removeOvertime()
        advanceUntilIdle()

        coVerify { mockOvertimeRepository.removeOvertime(testDate) }
        assertNull(viewModel.error.value)
    }

    // ── isSaving gate ────────────────────────────────────────────────────────

    @Test
    fun `isSaving is false after action completes successfully`() = testScope.runTest {
        viewModel.setManualOverride(ShiftType.REST)
        advanceUntilIdle()

        assertFalse(viewModel.isSaving.value)
    }

    @Test
    fun `isSaving is false after action fails`() = testScope.runTest {
        coEvery {
            mockShiftRepository.setManualOverride(any(), any(), any())
        } throws Exception("timeout")

        viewModel.setManualOverride(ShiftType.OFF)
        advanceUntilIdle()

        assertFalse(viewModel.isSaving.value)
    }

    // ── spectator mode ───────────────────────────────────────────────────────

    @Test
    fun `isSpectator is false by default`() = testScope.runTest {
        val job = launch { viewModel.isSpectator.collect { } }
        advanceUntilIdle()
        assertFalse(viewModel.isSpectator.value)
        job.cancel()
    }

    @Test
    fun `isSpectator reflects AppDataStore spectatorMode`() = testScope.runTest {
        val spectatorDataStore = mockk<AppDataStore> {
            every { spectatorMode } returns flowOf(true)
            every { selectedHostUid } returns flowOf(null)
        }
        val vm = DayDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("date" to "2025-06-15")),
            shiftRepository = mockShiftRepository,
            leaveRepository = mockLeaveRepository,
            overtimeRepository = mockOvertimeRepository,
            spectatorRepository = mockSpectatorRepository,
            userSession = mockUserSession,
            widgetUpdater = mockWidgetUpdater,
            attachmentRepository = mockAttachmentRepository,
            attachmentFileManager = mockAttachmentFileManager,
            storageMonitor = mockStorageMonitor,
            storageWarningNotifier = mockStorageWarningNotifier,
            appDataStore = spectatorDataStore,
            workedHoursOverrideDao = mockk(relaxed = true),
        )
        val job = launch { vm.isSpectator.collect { } }
        advanceUntilIdle()
        assertTrue(vm.isSpectator.value)
        job.cancel()
    }
}
