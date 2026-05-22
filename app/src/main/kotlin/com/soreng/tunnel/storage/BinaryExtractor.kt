package com.soreng.tunnel.storage

import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts native binaries from assets to filesDir/bin/.
 *
 * Binaries required:
 *   xray        — https://github.com/XTLS/Xray-core
 *   tun2socks   — https://github.com/xjasonlyu/tun2socks
 *   psiphon     — https://github.com/Psiphon-Inc/psiphon-android
 *
 * NEVER creates placeholder/stub binaries.
 * Missing binary → logged as error; managers will throw on start.
 */
@Singleton
class BinaryExtractor @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val TAG = "BinaryExtractor"
    private val MIN_SIZE = 512L

    suspend fun extractAll() = withContext(Dispatchers.IO) {
        val binDir = File(ctx.filesDir, "bin").also { it.mkdirs() }
        val abi    = detectAbi()
        Log.i(TAG, "Extracting binaries for ABI=$abi")

        for (name in listOf("xray","tun2socks","psiphon")) {
            val dest = File(binDir, name)
            if (!dest.exists() || dest.length() < MIN_SIZE || !dest.canExecute())
                extractAsset("bin/$abi/$name", dest)

            when {
                !dest.exists()           -> Log.e(TAG, "MISSING: $name — build from source and place in assets/bin/$abi/")
                dest.length() < MIN_SIZE -> Log.e(TAG, "INVALID: $name is ${dest.length()} bytes — likely a stub. Replace with real binary.")
                else -> {
                    dest.setExecutable(true, false); dest.setReadable(true, false)
                    Log.i(TAG, "OK: $name (${dest.length()} bytes, exec=${dest.canExecute()})")
                }
            }
        }
    }

    fun isReady(name: String): Boolean {
        val f = File(ctx.filesDir, "bin/$name")
        return f.exists() && f.length() >= MIN_SIZE && f.canExecute()
    }

    private fun detectAbi(): String {
        val abis = Build.SUPPORTED_ABIS.toList()
        return when {
            "arm64-v8a"   in abis -> "arm64-v8a"
            "x86_64"      in abis -> "x86_64"
            "armeabi-v7a" in abis -> "armeabi-v7a"
            "x86"         in abis -> "x86"
            else -> abis.firstOrNull() ?: "arm64-v8a"
        }
    }

    private fun extractAsset(assetPath: String, dest: File) {
        try {
            ctx.assets.open(assetPath).use { input ->
                val tmp = File(dest.parent, "${dest.name}.tmp")
                tmp.outputStream().use { out -> input.copyTo(out, bufferSize = 65_536) }
                if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite=true); tmp.delete() }
            }
            Log.i(TAG, "Extracted $assetPath → ${dest.absolutePath} (${dest.length()} bytes)")
        } catch (e: Exception) {
            Log.w(TAG, "Cannot extract $assetPath: ${e.message}")
        }
    }
}
