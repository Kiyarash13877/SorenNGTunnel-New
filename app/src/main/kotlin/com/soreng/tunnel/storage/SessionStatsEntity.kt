package com.soreng.tunnel.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_stats")
data class SessionStatsEntity(
    @PrimaryKey(autoGenerate = true) val id:   Long = 0,
    val configId:      Long = -1L,
    val startTime:     Long = 0L,
    val endTime:       Long = 0L,
    val uploadBytes:   Long = 0L,
    val downloadBytes: Long = 0L,
    val avgPingMs:     Long = -1L
)
