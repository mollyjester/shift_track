package com.slikharev.shifttrack.data.repository

import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.data.local.db.dao.LeaveBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.LeaveDao
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.LeaveEntity
import com.slikharev.shifttrack.model.LeaveType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for [LeaveRepository].
 *
 * Uses in-memory fake DAOs — no Android framework dependency needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LeaveRepositoryTest {

    private val uid = "test-uid"
    private val today = LocalDate.of(2025, 3, 15)

    private lateinit var fakeLeaveDao: FakeLeaveDao
    private lateinit var fakeLeaveBalanceDao: FakeLeaveBalanceDao
    private lateinit var repository: LeaveRepository

    private val userSession = object : UserSession {
        override val currentUserId: String = "test-uid"
    }

    @Before
    fun setUp() {
        fakeLeaveDao = FakeLeaveDao()
        fakeLeaveBalanceDao = FakeLeaveBalanceDao()
        repository = LeaveRepository(fakeLeaveDao, fakeLeaveBalanceDao, userSession)
    }

    // ── addLeave — storage ────────────────────────────────────────────────────

    @Test
    fun `addLeave stores leave entry in the DAO`() = runTest {
        repository.addLeave(today, LeaveType.ANNUAL, halfDay = false, note = null)

        val entry = fakeLeaveDao.getLeaveForDate(uid, today.toString())
        assertNotNull(entry)
        assertEquals(LeaveType.ANNUAL.name, entry!!.leaveType)
        assertEquals(false, entry.halfDay)
        assertEquals(uid, entry.userId)
    }

    @Test
    fun `addLeave stores the note field`() = runTest {
        repository.addLeave(today, LeaveType.SICK, halfDay = false, note = "flu")

        val entry = fakeLeaveDao.getLeaveForDate(uid, today.toString())
        assertEquals("flu", entry!!.note)
    }

    // ── addLeave — balance update ─────────────────────────────────────────────

    @Test
    fun `addLeave full day increments usedDays by 1 0`() = runTest {
        fakeLeaveBalanceDao.store[today.year] = LeaveBalanceEntity(
            year = today.year, totalDays = 20f, usedDays = 0f, userId = uid,
        )

        repository.addLeave(today, LeaveType.ANNUAL, halfDay = false, note = null)

        val balance = fakeLeaveBalanceDao.getBalanceForYear(uid, today.year)
        assertEquals(1.0f, balance!!.usedDays)
    }

    @Test
    fun `addLeave half day increments usedDays by 0 5`() = runTest {
        fakeLeaveBalanceDao.store[today.year] = LeaveBalanceEntity(
            year = today.year, totalDays = 20f, usedDays = 0f, userId = uid,
        )

        repository.addLeave(today, LeaveType.ANNUAL, halfDay = true, note = null)

        val balance = fakeLeaveBalanceDao.getBalanceForYear(uid, today.year)
        assertEquals(0.5f, balance!!.usedDays)
    }

    @Test
    fun `addLeave accumulates usedDays across multiple entries`() = runTest {
        fakeLeaveBalanceDao.store[today.year] = LeaveBalanceEntity(
            year = today.year, totalDays = 20f, usedDays = 0f, userId = uid,
        )

        repository.addLeave(today,                  LeaveType.ANNUAL,   halfDay = false, note = null)
        repository.addLeave(today.plusDays(1), LeaveType.SICK,     halfDay = false, note = null)
        repository.addLeave(today.plusDays(2), LeaveType.PERSONAL, halfDay = true,  note = null)

        val balance = fakeLeaveBalanceDao.getBalanceForYear(uid, today.year)
        assertEquals(2.5f, balance!!.usedDays) // 1.0 + 1.0 + 0.5
    }

    @Test
    fun `addLeave does not fail and entry is stored when no balance row exists`() = runTest {
        // No balance row — refreshUsedDays should be a no-op (early return)
        repository.addLeave(today, LeaveType.ANNUAL, halfDay = false, note = null)

        assertNotNull(fakeLeaveDao.getLeaveForDate(uid, today.toString()))
        assertNull(fakeLeaveBalanceDao.getBalanceForYear(uid, today.year))
    }

    // ── removeLeave ───────────────────────────────────────────────────────────

    @Test
    fun `removeLeave deletes the entry from the DAO`() = runTest {
        repository.addLeave(today, LeaveType.ANNUAL, halfDay = false, note = null)

        repository.removeLeave(today)

        assertNull(fakeLeaveDao.getLeaveForDate(uid, today.toString()))
    }

    @Test
    fun `removeLeave recalculates usedDays to zero when all leaves are removed`() = runTest {
        fakeLeaveBalanceDao.store[today.year] = LeaveBalanceEntity(
            year = today.year, totalDays = 20f, usedDays = 0f, userId = uid,
        )
        repository.addLeave(today, LeaveType.ANNUAL, halfDay = false, note = null)

        repository.removeLeave(today)

        val balance = fakeLeaveBalanceDao.getBalanceForYear(uid, today.year)
        assertEquals(0f, balance!!.usedDays)
    }

    @Test
    fun `removeLeave recalculates usedDays correctly when other leaves remain`() = runTest {
        fakeLeaveBalanceDao.store[today.year] = LeaveBalanceEntity(
            year = today.year, totalDays = 20f, usedDays = 0f, userId = uid,
        )
        repository.addLeave(today,                  LeaveType.ANNUAL, halfDay = false, note = null)
        repository.addLeave(today.plusDays(1), LeaveType.SICK,   halfDay = false, note = null)

        // Remove only the first day
        repository.removeLeave(today)

        val balance = fakeLeaveBalanceDao.getBalanceForYear(uid, today.year)
        assertEquals(1.0f, balance!!.usedDays)
    }

    // ── getLeaveForDate ───────────────────────────────────────────────────────

    @Test
    fun `getLeaveForDate returns the stored entry`() = runTest {
        repository.addLeave(today, LeaveType.PERSONAL, halfDay = false, note = "errand")

        val result = repository.getLeaveForDate(today)

        assertNotNull(result)
        assertEquals(LeaveType.PERSONAL.name, result!!.leaveType)
        assertEquals("errand", result.note)
    }

    @Test
    fun `getLeaveForDate returns null when nothing is stored`() = runTest {
        assertNull(repository.getLeaveForDate(today))
    }
}

// ── Fake DAOs ─────────────────────────────────────────────────────────────────

private class FakeLeaveDao : LeaveDao {
    private val store = mutableMapOf<String, LeaveEntity>() // key = date string

    override suspend fun getLeaveForDate(userId: String, date: String): LeaveEntity? =
        store[date]?.takeIf { it.userId == userId }

    override fun getLeavesForRange(userId: String, startDate: String, endDate: String): Flow<List<LeaveEntity>> =
        MutableStateFlow(
            store.values
                .filter { it.userId == userId && it.date in startDate..endDate }
                .sortedBy { it.date },
        )

    override fun getLeavesForYear(userId: String, startDate: String, endDate: String): Flow<List<LeaveEntity>> =
        getLeavesForRange(userId, startDate, endDate)

    override fun sumLeaveDaysForYear(userId: String, startDate: String, endDate: String): Flow<Float> =
        MutableStateFlow(
            store.values
                .filter { it.userId == userId && it.date in startDate..endDate }
                .sumOf { if (it.halfDay) 0.5 else 1.0 }
                .toFloat(),
        )

    override suspend fun upsert(leave: LeaveEntity): Long {
        val id = (store.size + 1).toLong()
        store[leave.date] = leave.copy(id = id)
        return id
    }

    override suspend fun delete(leave: LeaveEntity) {
        store.remove(leave.date)
    }

    override suspend fun deleteByDate(userId: String, date: String) {
        if (store[date]?.userId == userId) store.remove(date)
    }

    override suspend fun getUnsynced(userId: String): List<LeaveEntity> =
        store.values.filter { it.userId == userId && !it.synced }

    override suspend fun markSynced(ids: List<Long>) {
        store.keys.toList().forEach { key ->
            val entity = store[key]
            if (entity != null && entity.id in ids) store[key] = entity.copy(synced = true)
        }
    }
}

private class FakeLeaveBalanceDao : LeaveBalanceDao {
    val store = mutableMapOf<Int, LeaveBalanceEntity>() // key = year

    override suspend fun getBalanceForYear(userId: String, year: Int): LeaveBalanceEntity? =
        store[year]?.takeIf { it.userId == userId }

    override fun observeBalanceForYear(userId: String, year: Int): Flow<LeaveBalanceEntity?> =
        MutableStateFlow(store[year]?.takeIf { it.userId == userId })

    override suspend fun upsert(balance: LeaveBalanceEntity): Long {
        val id = (store.size + 1).toLong()
        store[balance.year] = balance.copy(id = id)
        return id
    }

    override suspend fun update(balance: LeaveBalanceEntity) {
        store[balance.year] = balance
    }

    override suspend fun delete(balance: LeaveBalanceEntity) {
        store.remove(balance.year)
    }
}
