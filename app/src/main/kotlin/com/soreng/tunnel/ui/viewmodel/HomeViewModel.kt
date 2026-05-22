package com.soreng.tunnel.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.soreng.tunnel.config.ConfigProfile
import com.soreng.tunnel.stats.StatsManager
import com.soreng.tunnel.storage.AppPreferences
import com.soreng.tunnel.storage.ConfigRepository
import com.soreng.tunnel.vpn.SorenVpnService
import com.soreng.tunnel.vpn.VpnConnectionState
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    app: Application,
    private val stats: StatsManager,
    private val prefs: AppPreferences,
    private val repo:  ConfigRepository
) : AndroidViewModel(app) {

    val state:         StateFlow<VpnConnectionState> = SorenVpnService.state
    val uploadSpeed    = stats.uploadSpeed
    val downloadSpeed  = stats.downloadSpeed
    val ping           = stats.ping
    val uploadTotal    = stats.uploadTotal
    val downloadTotal  = stats.downloadTotal

    private val _selected = MutableStateFlow<ConfigProfile?>(null)
    val selected: StateFlow<ConfigProfile?> = _selected.asStateFlow()

    private val _history = MutableStateFlow<List<Pair<Long,Long>>>(emptyList())
    val history: StateFlow<List<Pair<Long,Long>>> = _history.asStateFlow()

    @Volatile private var connectPending = false

    init {
        loadLastConfig()
        collectHistory()
        observeState()
    }

    private fun loadLastConfig() = viewModelScope.launch {
        val id = prefs.getLastConfigId()
        _selected.value = if (id >= 0) repo.getById(id)
                          else repo.getAll().first().firstOrNull()
    }

    private fun collectHistory() = viewModelScope.launch {
        state.flatMapLatest { s ->
            if (s is VpnConnectionState.Connected)
                combine(uploadSpeed, downloadSpeed) { u, d -> u to d }
            else flowOf(0L to 0L).also { _history.value = emptyList() }
        }.collect { pair ->
            if (pair.first > 0 || pair.second > 0)
                _history.update { (it + pair).takeLast(60) }
        }
    }

    private fun observeState() = viewModelScope.launch {
        state.collect { s ->
            if (s is VpnConnectionState.Disconnected || s is VpnConnectionState.Error)
                connectPending = false
        }
    }

    fun connect(cfgId: Long) {
        if (connectPending || state.value.isActive) return
        connectPending = true
        viewModelScope.launch {
            prefs.setLastConfigId(cfgId)
            val ctx = getApplication<Application>()
            ctx.startForegroundService(
                Intent(ctx, SorenVpnService::class.java).apply {
                    action = SorenVpnService.ACTION_START
                    putExtra(SorenVpnService.EXTRA_CONFIG_ID, cfgId)
                })
        }
    }

    fun disconnect() {
        connectPending = false
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, SorenVpnService::class.java).apply {
            action = SorenVpnService.ACTION_STOP })
    }

    fun selectConfig(p: ConfigProfile) {
        _selected.value = p
        viewModelScope.launch { prefs.setLastConfigId(p.id) }
    }

    fun fmt(bps: Long)  = stats.fmtSpeed(bps)
    fun fmtB(b: Long)   = stats.fmtBytes(b)
}
