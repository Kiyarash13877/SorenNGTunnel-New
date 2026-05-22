package com.soreng.tunnel.storage

import androidx.room.*
import com.soreng.tunnel.config.ConfigProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {
    @Query("SELECT * FROM config_profiles ORDER BY isFavorite DESC, updatedAt DESC")
    fun getAll(): Flow<List<ConfigProfile>>

    @Query("SELECT * FROM config_profiles WHERE isFavorite=1 ORDER BY updatedAt DESC")
    fun getFavorites(): Flow<List<ConfigProfile>>

    @Query("SELECT * FROM config_profiles WHERE id=:id")
    suspend fun getById(id: Long): ConfigProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(p: ConfigProfile): Long

    @Update
    suspend fun update(p: ConfigProfile)

    @Delete
    suspend fun delete(p: ConfigProfile)

    @Query("DELETE FROM config_profiles WHERE id=:id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM config_profiles WHERE subscriptionId=:subId")
    suspend fun getBySubscription(subId: Long): List<ConfigProfile>

    @Query("DELETE FROM config_profiles WHERE subscriptionId=:subId")
    suspend fun deleteBySubscription(subId: Long)

    @Query("SELECT * FROM config_profiles WHERE name LIKE :q OR address LIKE :q OR remarks LIKE :q ORDER BY isFavorite DESC, updatedAt DESC")
    fun search(q: String): Flow<List<ConfigProfile>>
}
