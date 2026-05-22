package com.soreng.tunnel.storage

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionStatsDao {
    @Insert                suspend fun insert(s: SessionStatsEntity): Long
    @Update                suspend fun update(s: SessionStatsEntity)
    @Query("SELECT * FROM session_stats ORDER BY startTime DESC LIMIT :n")
    fun getRecent(n: Int = 50): Flow<List<SessionStatsEntity>>
    @Query("SELECT SUM(uploadBytes) FROM session_stats")   suspend fun totalUpload(): Long?
    @Query("SELECT SUM(downloadBytes) FROM session_stats") suspend fun totalDownload(): Long?
}
