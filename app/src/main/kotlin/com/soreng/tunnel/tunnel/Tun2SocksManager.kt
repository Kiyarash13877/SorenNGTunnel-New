package com.soreng.tunnel.tunnel

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages tun2socks process.
 * Source: https://github.com/xjasonlyu/tun2socks
 *
 * MTU MUST match VpnService.Builder.setMtu() = 1500.
 * fd passed via -device fd://N (tun2socks 2.x).
 * UDP enabled for QUIC/Reality.
 * Both stdout/stderr drained asynchronously to prevent SIGPIPE deadlock.
 */
@Singleton
class Tun2SocksManager @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val TAG   = "Tun2SocksManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var proc: Process? = null
    private var outJob: Job? = null
    private var errJob: Job? = null

    suspend fun start(
        tunFd:     Int,
        socksHost: String  = "127.0.0.1",
        socksPort: Int     = 10808,
        mtu:       Int     = 1500,
        udp:       Boolean = true
    ) = withContext(Dispatchers.IO) {
        stop()
        val bin = File(ctx.filesDir, "bin/tun2socks")
        if (!bin.exists() || bin.length() < 512L)
            throw IllegalStateException("tun2socks binary missing at ${bin.absolutePath}")
        if (!bin.canExecute()) bin.setExecutable(true, false)

        val args = mutableListOf(
            bin.absolutePath,
            "-device",               "fd://$tunFd",
            "-proxy",                "socks5://$socksHost:$socksPort",
            "-mtu",                  mtu.toString(),
            "-loglevel",             "warning",
            "-tcp-send-buffer-size", "524288",
            "-tcp-recv-buffer-size", "524288",
            "-tcp-auto-tuning",      "true"
        )
        if (udp) args += listOf("-udp-timeout", "30s", "-udp-buf-size", "65535")

        Log.i(TAG, "tun2socks: ${args.joinToString(" ")}")

        val pb = ProcessBuilder(args).apply {
            redirectErrorStream(false)
            directory(ctx.filesDir)
            environment()["TUN_FD"]  = tunFd.toString()
            environment()["TUN_MTU"] = mtu.toString()
        }
        val p = pb.start(); proc = p
        outJob = scope.launch { drain(p.inputStream, "t2s/out") }
        errJob = scope.launch { drain(p.errorStream, "t2s/err") }

        delay(800)
        if (!p.isAlive)
            throw IllegalStateException("tun2socks exited immediately (exit=${p.exitValue()})")
        Log.i(TAG, "tun2socks running pid=${pid(p)}")
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        outJob?.cancel(); outJob = null
        errJob?.cancel(); errJob = null
        proc?.let { p ->
            Log.i(TAG, "Stopping tun2socks")
            p.destroy()
            withTimeoutOrNull(3_000) { while (p.isAlive) delay(100) }
            if (p.isAlive) { p.destroyForcibly(); withTimeoutOrNull(2_000) { while (p.isAlive) delay(100) } }
        }
        proc = null
    }

    fun isRunning(): Boolean = proc?.isAlive == true

    private fun drain(s: InputStream, lbl: String) {
        try {
            s.bufferedReader(Charsets.UTF_8).use { r ->
                var l: String?
                while (r.readLine().also { l = it } != null && !Thread.currentThread().isInterrupted) {
                    val line = l!!
                    if (line.contains("ERROR",true) || line.contains("WARN",true))
                        Log.w(TAG, "[$lbl] $line")
                    else Log.v(TAG, "[$lbl] $line")
                }
            }
        } catch (e: Exception) {
            val m = e.message ?: ""
            if (!m.contains("closed",true) && !m.contains("EOF",true)) Log.w(TAG,"[$lbl] $m")
        }
    }

    private fun pid(p: Process): String = try { p.pid().toString() } catch (_: Exception) { "?" }
}
