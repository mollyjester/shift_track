package com.slikharev.shifttrack.widget

import com.slikharev.shifttrack.data.local.AppDataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point used by [ShiftWidget] (a system component not managed by
 * Hilt) to retrieve [AppDataStore] from the application-scoped DI graph.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShiftWidgetEntryPoint {
    fun appDataStore(): AppDataStore
}
