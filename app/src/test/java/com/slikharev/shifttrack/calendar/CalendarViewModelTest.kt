package com.slikharev.shifttrack.calendar

import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.repository.ShiftRepository
import com.slikharev.shifttrack.data.repository.SpectatorRepository
import com.slikharev.shifttrack.model.DayInfo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import java.time.LocalDate
import java.time.YearMonth

/**
 * Unit tests for [CalendarViewModel].
 *
 * We use MockK to stub [ShiftRepository.getDayInfosForRange] so the ViewModel
 * can be instantiated without Room / DataStore dependencies.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var shiftRepository: ShiftRepository
    private lateinit var spectatorRepository: SpectatorRepository
    private lateinit var appDataStore: AppDataStore
    private lateinit var viewModel: CalendarViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        shiftRepository = mockk()
        spectatorRepository = mockk()
        appDataStore = mockk(relaxed = true) {
            every { spectatorMode } returns flowOf(false)
            every { watchedHosts } returns flowOf(emptyList())
            every { selectedHostUid } returns flowOf(null)
        }
        // Return an empty list for any date range — we are not testing calendarDays content here.
        every { shiftRepository.getDayInfosForRange(any(), any()) } returns flowOf(emptyList<DayInfo>())
        viewModel = CalendarViewModel(shiftRepository, spectatorRepository, appDataStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── Initial state ────────────────────────────────────────────────────────────

    @Test
    fun `initial currentYearMonth is the current month`() {
        assertEquals(YearMonth.now(), viewModel.currentYearMonth.value)
    }

    @Test
    fun `initial calendarDays has size that is multiple of 7`() = runTest {
        advanceUntilIdle()
        val days = viewModel.calendarDays.value
        assertTrue(
            "Expected multiple of 7 but was ${days.size}",
            days.size % 7 == 0,
        )
    }

    // ─── Month navigation ─────────────────────────────────────────────────────────

    @Test
    fun `prevMonth decrements currentYearMonth by one month`() {
        val initial = viewModel.currentYearMonth.value
        viewModel.prevMonth()
        assertEquals(initial.minusMonths(1), viewModel.currentYearMonth.value)
    }

    @Test
    fun `nextMonth increments currentYearMonth by one month`() {
        val initial = viewModel.currentYearMonth.value
        viewModel.nextMonth()
        assertEquals(initial.plusMonths(1), viewModel.currentYearMonth.value)
    }

    @Test
    fun `prevMonth then nextMonth returns to original month`() {
        val initial = viewModel.currentYearMonth.value
        viewModel.prevMonth()
        viewModel.nextMonth()
        assertEquals(initial, viewModel.currentYearMonth.value)
    }

    @Test
    fun `multiple prevMonth calls accumulate correctly`() {
        val initial = viewModel.currentYearMonth.value
        repeat(5) { viewModel.prevMonth() }
        assertEquals(initial.minusMonths(5), viewModel.currentYearMonth.value)
    }

    @Test
    fun `multiple nextMonth calls accumulate correctly`() {
        val initial = viewModel.currentYearMonth.value
        repeat(13) { viewModel.nextMonth() }
        assertEquals(initial.plusMonths(13), viewModel.currentYearMonth.value)
    }

    @Test
    fun `navigating across year boundary works correctly`() {
        // Set to January by navigating back far enough, then go back one more
        val jan = YearMonth.of(LocalDate.now().year, 1)
        val diff = YearMonth.now().until(jan, java.time.temporal.ChronoUnit.MONTHS).toInt()
        repeat(-diff) { viewModel.prevMonth() }
        assertEquals(jan, viewModel.currentYearMonth.value)

        viewModel.prevMonth()
        assertEquals(YearMonth.of(jan.year - 1, 12), viewModel.currentYearMonth.value)
    }

    // ─── CalendarDays reflect current month ───────────────────────────────────────

    @Test
    fun `calendarDays updates after nextMonth`() = runTest {
        advanceUntilIdle()
        val beforeSize = viewModel.calendarDays.value.size

        viewModel.nextMonth()
        advanceUntilIdle()

        // Both months should produce valid grids
        assertTrue(beforeSize % 7 == 0)
        assertTrue(viewModel.calendarDays.value.size % 7 == 0)
    }

    @Test
    fun `calendarDays for given month has correct ShiftDay count`() = runTest {
        // Navigate to a known month: March 2024 (31 days)
        val targetYm = YearMonth.of(2024, 3)
        val current = viewModel.currentYearMonth.value
        val monthsToNavigate = current.until(targetYm, java.time.temporal.ChronoUnit.MONTHS).toInt()
        if (monthsToNavigate >= 0) {
            repeat(monthsToNavigate) { viewModel.nextMonth() }
        } else {
            repeat(-monthsToNavigate) { viewModel.prevMonth() }
        }
        advanceUntilIdle()
        assertEquals(targetYm, viewModel.currentYearMonth.value)

        val shiftDays = viewModel.calendarDays.value.filterIsInstance<CalendarDay.ShiftDay>()
        assertEquals(31, shiftDays.size)
    }
}
