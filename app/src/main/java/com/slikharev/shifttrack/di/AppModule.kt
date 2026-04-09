package com.slikharev.shifttrack.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.slikharev.shifttrack.data.local.db.ShiftTrackDatabase
import com.slikharev.shifttrack.data.local.db.dao.AttachmentDao
import com.slikharev.shifttrack.data.local.db.dao.LeaveBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.LeaveDao
import com.slikharev.shifttrack.data.local.db.dao.OvertimeBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.OvertimeDao
import com.slikharev.shifttrack.data.local.db.dao.ShiftDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `attachments` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `date` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `file_name` TEXT NOT NULL,
                `mime_type` TEXT NOT NULL,
                `file_size_bytes` INTEGER NOT NULL,
                `local_path` TEXT NOT NULL,
                `firebase_path` TEXT,
                `synced` INTEGER NOT NULL DEFAULT 0,
                `created_at` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_attachments_date_user_id_file_name`
            ON `attachments` (`date`, `user_id`, `file_name`)
            """.trimIndent(),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "shift_track_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("shift_track_prefs") },
        )

    // ── Room ────────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideShiftTrackDatabase(@ApplicationContext context: Context): ShiftTrackDatabase =
        Room.databaseBuilder(context, ShiftTrackDatabase::class.java, "shift_track.db")
            .addMigrations(MIGRATION_4_5)
            .build()

    @Provides
    fun provideShiftDao(db: ShiftTrackDatabase): ShiftDao = db.shiftDao()

    @Provides
    fun provideLeaveDao(db: ShiftTrackDatabase): LeaveDao = db.leaveDao()

    @Provides
    fun provideOvertimeDao(db: ShiftTrackDatabase): OvertimeDao = db.overtimeDao()

    @Provides
    fun provideLeaveBalanceDao(db: ShiftTrackDatabase): LeaveBalanceDao = db.leaveBalanceDao()

    @Provides
    fun provideOvertimeBalanceDao(db: ShiftTrackDatabase): OvertimeBalanceDao = db.overtimeBalanceDao()

    @Provides
    fun provideAttachmentDao(db: ShiftTrackDatabase): AttachmentDao = db.attachmentDao()
}
