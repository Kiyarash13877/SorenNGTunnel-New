package com.soreng.tunnel.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name:           String  = "",
    val url:            String  = "",
    val lastUpdated:    Long    = 0L,
    val autoUpdate:     Boolean = true,
    val updateInterval: Int     = 86400,
    val enabled:        Boolean = true
)
