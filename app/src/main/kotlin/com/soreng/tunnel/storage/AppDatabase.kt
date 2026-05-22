package com.soreng.tunnel.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.soreng.tunnel.config.ConfigProfile

@Database(
    entities  = [ConfigProfile::class, SubscriptionEntity::class, SessionStatsEntity::class],
    version   = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun configDao():  ConfigDao
    abstract fun subDao():     SubscriptionDao
    abstract fun statsDao():   SessionStatsDao
}
