package com.slikharev.shifttrack.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.slikharev.shifttrack.data.local.db.dao.LeaveBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.LeaveDao
import com.slikharev.shifttrack.data.local.db.dao.OvertimeBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.OvertimeDao
import com.slikharev.shifttrack.data.local.db.dao.ShiftDao
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.LeaveEntity
import com.slikharev.shifttrack.data.local.db.entity.OvertimeBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.OvertimeEntity
import com.slikharev.shifttrack.data.local.db.entity.ShiftEntity

@Database(
    entities = [
        ShiftEntity::class,
        LeaveEntity::class,
        OvertimeEntity::class,
        LeaveBalanceEntity::class,
        OvertimeBalanceEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ShiftTrackDatabase : RoomDatabase() {
    abstract fun shiftDao(): ShiftDao
    abstract fun leaveDao(): LeaveDao
    abstract fun overtimeDao(): OvertimeDao
    abstract fun leaveBalanceDao(): LeaveBalanceDao
    abstract fun overtimeBalanceDao(): OvertimeBalanceDao
}
