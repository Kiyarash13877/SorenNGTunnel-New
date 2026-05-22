package com.soreng.tunnel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.soreng.tunnel.stats.StatsManager
import com.soreng.tunnel.vpn.SorenVpnService
import com.soreng.tunnel.vpn.VpnConnectionState
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(private val stats: StatsManager) : ViewModel() {
    val uploadSpeed   = stats.uploadSpeed
    val downloadSpeed = stats.downloadSpeed
    val ping          = stats.ping
    val uploadTotal   = stats.uploadTotal
    val downloadTotal = stats.downloadTotal

    private val _history  = MutableStateFlow<List<Pair<Long,Long>>>(emptyList())
    private val _duration = MutableStateFlow("00:00:00")
    val history:  StateFlow<List<Pair<Long,Long>>> = _history.asStateFlow()
    val duration: StateFlow<String>                = _duration.asStateFlow()

    init {
        // History — only while connected
        viewModelScope.launch {
            SorenVpnService.state.flatMapLatest { s ->
                if (s is VpnConnectionState.Connected)
                    combine(uploadSpeed, downloadSpeed) { u, d -> u to d }
                else flowOf(0L to 0L).also { _history.value = emptyList() }
            }.collect { p -> if (p.first > 0 || p.second > 0) _history.update { (it + p).takeLast(60) } }
        }
        // Session timer — lifecycle-aware isActive loop
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val s = SorenVpnService.state.value
                _duration.value = if (s is VpnConnectionState.Connected) {
                    val e = (System.currentTimeMillis() - s.connectedAt) / 1_000L
                    "%02d:%02d:%02d".format(e/3600, (e%3600)/60, e%60)
                } else "00:00:00"
            }
        }
    }

    fun fmt(bps: Long)  = stats.fmtSpeed(bps)
    fun fmtB(b: Long)   = stats.fmtBytes(b)
}
