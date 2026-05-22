package com.soreng.tunnel.psiphon

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the embedded Psiphon tunnel.
 *
 * Sources: https://github.com/Psiphon-Inc/psiphon-android
 *          https://github.com/Psiphon-Labs/psiphon-tunnel-core
 *
 * Psiphon runs as a child process exposing SOCKS5 on 127.0.0.1:1080.
 * Xray chains ALL outbound traffic through this SOCKS5.
 *
 * HARD RULE: if Psiphon cannot start → throw. Never allow direct fallback.
 */
@Singleton
class PsiphonManager @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val TAG   = "PsiphonManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var proc:         Process? = null
    private var outJob:         Job? = null
    private var errJob:         Job? = null
    private var heartbeatJob:   Job? = null

    companion object {
        const val SOCKS_HOST = "127.0.0.1"
        const val SOCKS_PORT = 1080
        private const val HEARTBEAT_MS = 15_000L
        private const val MIN_BINARY_BYTES = 512L
    }

    suspend fun start() = withContext(Dispatchers.IO) {
        stop()
        val bin = File(ctx.filesDir, "bin/psiphon")
        when {
            bin.exists() && bin.length() > MIN_BINARY_BYTES -> startBinary(bin)
            else -> startLibrary()
        }
        startHeartbeat()
    }

    private suspend fun startBinary(bin: File) {
        if (!bin.canExecute()) bin.setExecutable(true, false)
        val cfg = File(ctx.filesDir, "psiphon_config.json")
        cfg.writeText(buildConfig().toString(2))

        val pb = ProcessBuilder(bin.absolutePath, "--config", cfg.absolutePath).apply {
            redirectErrorStream(false)
            directory(ctx.filesDir)
            environment()["PSIPHON_DATA_ROOT"] = ctx.filesDir.absolutePath
        }
        val p = pb.start(); proc = p
        writePid("psiphon", p.pid().toLong())
        outJob = scope.launch { drainStream(p.inputStream, "psiphon/out") }
        errJob = scope.launch { drainStream(p.errorStream, "psiphon/err") }
        delay(1_300)
        if (!p.isAlive) {
            delPid("psiphon")
            throw IllegalStateException("Psiphon exited immediately (exit=${p.exitValue()})")
        }
        Log.i(TAG, "Psiphon binary running pid=${p.pid()}")
    }

    private fun startLibrary() {
        if (!PsiphonLibBridge.start(SOCKS_PORT)) {
            throw IllegalStateException(
                "Psiphon unavailable: binary not found at filesDir/bin/psiphon " +
                "and libpsiphon.so not loaded. Build from " +
                "https://github.com/Psiphon-Inc/psiphon-android and place binary in assets.")
        }
        Log.i(TAG, "Psiphon library started on SOCKS5 :$SOCKS_PORT")
    }

    private fun buildConfig() = JSONObject().apply {
        put("PropagationChannelId",              "FFFFFFFFFFFFFFFF")
        put("SponsorId",                         "FFFFFFFFFFFFFFFF")
        put("RemoteServerListURLs",               JSONArray())
        put("RemoteServerListSignaturePublicKey", "")
        put("LocalSocksProxyPort",                SOCKS_PORT)
        put("LocalHttpProxyPort",                 0)
        put("DisableLocalHTTPProxy",              true)
        put("DisableLocalSocksProxy",             false)
        put("EmitDiagnosticNotices",              false)
        put("EmitServerAlerts",                   false)
        put("UpstreamProxyURL",                   "")
        put("EgressRegion",                       "")
        put("TunnelProtocol",                     "")
        put("ConnectionWorkerPoolSize",            5)
        put("LimitIntensiveConnectionWorkers",     3)
        put("TunnelConnectTimeoutSeconds",         20)
        put("TunnelPortForwardDialTimeoutSeconds", 10)
        put("PacketTunnelReadTimeout",             "30s")
        put("PacketTunnelWriteTimeout",            "30s")
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                if (!isRunning()) Log.w(TAG, "Heartbeat: Psiphon not running")
            }
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        heartbeatJob?.cancel(); heartbeatJob = null
        outJob?.cancel(); outJob = null
        errJob?.cancel(); errJob = null
        proc?.let { p ->
            Log.i(TAG, "Stopping Psiphon pid=${pid(p)}")
            p.destroy()
            withTimeoutOrNull(5_000) { while (p.isAlive) delay(100) }
            if (p.isAlive) { p.destroyForcibly(); withTimeoutOrNull(3_000) { while (p.isAlive) delay(100) } }
            if (p.isAlive) Log.e(TAG, "Psiphon zombie — kill failed")
        }
        proc = null; delPid("psiphon")
        runCatching { PsiphonLibBridge.stop() }
    }

    fun isRunning(): Boolean =
        (proc?.isAlive == true) || runCatching { PsiphonLibBridge.isConnected() }.getOrDefault(false)

    private fun drainStream(stream: InputStream, label: String) {
        try {
            stream.bufferedReader(Charsets.UTF_8).use { r ->
                var line: String?
                while (r.readLine().also { line = it } != null && !Thread.currentThread().isInterrupted) {
                    val l = line!!
                    when { l.contains("ERROR",true) -> Log.e(TAG,"[$label] $l")
                           l.contains("warn", true) -> Log.w(TAG,"[$label] $l")
                           else                     -> Log.v(TAG,"[$label] $l") }
                }
            }
        } catch (e: Exception) {
            val m = e.message ?: ""
            if (!m.contains("closed",true) && !m.contains("EOF",true)) Log.w(TAG,"[$label] drain: $m")
        }
    }

    private fun writePid(n: String, pid: Long) = runCatching { File(ctx.filesDir,"$n.pid").writeText(pid.toString()) }
    private fun delPid(n: String)              = runCatching { File(ctx.filesDir,"$n.pid").delete() }
    private fun pid(p: Process): String        = try { p.pid().toString() } catch (_: Exception) { "?" }
}
