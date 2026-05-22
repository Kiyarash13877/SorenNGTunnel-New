package com.soreng.tunnel.xray

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import com.soreng.tunnel.config.RuntimeConfigBuilder
import com.soreng.tunnel.storage.ConfigRepository
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XrayManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val configBuilder: RuntimeConfigBuilder,
    private val configRepo: ConfigRepository
) {
    private val TAG   = "XrayManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var proc: Process? = null
    private var outJob: Job? = null
    private var errJob: Job? = null

    suspend fun start(configId: Long, psiphonSocksPort: Int) = withContext(Dispatchers.IO) {
        stop()
        val profile = configRepo.getById(configId)
            ?: throw IllegalArgumentException("Config $configId not found")
        val cfg = configBuilder.build(profile, psiphonSocksPort)
        val cfgFile = File(ctx.filesDir, "xray_config.json")
        cfgFile.writeText(GsonBuilder().setPrettyPrinting().create().toJson(cfg))

        val bin = File(ctx.filesDir, "bin/xray")
        if (!bin.exists() || bin.length() < 512L)
            throw IllegalStateException("Xray binary missing/invalid at ${bin.absolutePath}")
        if (!bin.canExecute()) bin.setExecutable(true, false)

        val pb = ProcessBuilder(bin.absolutePath, "run", "-config", cfgFile.absolutePath).apply {
            redirectErrorStream(false)
            directory(ctx.filesDir)
            environment()["XRAY_LOCATION_ASSET"] = ctx.filesDir.absolutePath
        }
        val p = pb.start(); proc = p
        writePid("xray", p.pid().toLong())
        outJob = scope.launch { drain(p.inputStream, "xray/out") }
        errJob = scope.launch { drain(p.errorStream, "xray/err") }

        delay(1_200)
        if (!p.isAlive) {
            delPid("xray")
            throw IllegalStateException("Xray exited immediately (exit=${p.exitValue()}) — check config or port conflict")
        }
        Log.i(TAG, "Xray running pid=${p.pid()}")
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        outJob?.cancel(); outJob = null
        errJob?.cancel(); errJob = null
        proc?.let { p ->
            Log.i(TAG, "Stopping Xray")
            p.destroy()
            withTimeoutOrNull(4_000) { while (p.isAlive) delay(100) }
            if (p.isAlive) { p.destroyForcibly(); withTimeoutOrNull(2_000) { while (p.isAlive) delay(100) } }
        }
        proc = null; delPid("xray")
        runCatching { File(ctx.filesDir, "xray_config.json").delete() }
    }

    fun isRunning(): Boolean = proc?.isAlive == true

    private fun drain(s: InputStream, lbl: String) {
        try {
            s.bufferedReader(Charsets.UTF_8).use { r ->
                var l: String?
                while (r.readLine().also { l = it } != null && !Thread.currentThread().isInterrupted) {
                    val line = l!!
                    when { line.contains("ERROR",true) -> Log.e(TAG,"[$lbl] $line")
                           line.contains("warn", true) -> Log.w(TAG,"[$lbl] $line")
                           else                        -> Log.v(TAG,"[$lbl] $line") }
                }
            }
        } catch (e: Exception) {
            val m = e.message ?: ""
            if (!m.contains("closed",true) && !m.contains("EOF",true)) Log.w(TAG,"[$lbl] $m")
        }
    }

    private fun writePid(n: String, pid: Long) = runCatching { File(ctx.filesDir,"$n.pid").writeText(pid.toString()) }
    private fun delPid(n: String)              = runCatching { File(ctx.filesDir,"$n.pid").delete() }
}
