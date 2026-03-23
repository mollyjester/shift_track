package com.slikharev.shifttrack.data.repository

import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.data.local.db.ShiftTrackDatabase
import com.slikharev.shifttrack.data.local.db.dao.LeaveBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.LeaveDao
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.LeaveEntity
import com.slikharev.shifttrack.model.LeaveType
import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
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
    private lateinit var mockDb: ShiftTrackDatabase
    private lateinit var repository: LeaveRepository

    private val userSession = object : UserSession {
        override val currentUserId: String = "test-uid"
    }

    @Before
    fun setUp() {
        fakeLeaveDao = FakeLeaveDao()
        fakeLeaveBalanceDao = FakeLeaveBalanceDao()
        mockDb = mockk(relaxed = true)
        mockkStatic("androidx.room.RoomDatabaseKt")
        val transactionLambda = slot<suspend () -> Any?>()
        coEvery { mockDb.withTransaction(capture(transactionLambda)) } coAnswers {
            transactionLambda.captured.invoke()
        }
        repository = LeaveRepository(mockDb, fakeLeaveDao, fakeLeaveBalanceDao, userSession)
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
        fakeLeaveBalanceDao.storeByYearType["${today.year}-ANNUAL"] = LeaveBalanceEntity(
            year = today.year, leaveType = "ANNUAL", totalDays = 20f, usedDays = 0f, userId = uid,
        )

        repository.addLeave(today, LeaveType.ANNUAL, halfDay = false, note = null)

        val balance = fakeLeaveBalanceDao.getBalanceForYearAndType(uid, today.year, "ANNUAL")
        assertEquals(1.0f, balance!!.usedDays)
    }

    @Test
    fun `addLeave half day increments usedDays by 0 5`() = runTest {
        fakeLeaveBalanceDao.storeByYearType["${today.year}-ANNUAL"] = LeaveBalanceEntity(
            year = today.year, leaveType = "ANNUAL", totalDays = 20f, usedDays = 0f, userId = uid,
        )

        repository.addLeave(today, LeaveType.ANNUAL, halfDay = true, note = null)

        val balance = fakeLeaveBalanceDao.getBalanceForYearAndType(uid, today.year, "ANNUAL")
        assertEquals(0.5f, balance!!.usedDays)
    }

    @Test
    fun `addLeave accumulates usedDays across multiple entries of same type`() = runTest {
        fakeLeaveBalanceDao.storeByYearType["${today.year}-ANNUAL"] = LeaveBalanceEntity(
            year = today.year, leaveType = "ANNUAL", totalDays = 20f, usedDays = 0f, userId = uid,
        )

        repository.addLeave(today,                  LeaveType.ANNUAL, halfDay = false, note = null)
        repository.addLeave(today.plusDays(1), LeaveType.ANNUAL, halfDay = false, note = null)
        repository.addLeave(today.plusDays(2), LeaveType.ANNUAL, halfDay = true,  note = null)

        val balance = fakeLeaveBalanceDao.getBalanceForYearAndType(uid, today.year, "ANNUAL")
        assertEquals(2.5f, balance!!.usedDays) // 1.0 + 1.0 + 0.5
    }

    @Test
    fun `addLeave creates balance row when none exists`() = runTest {
        // No balance row — refreshUsedDays should create one
        repository.addLeave(today, LeaveType.ANNUAL, halfDay = false, note = null)

        assertNotNull(fakeLeaveDao.getLeaveForDate(uid, today.toString()))
        val balance = fakeLeaveBalanceDao.getBalanceForYearAndType(uid, today.year, "ANNUAL")
        assertNotNull(balance)
        assertEquals(1.0f, balance!!.usedDays)
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
        fakeLeaveBalanceDao.storeByYearType["${today.year}-ANNUAL"] = LeaveBalanceEntity(
            year = today.year, leaveType = "ANNUAL", totalDays = 20f, usedDays = 0f, userId = uid,
        )
        repository.addLeave(today, LeaveType.ANNUAL, halfDay = false, note = null)

        repository.removeLeave(today)

        val balance = fakeLeaveBalanceDao.getBalanceForYearAndType(uid, today.year, "ANNUAL")
        assertEquals(0f, balance!!.usedDays)
    }

    @Test
    fun `removeLeave recalculates usedDays correctly when other leaves remain`() = runTest {
        fakeLeaveBalanceDao.storeByYearType["${today.year}-ANNUAL"] = LeaveBalanceEntity(
            year = today.year, leaveType = "ANNUAL", totalDays = 20f, usedDays = 0f, userId = uid,
        )
        repository.addLeave(today,                  LeaveType.ANNUAL, halfDay = false, note = null)
        repository.addLeave(today.plusDays(1), LeaveType.ANNUAL, halfDay = false, note = null)

        // Remove only the first day
        repository.removeLeave(today)

        val balance = fakeLeaveBalanceDao.getBalanceForYearAndType(uid, today.year, "ANNUAL")
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

    override fun sumLeaveDaysByType(userId: String, startDate: String, endDate: String, leaveType: String): Flow<Float> =
        MutableStateFlow(
            store.values
                .filter { it.userId == userId && it.date in startDate..endDate && it.leaveType == leaveType }
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

    override suspend fun deleteAllForUser(userId: String) {
        store.entries.removeIf { it.value.userId == userId }
    }
}

private class FakeLeaveBalanceDao : LeaveBalanceDao {
    val storeByYearType = mutableMapOf<String, LeaveBalanceEntity>() // key = "$year-$leaveType"

    private fun key(year: Int, leaveType: String) = "$year-$leaveType"

    override suspend fun getBalanceForYearAndType(userId: String, year: Int, leaveType: String): LeaveBalanceEntity? =
        storeByYearType[key(year, leaveType)]?.takeIf { it.userId == userId }

    override fun observeBalanceForYearAndType(userId: String, year: Int, leaveType: String): Flow<LeaveBalanceEntity?> =
        MutableStateFlow(storeByYearType[key(year, leaveType)]?.takeIf { it.userId == userId })

    override fun observeAllBalancesForYear(userId: String, year: Int): Flow<List<LeaveBalanceEntity>> =
        MutableStateFlow(storeByYearType.values.filter { it.userId == userId && it.year == year })

    override suspend fun getAllBalancesForYear(userId: String, year: Int): List<LeaveBalanceEntity> =
        storeByYearType.values.filter { it.userId == userId && it.year == year }

    override suspend fun upsert(balance: LeaveBalanceEntity): Long {
        val id = (storeByYearType.size + 1).toLong()
        storeByYearType[key(balance.year, balance.leaveType)] = balance.copy(id = id)
        return id
    }

    override suspend fun update(balance: LeaveBalanceEntity) {
        storeByYearType[key(balance.year, balance.leaveType)] = balance
    }

    override suspend fun delete(balance: LeaveBalanceEntity) {
        storeByYearType.remove(key(balance.year, balance.leaveType))
    }

    override suspend fun deleteAllForUser(userId: String) {
        storeByYearType.entries.removeIf { it.value.userId == userId }
    }
}
