package com.slikharev.shifttrack.dashboard

import com.slikharev.shifttrack.data.local.AppDataStore
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
    private lateinit var mockAppDataStore: AppDataStore
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockShiftRepository = mockk(relaxed = true)
        mockLeaveRepository = mockk(relaxed = true)
        mockOvertimeRepository = mockk(relaxed = true)
        mockAppDataStore = mockk(relaxed = true) {
            every { spectatorMode } returns flowOf(false)
        }

        every { mockShiftRepository.getDayInfosForRange(any(), any()) } returns flowOf(emptyList())
        every { mockLeaveRepository.observeAllBalancesForYear(any()) } returns
            flowOf(emptyList<LeaveBalanceEntity>())
        every { mockOvertimeRepository.getOvertimeForRange(any(), any()) } returns flowOf(emptyList())
        every { mockOvertimeRepository.observeBalanceForYear(any()) } returns
            flowOf<OvertimeBalanceEntity?>(null)

        viewModel = DashboardViewModel(mockShiftRepository, mockLeaveRepository, mockOvertimeRepository, mockAppDataStore)
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
        viewModel = DashboardViewModel(mockShiftRepository, mockLeaveRepository, mockOvertimeRepository, mockAppDataStore)

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
        viewModel = DashboardViewModel(mockShiftRepository, mockLeaveRepository, mockOvertimeRepository, mockAppDataStore)

        val job = launch { viewModel.upcomingDays.collect { } }
        advanceUntilIdle()

        assertEquals(7, viewModel.upcomingDays.value.size)
        job.cancel()
    }

    // ── leaveBalances ─────────────────────────────────────────────────────────

    @Test
    fun `leaveBalances is empty when repository emits empty list`() = testScope.runTest {
        val job = launch { viewModel.leaveBalances.collect { } }
        advanceUntilIdle()
        assertTrue(viewModel.leaveBalances.value.isEmpty())
        job.cancel()
    }

    @Test
    fun `leaveBalances reflects entities emitted by repository`() = testScope.runTest {
        val balances = listOf(
            LeaveBalanceEntity(year = 2025, leaveType = "ANNUAL", totalDays = 20f, usedDays = 5f, userId = "u1"),
            LeaveBalanceEntity(year = 2025, leaveType = "SICK", totalDays = 10f, usedDays = 2f, userId = "u1"),
        )
        every { mockLeaveRepository.observeAllBalancesForYear(any()) } returns flowOf(balances)
        viewModel = DashboardViewModel(mockShiftRepository, mockLeaveRepository, mockOvertimeRepository, mockAppDataStore)

        val job = launch { viewModel.leaveBalances.collect { } }
        advanceUntilIdle()

        assertEquals(balances, viewModel.leaveBalances.value)
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
        viewModel = DashboardViewModel(mockShiftRepository, mockLeaveRepository, mockOvertimeRepository, mockAppDataStore)

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
        viewModel = DashboardViewModel(mockShiftRepository, mockLeaveRepository, mockOvertimeRepository, mockAppDataStore)

        val job = launch { viewModel.yearlyOvertimeBalance.collect { } }
        advanceUntilIdle()

        assertEquals(balance, viewModel.yearlyOvertimeBalance.value)
        job.cancel()
    }
}
