package com.slikharev.shifttrack.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.remote.FirestoreUserDataSource
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class FcmTokenManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var appDataStore: AppDataStore
    private val mockFirestoreUserDataSource: FirestoreUserDataSource = mockk(relaxed = true)
    private lateinit var manager: FcmTokenManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(testDispatcher),
            produceFile = { tempFolder.newFile("fcm_token_test_prefs.preferences_pb") },
        )
        appDataStore = AppDataStore(dataStore)
        manager = FcmTokenManager(appDataStore, mockFirestoreUserDataSource)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── onTokenRefreshed ─────────────────────────────────────────────────────

    @Test
    fun `onTokenRefreshed persists token in DataStore when user is not logged in`() =
        testScope.runTest {
            manager.onTokenRefreshed("token-abc", uid = null)

            assertEquals("token-abc", appDataStore.pendingFcmToken.first())
            coVerify(exactly = 0) { mockFirestoreUserDataSource.saveFcmToken(any(), any()) }
        }

    @Test
    fun `onTokenRefreshed persists token and uploads to Firestore when user is logged in`() =
        testScope.runTest {
            coJustRun { mockFirestoreUserDataSource.saveFcmToken(any(), any()) }

            manager.onTokenRefreshed("token-xyz", uid = "uid-123")

            assertEquals("token-xyz", appDataStore.pendingFcmToken.first())
            coVerify(exactly = 1) { mockFirestoreUserDataSource.saveFcmToken("uid-123", "token-xyz") }
        }

    @Test
    fun `onTokenRefreshed does not propagate Firestore errors`() =
        testScope.runTest {
            coEvery { mockFirestoreUserDataSource.saveFcmToken(any(), any()) } throws Exception("Network error")

            // Should complete without throwing
            manager.onTokenRefreshed("token-err", uid = "uid-123")

            // Token is still persisted locally even when Firestore upload fails
            assertEquals("token-err", appDataStore.pendingFcmToken.first())
        }

    // ── uploadPendingToken ───────────────────────────────────────────────────

    @Test
    fun `uploadPendingToken uploads stored token to Firestore`() =
        testScope.runTest {
            appDataStore.setPendingFcmToken("stored-token")
            coJustRun { mockFirestoreUserDataSource.saveFcmToken(any(), any()) }

            manager.uploadPendingToken("uid-456")

            coVerify(exactly = 1) { mockFirestoreUserDataSource.saveFcmToken("uid-456", "stored-token") }
        }

    @Test
    fun `uploadPendingToken is a no-op when no token is stored`() =
        testScope.runTest {
            manager.uploadPendingToken("uid-456")

            coVerify(exactly = 0) { mockFirestoreUserDataSource.saveFcmToken(any(), any()) }
        }
}
