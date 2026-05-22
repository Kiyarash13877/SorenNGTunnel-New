package com.soreng.tunnel.vpn

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scans /proc for zombie native daemons owned by this UID and kills them.
 * Runs at startup to clean up from previous crash/kill.
 * Also manages PID files for process tracking.
 */
@Singleton
class ProcessGuard @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val TAG = "ProcessGuard"

    suspend fun killZombies() = withContext(Dispatchers.IO) {
        val myUid  = android.os.Process.myUid()
        val binDir = File(ctx.filesDir, "bin").absolutePath
        val targets = listOf("xray","tun2socks","psiphon")

        try {
            File("/proc").listFiles { f -> f.name.all { it.isDigit() } }
                ?.forEach { pidDir ->
                    try {
                        val pid = pidDir.name.toIntOrNull() ?: return@forEach
                        // Check UID matches ours
                        val status = File(pidDir, "status")
                        if (!status.exists()) return@forEach
                        val uidLine = status.readLines().find { it.startsWith("Uid:") } ?: return@forEach
                        val uid = uidLine.split("\t").getOrNull(1)?.trim()?.toIntOrNull() ?: return@forEach
                        if (uid != myUid) return@forEach
                        // Check exe path is one of our binaries
                        val exe = try { File(pidDir,"exe").canonicalPath } catch (_:Exception) { return@forEach }
                        if (targets.any { name -> exe.endsWith("/$name") && exe.startsWith(binDir) }) {
                            Log.w(TAG,"Killing zombie: pid=$pid exe=$exe")
                            android.os.Process.killProcess(pid)
                        }
                    } catch (_: Exception) { /* permission denied for other pids — expected */ }
                }
        } catch (e: Exception) {
            Log.w(TAG,"killZombies: ${e.message}")
        }

        // Also kill any processes listed in PID files
        for (name in targets) {
            val pidFile = File(ctx.filesDir, "$name.pid")
            if (pidFile.exists()) {
                val pid = pidFile.readText().trim().toIntOrNull()
                if (pid != null && pid > 0) {
                    try {
                        android.os.Process.killProcess(pid)
                        Log.i(TAG,"Killed pid-file process: $name pid=$pid")
                    } catch (_: Exception) {}
                }
                pidFile.delete()
            }
        }
    }

    suspend fun waitForDeath(proc: Process, timeoutMs: Long = 3_000): Boolean =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs) { while (proc.isAlive) kotlinx.coroutines.delay(100); true } ?: false
        }

    fun writePid(name: String, pid: Long) =
        runCatching { File(ctx.filesDir,"$name.pid").writeText(pid.toString()) }
    fun deletePid(name: String) =
        runCatching { File(ctx.filesDir,"$name.pid").delete() }
    fun readPid(name: String): Long? =
        runCatching { File(ctx.filesDir,"$name.pid").readText().trim().toLongOrNull() }.getOrNull()
}
