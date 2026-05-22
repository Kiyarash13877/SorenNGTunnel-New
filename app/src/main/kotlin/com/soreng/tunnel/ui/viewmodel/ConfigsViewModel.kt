package com.soreng.tunnel.ui.viewmodel

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.soreng.tunnel.config.ConfigParser
import com.soreng.tunnel.config.ConfigProfile
import com.soreng.tunnel.stats.StatsManager
import com.soreng.tunnel.storage.AppPreferences
import com.soreng.tunnel.storage.ConfigRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

@HiltViewModel
class ConfigsViewModel @Inject constructor(
    app: Application,
    private val repo:   ConfigRepository,
    private val parser: ConfigParser,
    private val prefs:  AppPreferences,
    private val stats:  StatsManager
) : AndroidViewModel(app) {

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    val configs: StateFlow<List<ConfigProfile>> = _search
        .debounce(300)
        .flatMapLatest { q -> if (q.isBlank()) repo.getAll() else repo.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importFeedback = MutableStateFlow<String?>(null)
    val importFeedback: StateFlow<String?> = _importFeedback.asStateFlow()

    fun setSearch(q: String)          { _search.value = q }
    fun clearFeedback()               { _importFeedback.value = null }
    fun toggleFavorite(id: Long)      = viewModelScope.launch { repo.toggleFavorite(id) }
    fun delete(p: ConfigProfile)      = viewModelScope.launch { repo.delete(p) }
    fun selectConfig(p: ConfigProfile)= viewModelScope.launch { prefs.setLastConfigId(p.id) }

    fun importUris(text: String) = viewModelScope.launch {
        var count = 0
        text.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
            parser.parse(line)?.let { repo.insert(it); count++ }
        }
        _importFeedback.value = if (count > 0) "Imported $count config(s)" else "No valid configs found"
    }

    fun importFromClipboard() {
        val cm = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: run {
            _importFeedback.value = "Clipboard empty"; return
        }
        importUris(text)
    }

    fun testLatency(p: ConfigProfile) = viewModelScope.launch {
        val ms = stats.run {
            // Measure via protected socket to server address
            try {
                val s = java.net.Socket()
                val t = System.currentTimeMillis()
                s.soTimeout = 3000
                s.connect(java.net.InetSocketAddress(p.address, p.port), 3000)
                val rtt = System.currentTimeMillis() - t
                s.close(); rtt
            } catch (_: Exception) { -1L }
        }
        repo.updateLatency(p.id, ms)
    }

    fun importSubscription(url: String, name: String) = viewModelScope.launch {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build()
            val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
            if (!resp.isSuccessful) { _importFeedback.value = "HTTP ${resp.code}"; return@launch }
            val body = resp.body?.string() ?: return@launch
            val decoded = try {
                String(android.util.Base64.decode(body.trim(), android.util.Base64.DEFAULT))
            } catch (_: Exception) { body }
            importUris(decoded)
        } catch (e: Exception) {
            Log.e("ConfigsVM", "Sub import: ${e.message}")
            _importFeedback.value = "Import failed: ${e.message?.take(60)}"
        }
    }
}
