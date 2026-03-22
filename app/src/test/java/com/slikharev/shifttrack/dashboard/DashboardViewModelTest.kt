package com.slikharev.shifttrack.dashboard

import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.OvertimeBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.OvertimeEntity
import com.slikharev.shifttrack.data.repository.LeaveRepository
import com.slikharev.shifttrack.data.repository.OvertimeRepository
import com.slikharev.shifttrack.data.repository.ShiftRepository
import com.slikharev.shifttrack.model.DayInfo
import com.slikharev.shifttrack.model.ShiftType
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
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var mockShiftRepository: ShiftRepository
    private lateinit var mockLeaveRepository: LeaveRepository
    private lateinit var mockOvertimeRepository: OvertimeRepository
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockShiftRepository = mockk(relaxed = true)
        mockLeaveRepository = mockk(relaxed = true)
        mockOvertimeRepository = mockk(relaxed = true)

        every { mockShiftRepository.getDayInfosForRange(any(), any()) } returns flowOf(emptyList())
        every { mockLeaveRepository.observeBalanceForYear(any()) } returns
            flowOf<LeaveBalanceEntity?>(null)
        every { mockOvertimeRepository.getOvertimeForRange(any(), any()) } returns flowOf(emptyList())
        every { mockOvertimeRepository.observeBalanceForYear(any()) } returns
            flowOf<OvertimeBalanceEntity?>(null)

        viewModel = DashboardViewModel(mockShiftRepository, mockLeaveRepository, mockOvertimeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── upcomingDays ─────────────────────────────────────────────────────────

    @Test
    fun `upcomingDays is empty when repository emits empty list`() = testScope.runTest {
        val job = launch { viewModel.upcomingDays.collect { } }
        advanceUntilIdle()
        assertTrue(viewModel.upcomingDays.value.isEmpty())
        job.cancel()
    }

    @Test
    fun `upcomingDays marks first entry as isToday true`() = testScope.runTest {
        val today = LocalDate.now()
        val infos = listOf(
            DayInfo(date = today, shiftType = ShiftType.DAY),
            DayInfo(date = today.plusDays(1), shiftType = ShiftType.NIGHT),
        )
        every { mockShiftRepository.getDayInfosForRange(any(), any()) } returns flowOf(infos)
        viewModel = DashboardViewModel(mockShiftRepository, mockLeaveRepository, mockOvertimeRepository)

        val job = launch { viewModel.upcomingDays.collect { } }
        advanceUntilIdle()

        val days = viewModel.upcomingDays.value
        assertTrue("First entry should be isToday=true", days[0].isToday)
        assertFalse("Second entry should be isToday=false", days[1].isToday)
        job.cancel()
    }

    @Test
    fun `upcomingDays returns 7 entries when repository emits 7 DayInfos`() = testScope.runTest {
        val today = LocalDate.now()
        val infos = (0L..6L).map { DayInfo(date = today.plusDays(it), shiftType = ShiftType.DAY) }
        every { mockShiftRepository.getDayInfosForRange(any(), any()) } returns flowOf(infos)
        viewModel = DashboardViewModel(mockShiftRepository, mockLeaveRepository, mockOvertimeRepository)

        val job = launch { viewModel.upcomingDays.collect { } }
        advanceUntilIdle()

        assertEquals(7, viewModel.upcomingDays.value.size)
        job.cancel()
    }

    // ── leaveBalance ─────────────────────────────────────────────────────────

    @Test
    fun `leaveBalance is null when repository emits null`() = testScope.runTest {
        val job = launch { viewModel.leaveBalance.collect { } }
        advanceUntilIdle()
        assertNull(viewModel.leaveBalance.value)
        job.cancel()
    }

    @Test
    fun `leaveBalance reflects entity emitted by repository`() = testScope.runTest {
        val balance = LeaveBalanceEntity(year = 2025, totalDays = 20f, usedDays = 5f, userId = "u1")
        every { mockLeaveRepository.observeBalanceForYear(any()) } returns flowOf(balance)
        viewModel = DashboardViewModel(mockShiftRepository, mockLeaveRepository, mockOvertimeRepository)

        val job = launch { viewModel.leaveBalance.collect { } }
        advanceUntilIdle()

        assertEquals(balance, viewModel.leaveBalance.value)
        job.cancel()
    }

    // ── remainingLeaveDays ───────────────────────────────────────────────────

    @Test
    fun `remainingLeaveDays is null when leaveBalance is null`() = testScope.runTest {
        val job = launch { viewModel.remainingLeaveDays.collect { } }
        advanceUntilIdle()
        assertNull(viewModel.remainingLeaveDays.value)
        job.cancel()
    }

    @Test
    fun `remainingLeaveDays computes totalDays minus usedDays`() = testScope.runTest {
        val balance = LeaveBalanceEntity(year = 2025, totalDays = 20f, usedDays = 5f, userId = "u1")
        every { mockLeaveRepository.observeBalanceForYear(any()) } returns flowOf(balance)
        viewModel = DashboardViewModel(mockShiftRepository, mockLeaveRepository, mockOvertimeRepository)

        val job = launch { viewModel.remainingLeaveDays.collect { } }
        advanceUntilIdle()

        assertEquals(15f, viewModel.remainingLeaveDays.value)
        job.cancel()
    }

    @Test
    fun `remainingLeaveDays is clamped to zero when usedDays exceeds totalDays`() = testScope.runTest {
        val balance = LeaveBalanceEntity(year = 2025, totalDays = 5f, usedDays = 10f, userId = "u1")
        every { mockLeaveRepository.observeBalanceForYear(any()) } returns flowOf(balance)
        viewModel = DashboardViewModel(mockShiftRepository, mockLeaveRepository, mockOvertimeRepository)

        val job = launch { viewModel.remainingLeaveDays.collect { } }
        advanceUntilIdle()

        assertEquals(0f, viewModel.remainingLeaveDays.value)
        job.cancel()
    }

    // ── weeklyOvertimeHours ──────────────────────────────────────────────────

    @Test
    fun `weeklyOvertimeHours is zero when repository emits empty list`() = testScope.runTest {
        val job = launch { viewModel.weeklyOvertimeHours.collect { } }
        advanceUntilIdle()
        assertEquals(0f, viewModel.weeklyOvertimeHours.value)
        job.cancel()
    }

    @Test
    fun `weeklyOvertimeHours sums all entry hours`() = testScope.runTest {
        val today = LocalDate.now()
        val entries = listOf(
            OvertimeEntity(date = today.toString(), hours = 2.5f, userId = "u1"),
            OvertimeEntity(date = today.plusDays(1).toString(), hours = 1.5f, userId = "u1"),
        )
        every { mockOvertimeRepository.getOvertimeForRange(any(), any()) } returns flowOf(entries)
        viewModel = DashboardViewModel(mockShiftRepository, mockLeaveRepository, mockOvertimeRepository)

        val job = launch { viewModel.weeklyOvertimeHours.collect { } }
        advanceUntilIdle()

        assertEquals(4.0f, viewModel.weeklyOvertimeHours.value)
        job.cancel()
    }

    // ── yearlyOvertimeBalance ────────────────────────────────────────────────

    @Test
    fun `yearlyOvertimeBalance reflects entity emitted by repository`() = testScope.runTest {
        val balance = OvertimeBalanceEntity(year = 2025, totalHours = 12f, userId = "u1")
        every { mockOvertimeRepository.observeBalanceForYear(any()) } returns flowOf(balance)
        viewModel = DashboardViewModel(mockShiftRepository, mockLeaveRepository, mockOvertimeRepository)

        val job = launch { viewModel.yearlyOvertimeBalance.collect { } }
        advanceUntilIdle()

        assertEquals(balance, viewModel.yearlyOvertimeBalance.value)
        job.cancel()
    }
}
