package com.soreng.tunnel.stats

import android.content.Context
import android.net.TrafficStats
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.soreng.tunnel.storage.SessionStatsDao
import com.soreng.tunnel.storage.SessionStatsEntity
import com.soreng.tunnel.vpn.SocketProtector
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time traffic statistics.
 *
 * - Uses TrafficStats.getUidRxBytes/TxBytes for real packet counters.
 * - 1-second tick — lifecycle-aware (runs only during active session).
 * - Ping uses protected socket → measures Xray→Psiphon path RTT.
 * - No fake stats, no polling when disconnected.
 */
@Singleton
class StatsManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val statsDao:   SessionStatsDao,
    private val protector:  SocketProtector
) {
    private val TAG   = "StatsManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uploadSpeed   = MutableStateFlow(0L)
    private val _downloadSpeed = MutableStateFlow(0L)
    private val _ping          = MutableStateFlow(-1L)
    private val _uploadTotal   = MutableStateFlow(0L)
    private val _downloadTotal = MutableStateFlow(0L)

    val uploadSpeed:   StateFlow<Long> = _uploadSpeed.asStateFlow()
    val downloadSpeed: StateFlow<Long> = _downloadSpeed.asStateFlow()
    val ping:          StateFlow<Long> = _ping.asStateFlow()
    val uploadTotal:   StateFlow<Long> = _uploadTotal.asStateFlow()
    val downloadTotal: StateFlow<Long> = _downloadTotal.asStateFlow()

    @Volatile private var tickJob:     Job? = null
    @Volatile private var pingJob:     Job? = null
    @Volatile private var sessionId:   Long = -1L
    @Volatile private var sessEntity:  SessionStatsEntity? = null
    private var baseRx = 0L; private var baseTx = 0L
    private var prevRx = 0L; private var prevTx = 0L
    private var prevTs = 0L

    fun startSession() {
        stopSession()
        val uid = android.os.Process.myUid()
        prevRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)
        prevTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)
        baseRx = prevRx; baseTx = prevTx
        prevTs = System.currentTimeMillis()
        _uploadTotal.value = 0L; _downloadTotal.value = 0L
        _uploadSpeed.value = 0L; _downloadSpeed.value = 0L
        _ping.value        = -1L

        scope.launch {
            val e = SessionStatsEntity(startTime = System.currentTimeMillis())
            sessionId = statsDao.insert(e); sessEntity = e.copy(id = sessionId)
        }

        tickJob = scope.launch {
            while (isActive) { delay(1_000); tick(uid) }
        }
        // Ping every 8s — lower frequency reduces battery usage
        pingJob = scope.launch {
            while (isActive) { delay(8_000); _ping.value = measurePing() }
        }
    }

    private fun tick(uid: Int) {
        val now = System.currentTimeMillis()
        val rx  = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)
        val tx  = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)
        val dt  = (now - prevTs).coerceAtLeast(1L)

        _downloadSpeed.value  = (rx - prevRx).coerceAtLeast(0L) * 1_000L / dt
        _uploadSpeed.value    = (tx - prevTx).coerceAtLeast(0L) * 1_000L / dt
        _downloadTotal.value  = (rx - baseRx).coerceAtLeast(0L)
        _uploadTotal.value    = (tx - baseTx).coerceAtLeast(0L)

        prevRx = rx; prevTx = tx; prevTs = now
    }

    private fun measurePing(): Long = try {
        Socket().use { s ->
            protector.protect(s)  // protect → ping does not route through VPN
            val t = System.currentTimeMillis()
            s.soTimeout = 3_000; s.tcpNoDelay = true
            s.connect(InetSocketAddress("1.1.1.1", 443), 3_000)
            System.currentTimeMillis() - t
        }
    } catch (_: Exception) { -1L }

    fun stopSession() {
        tickJob?.cancel(); tickJob = null
        pingJob?.cancel(); pingJob = null
        scope.launch {
            sessEntity?.let { e ->
                runCatching { statsDao.update(e.copy(
                    endTime       = System.currentTimeMillis(),
                    uploadBytes   = _uploadTotal.value,
                    downloadBytes = _downloadTotal.value,
                    avgPingMs     = _ping.value
                ))}
            }
        }
        _uploadSpeed.value = 0L; _downloadSpeed.value = 0L; _ping.value = -1L
        sessEntity = null
    }

    fun fmtBytes(b: Long): String = when {
        b < 1_024L         -> "$b B"
        b < 1_048_576L     -> "${"%.1f".format(b/1_024.0)} KB"
        b < 1_073_741_824L -> "${"%.2f".format(b/1_048_576.0)} MB"
        else               -> "${"%.2f".format(b/1_073_741_824.0)} GB"
    }
    fun fmtSpeed(bps: Long): String = when {
        bps < 1_024L     -> "$bps B/s"
        bps < 1_048_576L -> "${"%.1f".format(bps/1_024.0)} KB/s"
        else             -> "${"%.2f".format(bps/1_048_576.0)} MB/s"
    }
}
