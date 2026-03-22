package com.slikharev.shifttrack.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.local.db.dao.LeaveBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.OvertimeBalanceDao
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.OvertimeBalanceEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

@OptIn(ExperimentalCoroutinesApi::class)
class AnnualResetUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var appDataStore: AppDataStore
    private lateinit var fakeLeaveDao: FakeLeaveBalanceDao
    private lateinit var fakeOvertimeDao: FakeOvertimeBalanceDao
    private lateinit var useCase: AnnualResetUseCase

    private val uid = "test-uid"
    private val currentYear = LocalDate.now().year

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(testDispatcher),
            produceFile = { tempFolder.newFile("annual_reset_test_prefs.preferences_pb") },
        )
        appDataStore = AppDataStore(dataStore)
        fakeLeaveDao = FakeLeaveBalanceDao()
        fakeOvertimeDao = FakeOvertimeBalanceDao()
        useCase = AnnualResetUseCase(appDataStore, fakeLeaveDao, fakeOvertimeDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── runIfNeeded — no previous reset ──────────────────────────────────────

    @Test
    fun `runIfNeeded creates leave balance for current year when never reset before`() = testScope.runTest {
        // lastResetYear defaults to 0 — triggers reset
        val result = useCase.runIfNeeded(uid)

        assertTrue(result)
        val balance = fakeLeaveDao.getBalanceForYear(uid, currentYear)
        assertNotNull(balance)
        assertEquals(AnnualResetUseCase.DEFAULT_LEAVE_DAYS, balance!!.totalDays)
        assertEquals(0f, balance.usedDays)
        assertEquals(uid, balance.userId)
    }

    @Test
    fun `runIfNeeded carries over totalDays from previous year`() = testScope.runTest {
        val previousYear = currentYear - 1
        fakeLeaveDao.store[previousYear] = LeaveBalanceEntity(
            id = 1L, year = previousYear, totalDays = 25f, usedDays = 3f, userId = uid
        )

        useCase.runIfNeeded(uid)

        val newBalance = fakeLeaveDao.getBalanceForYear(uid, currentYear)
        assertNotNull(newBalance)
        assertEquals(25f, newBalance!!.totalDays)  // carried from previous year
        assertEquals(0f, newBalance.usedDays)       // reset to 0
    }

    @Test
    fun `runIfNeeded sets lastResetYear to currentYear`() = testScope.runTest {
        useCase.runIfNeeded(uid)
        testScheduler.advanceUntilIdle()

        assertEquals(currentYear, appDataStore.lastResetYear.first())
    }

    // ── runIfNeeded — already reset this year ─────────────────────────────────

    @Test
    fun `runIfNeeded is a no-op when lastResetYear equals currentYear`() = testScope.runTest {
        appDataStore.setLastResetYear(currentYear)

        val result = useCase.runIfNeeded(uid)

        assertFalse(result)
        assertNull(fakeLeaveDao.getBalanceForYear(uid, currentYear))
    }

    // ── runIfNeeded — leave balance already exists for new year ───────────────

    @Test
    fun `runIfNeeded skips leave upsert when balance already exists for current year`() = testScope.runTest {
        fakeLeaveDao.store[currentYear] = LeaveBalanceEntity(
            id = 1L, year = currentYear, totalDays = 30f, usedDays = 5f, userId = uid
        )
        val upsertCountBefore = fakeLeaveDao.upsertCount

        useCase.runIfNeeded(uid)

        // upsert should NOT have been called again
        assertEquals(upsertCountBefore, fakeLeaveDao.upsertCount)
    }

    // ── multi-year gap ────────────────────────────────────────────────────────

    @Test
    fun `runIfNeeded with multi-year gap carries totalDays from last reset year`() =
        testScope.runTest {
            val lastResetYear = currentYear - 3
            appDataStore.setLastResetYear(lastResetYear)
            fakeLeaveDao.store[lastResetYear] = LeaveBalanceEntity(
                id = 1L, year = lastResetYear, totalDays = 22f, usedDays = 10f, userId = uid,
            )

            val result = useCase.runIfNeeded(uid)

            assertTrue(result)
            val balance = fakeLeaveDao.getBalanceForYear(uid, currentYear)
            assertNotNull(balance)
            assertEquals(22f, balance!!.totalDays)    // carried from 3 years ago
            assertEquals(0f, balance.usedDays)         // reset to 0
            assertEquals(currentYear, appDataStore.lastResetYear.first())
        }

    @Test
    fun `runIfNeeded with multi-year gap uses DEFAULT when no prior balance found`() =
        testScope.runTest {
            // Set last reset year to 3 years ago — no balance row exists for that year
            appDataStore.setLastResetYear(currentYear - 3)

            val result = useCase.runIfNeeded(uid)

            assertTrue(result)
            val balance = fakeLeaveDao.getBalanceForYear(uid, currentYear)
            assertEquals(AnnualResetUseCase.DEFAULT_LEAVE_DAYS, balance!!.totalDays)
        }

    // ── idempotency ───────────────────────────────────────────────────────────

    @Test
    fun `runIfNeeded is idempotent — second call in same year does nothing`() = testScope.runTest {
        useCase.runIfNeeded(uid)
        val upsertAfterFirst = fakeLeaveDao.upsertCount

        val secondCallResult = useCase.runIfNeeded(uid)

        assertFalse(secondCallResult)
        assertEquals(upsertAfterFirst, fakeLeaveDao.upsertCount)
    }
}

// ── Fake DAOs ────────────────────────────────────────────────────────────────

private class FakeLeaveBalanceDao : LeaveBalanceDao {
    /** Keyed by year for simplicity. */
    val store = mutableMapOf<Int, LeaveBalanceEntity>()
    var upsertCount = 0

    override suspend fun getBalanceForYear(userId: String, year: Int): LeaveBalanceEntity? =
        store[year]?.takeIf { it.userId == userId }

    override fun observeBalanceForYear(userId: String, year: Int): Flow<LeaveBalanceEntity?> =
        MutableStateFlow(store[year]?.takeIf { it.userId == userId })

    override suspend fun upsert(balance: LeaveBalanceEntity): Long {
        upsertCount++
        store[balance.year] = balance.copy(id = upsertCount.toLong())
        return upsertCount.toLong()
    }

    override suspend fun update(balance: LeaveBalanceEntity) {
        store[balance.year] = balance
    }

    override suspend fun delete(balance: LeaveBalanceEntity) {
        store.remove(balance.year)
    }
}

private class FakeOvertimeBalanceDao : OvertimeBalanceDao {
    private val _flow = MutableStateFlow<OvertimeBalanceEntity?>(null)
    var stored: OvertimeBalanceEntity? = null

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
        _flow.value = stored
    }

    override suspend fun delete(balance: OvertimeBalanceEntity) {
        stored = null
        _flow.value = null
    }
}
