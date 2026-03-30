package com.slikharev.shifttrack.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.slikharev.shifttrack.alarm.AlarmOverrideDao // [EXPERIMENTAL:ALARM]
import com.slikharev.shifttrack.data.local.db.ShiftTrackDatabase
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

    // [EXPERIMENTAL:ALARM] Migration 2→3: add alarm_overrides table
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `alarm_overrides` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `date` TEXT NOT NULL,
                    `alarm_count` INTEGER NOT NULL,
                    `interval_minutes` INTEGER NOT NULL,
                    `first_alarm_time` TEXT NOT NULL,
                    `user_id` TEXT NOT NULL,
                    `synced` INTEGER NOT NULL DEFAULT 0
                )""",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_alarm_overrides_date_user_id` ON `alarm_overrides` (`date`, `user_id`)",
            )
        }
    }

    @Provides
    @Singleton
    fun provideShiftTrackDatabase(@ApplicationContext context: Context): ShiftTrackDatabase =
        Room.databaseBuilder(context, ShiftTrackDatabase::class.java, "shift_track.db")
            .addMigrations(MIGRATION_2_3) // [EXPERIMENTAL:ALARM]
            .fallbackToDestructiveMigration()
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

    // [EXPERIMENTAL:ALARM]
    @Provides
    fun provideAlarmOverrideDao(db: ShiftTrackDatabase): AlarmOverrideDao = db.alarmOverrideDao()
}
