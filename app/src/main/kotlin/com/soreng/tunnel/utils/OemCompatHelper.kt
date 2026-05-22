package com.soreng.tunnel.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OEM-specific compatibility helpers.
 * Detects MIUI/HyperOS/EMUI/ColorOS and logs guidance for users.
 *
 * NOTE: We cannot programmatically disable OEM battery managers.
 * The correct approach is to prompt users to whitelist the app
 * in OEM settings, combined with WorkManager keepalive.
 */
@Singleton
class OemCompatHelper @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val TAG = "OemCompatHelper"

    enum class OemRom { STOCK, MIUI, EMUI, COLOROS, ONEPLUS, SAMSUNG, UNKNOWN }

    val rom: OemRom by lazy { detectRom() }

    private fun detectRom(): OemRom {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand        = Build.BRAND.lowercase()
        return when {
            getSystemProperty("ro.miui.ui.version.name").isNotBlank() -> OemRom.MIUI
            getSystemProperty("ro.build.version.emui").isNotBlank()   -> OemRom.EMUI
            getSystemProperty("ro.build.version.opporom").isNotBlank()-> OemRom.COLOROS
            brand == "oneplus"                                        -> OemRom.ONEPLUS
            manufacturer == "samsung"                                 -> OemRom.SAMSUNG
            else -> OemRom.UNKNOWN
        }
    }

    fun logRomInfo() {
        Log.i(TAG, "ROM: $rom | Manufacturer: ${Build.MANUFACTURER} | " +
            "Brand: ${Build.BRAND} | API: ${Build.VERSION.SDK_INT}")
        if (rom in listOf(OemRom.MIUI, OemRom.EMUI, OemRom.COLOROS)) {
            Log.w(TAG, "Aggressive OEM ROM detected ($rom). " +
                "User should disable battery optimization for Soren NG " +
                "in OEM battery settings to prevent background kills.")
        }
    }

    fun isAggressiveRom(): Boolean =
        rom in listOf(OemRom.MIUI, OemRom.EMUI, OemRom.COLOROS)

    /** Returns OEM-specific settings hint for the user. */
    fun getBatterySettingsHint(): String = when (rom) {
        OemRom.MIUI    -> "Settings → Apps → Soren NG → Battery saver → No restrictions\n" +
                          "Also: Security app → Battery → App battery saver → No restrictions"
        OemRom.EMUI    -> "Settings → Apps → Soren NG → Battery → Allow background activity"
        OemRom.COLOROS -> "Settings → Battery → More → App energy savings → Soren NG → No restriction"
        OemRom.SAMSUNG -> "Settings → Apps → Soren NG → Battery → Unrestricted"
        OemRom.ONEPLUS -> "Settings → Apps → Soren NG → Battery optimization → Don't optimize"
        else           -> "Settings → Apps → Soren NG → Battery → No restrictions"
    }

    private fun getSystemProperty(key: String): String = try {
        val c = Class.forName("android.os.SystemProperties")
        c.getMethod("get", String::class.java).invoke(null, key) as? String ?: ""
    } catch (_: Exception) { "" }
}
