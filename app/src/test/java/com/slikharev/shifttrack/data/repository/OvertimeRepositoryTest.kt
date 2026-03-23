package com.slikharev.shifttrack.data.repository

import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.data.local.db.ShiftTrackDatabase
import com.slikharev.shifttrack.data.local.db.dao.OvertimeBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.OvertimeDao
import com.slikharev.shifttrack.data.local.db.entity.OvertimeBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.OvertimeEntity
import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for [OvertimeRepository].
 *
 * Uses in-memory fake DAOs — no Android framework dependency needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OvertimeRepositoryTest {

    // ─── Fake DAOs ────────────────────────────────────────────────────────────────

    private class FakeOvertimeDao : OvertimeDao {
        private val store = mutableMapOf<String, OvertimeEntity>() // key = date

        override suspend fun getOvertimeForDate(userId: String, date: String): OvertimeEntity? =
            store.values.firstOrNull { it.userId == userId && it.date == date }

        override fun getOvertimeForRange(
            userId: String,
            startDate: String,
            endDate: String,
        ): Flow<List<OvertimeEntity>> = MutableStateFlow(
            store.values.filter { it.userId == userId && it.date in startDate..endDate }
                .sortedBy { it.date },
        )

        override fun sumOvertimeHoursForYear(
            userId: String,
            startDate: String,
            endDate: String,
        ): Flow<Float> = MutableStateFlow(
            store.values
                .filter { it.userId == userId && it.date in startDate..endDate }
                .sumOf { it.hours.toDouble() }.toFloat(),
        )

        override suspend fun sumOvertimeHoursOnce(
            userId: String,
            startDate: String,
            endDate: String,
        ): Float = store.values
            .filter { it.userId == userId && it.date in startDate..endDate }
            .sumOf { it.hours.toDouble() }.toFloat()

        override suspend fun upsert(overtime: OvertimeEntity): Long {
            store[overtime.date] = overtime.copy(id = store.size.toLong() + 1)
            return store[overtime.date]!!.id
        }

        override suspend fun delete(overtime: OvertimeEntity) {
            store.remove(overtime.date)
        }

        override suspend fun deleteByDate(userId: String, date: String) {
            store.values.removeIf { it.userId == userId && it.date == date }
        }

        override suspend fun getUnsynced(userId: String): List<OvertimeEntity> =
            store.values.filter { it.userId == userId && !it.synced }

        override suspend fun markSynced(ids: List<Long>) {
            store.values
                .filter { it.id in ids }
                .forEach { entry -> store[entry.date] = entry.copy(synced = true) }
        }

        override suspend fun deleteAllForUser(userId: String) {
            store.entries.removeIf { it.value.userId == userId }
        }
    }

    private class FakeOvertimeBalanceDao : OvertimeBalanceDao {
        private val _flow = MutableStateFlow<OvertimeBalanceEntity?>(null)
        private var stored: OvertimeBalanceEntity? = null

        override suspend fun getBalanceForYear(userId: String, year: Int): OvertimeBalanceEntity? =
            stored?.takeIf { it.userId == userId && it.year == year }

        override fun observeBalanceForYear(userId: String, year: Int): Flow<OvertimeBalanceEntity?> =
            _flow.map { it?.takeIf { b -> b.userId == userId && b.year == year } }

        override suspend fun upsert(balance: OvertimeBalanceEntity): Long {
            stored = balance.copy(id = 1L)
            _flow.value = stored
            return 1L
        }

        override suspend fun update(balance: OvertimeBalanceEntity) {
            stored = balance
            _flow.value = balance
        }

        override suspend fun delete(balance: OvertimeBalanceEntity) {
            if (stored?.id == balance.id) {
                stored = null
                _flow.value = null
            }
        }

        override suspend fun deleteAllForUser(userId: String) {
            if (stored?.userId == userId) { stored = null; _flow.value = null }
        }
    }

    // ─── Test setup ───────────────────────────────────────────────────────────────

    private val fakeUserSession = object : UserSession {
        override val currentUserId = "test-uid"
    }
    private lateinit var mockDb: ShiftTrackDatabase
    private lateinit var overtimeDao: FakeOvertimeDao
    private lateinit var overtimeBalanceDao: FakeOvertimeBalanceDao
    private lateinit var repository: OvertimeRepository

    private val today = LocalDate.of(2025, 6, 15)

    @Before
    fun setUp() {
        overtimeDao = FakeOvertimeDao()
        overtimeBalanceDao = FakeOvertimeBalanceDao()
        mockDb = mockk(relaxed = true)
        mockkStatic("androidx.room.RoomDatabaseKt")
        val transactionLambda = slot<suspend () -> Any?>()
        coEvery { mockDb.withTransaction(capture(transactionLambda)) } coAnswers {
            transactionLambda.captured.invoke()
        }
        repository = OvertimeRepository(mockDb, overtimeDao, overtimeBalanceDao, fakeUserSession)
    }

    // ─── addOvertime ─────────────────────────────────────────────────────────────

    @Test
    fun `addOvertime stores entry in DAO`() = runTest {
        repository.addOvertime(today, 2.5f)
        val entry = overtimeDao.getOvertimeForDate("test-uid", today.toString())
        assertNotNull(entry)
        assertEquals(2.5f, entry!!.hours)
    }

    @Test
    fun `addOvertime creates OvertimeBalanceEntity when none exists`() = runTest {
        repository.addOvertime(today, 3f)
        val balance = overtimeBalanceDao.getBalanceForYear("test-uid", today.year)
        assertNotNull(balance)
        assertEquals(3f, balance!!.totalHours)
    }

    @Test
    fun `addOvertime accumulates totalHours across multiple dates`() = runTest {
        repository.addOvertime(today, 2f)
        repository.addOvertime(today.plusDays(1), 1.5f)
        val balance = overtimeBalanceDao.getBalanceForYear("test-uid", today.year)
        assertEquals(3.5f, balance!!.totalHours)
    }

    @Test
    fun `addOvertime replaces existing entry for same date`() = runTest {
        repository.addOvertime(today, 1f)
        repository.addOvertime(today, 3f) // replace
        val balance = overtimeBalanceDao.getBalanceForYear("test-uid", today.year)
        assertEquals(3f, balance!!.totalHours)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `addOvertime throws on zero hours`() = runTest {
        repository.addOvertime(today, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `addOvertime throws on negative hours`() = runTest {
        repository.addOvertime(today, -1f)
    }

    // ─── removeOvertime ───────────────────────────────────────────────────────────

    @Test
    fun `removeOvertime deletes entry from DAO`() = runTest {
        repository.addOvertime(today, 2f)
        repository.removeOvertime(today)
        val entry = overtimeDao.getOvertimeForDate("test-uid", today.toString())
        assertNull(entry)
    }

    @Test
    fun `removeOvertime recalculates totalHours`() = runTest {
        repository.addOvertime(today, 2f)
        repository.addOvertime(today.plusDays(1), 3f)
        // Remove first entry
        repository.removeOvertime(today)
        val balance = overtimeBalanceDao.getBalanceForYear("test-uid", today.year)
        assertEquals(3f, balance!!.totalHours)
    }

    @Test
    fun `removeOvertime on non-existent date does not throw`() = runTest {
        repository.removeOvertime(today) // nothing stored — should be silent
    }

    // ─── getOvertimeForDate ───────────────────────────────────────────────────────

    @Test
    fun `getOvertimeForDate returns null when nothing stored`() = runTest {
        val entry = repository.getOvertimeForDate(today)
        assertNull(entry)
    }

    @Test
    fun `getOvertimeForDate returns correct entry`() = runTest {
        repository.addOvertime(today, 1.5f, note = "test note")
        val entry = repository.getOvertimeForDate(today)
        assertNotNull(entry)
        assertEquals("test note", entry!!.note)
    }

    // ─── observeBalanceForYear ────────────────────────────────────────────────────

    @Test
    fun `observeBalanceForYear emits null before any overtime recorded`() = runTest {
        val balance = repository.observeBalanceForYear(today.year).first()
        assertNull(balance)
    }

    @Test
    fun `observeBalanceForYear emits updated balance after addOvertime`() = runTest {
        repository.addOvertime(today, 4f)
        val balance = repository.observeBalanceForYear(today.year).first()
        assertNotNull(balance)
        assertEquals(4f, balance!!.totalHours)
    }

    // ─── getOvertimeForRange ──────────────────────────────────────────────────────

    @Test
    fun `getOvertimeForRange returns only entries within range`() = runTest {
        repository.addOvertime(today, 1f)
        repository.addOvertime(today.plusDays(1), 2f)
        repository.addOvertime(today.plusDays(10), 3f) // outside range
        val entries = repository
            .getOvertimeForRange(today, today.plusDays(2))
            .first()
        assertEquals(2, entries.size)
    }

    @Test
    fun `getOvertimeForRange returns empty list when none in range`() = runTest {
        repository.addOvertime(today.plusMonths(1), 1f)
        val entries = repository
            .getOvertimeForRange(today, today.plusDays(6))
            .first()
        assertEquals(0, entries.size)
    }
}
