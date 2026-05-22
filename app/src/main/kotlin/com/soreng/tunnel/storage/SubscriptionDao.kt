package com.soreng.tunnel.storage

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY name ASC")
    fun getAll(): Flow<List<SubscriptionEntity>>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insert(s: SubscriptionEntity): Long
    @Update                                         suspend fun update(s: SubscriptionEntity)
    @Delete                                         suspend fun delete(s: SubscriptionEntity)
    @Query("SELECT * FROM subscriptions WHERE id=:id") suspend fun getById(id: Long): SubscriptionEntity?
}
