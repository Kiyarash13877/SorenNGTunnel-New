package com.soreng.tunnel.storage

import com.soreng.tunnel.config.ConfigProfile
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigRepository @Inject constructor(private val dao: ConfigDao) {
    fun getAll():                      Flow<List<ConfigProfile>> = dao.getAll()
    fun getFavorites():                Flow<List<ConfigProfile>> = dao.getFavorites()
    suspend fun getById(id: Long):     ConfigProfile?            = dao.getById(id)
    suspend fun insert(p: ConfigProfile): Long                   = dao.insert(p)
    suspend fun update(p: ConfigProfile)                         = dao.update(p)
    suspend fun delete(p: ConfigProfile)                         = dao.delete(p)
    suspend fun deleteById(id: Long)                             = dao.deleteById(id)
    suspend fun toggleFavorite(id: Long) {
        val p = dao.getById(id) ?: return
        dao.update(p.copy(isFavorite = !p.isFavorite, updatedAt = System.currentTimeMillis()))
    }
    suspend fun updateLatency(id: Long, ms: Long) {
        val p = dao.getById(id) ?: return
        dao.update(p.copy(latencyMs = ms, updatedAt = System.currentTimeMillis()))
    }
    fun search(q: String): Flow<List<ConfigProfile>>             = dao.search("%$q%")
}
