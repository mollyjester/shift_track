package com.slikharev.shifttrack.widget

import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.local.db.dao.LeaveDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShiftWidgetEntryPoint {
    fun appDataStore(): AppDataStore
    fun leaveDao(): LeaveDao
}
