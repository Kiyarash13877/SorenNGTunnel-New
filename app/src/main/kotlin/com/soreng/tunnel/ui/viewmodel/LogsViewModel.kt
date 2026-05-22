package com.soreng.tunnel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.soreng.tunnel.vpn.SorenVpnService
import com.soreng.tunnel.vpn.VpnConnectionState
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor() : ViewModel() {

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    init {
        viewModelScope.launch {
            SorenVpnService.state.collect { state ->
                when (state) {
                    is VpnConnectionState.Connecting ->
                        addLog("Connecting...")
                    is VpnConnectionState.Connected ->
                        addLog("Connected — probe latency=${state.probeLatencyMs}ms")
                    is VpnConnectionState.Disconnected ->
                        addLog("Disconnected")
                    is VpnConnectionState.Error ->
                        addLog("ERROR: ${state.message}")
                    else -> {}
                }
            }
        }
    }

    private fun addLog(msg: String) {
        val entry = "[${fmt.format(Date())}] $msg"
        _logs.update { (it + entry).takeLast(200) }
    }

    fun clearLogs() { _logs.value = emptyList() }
}
