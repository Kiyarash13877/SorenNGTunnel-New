package com.soreng.tunnel.storage

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM encrypted key-value store for sensitive config data.
 * Falls back to plain SharedPreferences if Keystore is unavailable (logs warning).
 */
@Singleton
class SecureConfigStore @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val TAG = "SecureConfigStore"

    private val prefs by lazy {
        try {
            val mk = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setRequestStrongBoxBacked(false)
                .setUserAuthenticationRequired(false)
                .build()
            EncryptedSharedPreferences.create(ctx, "soren_secure",
                mk, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable: ${e.message} — using fallback")
            ctx.getSharedPreferences("soren_secure_fb", Context.MODE_PRIVATE)
        }
    }

    fun put(key: String, value: String)     = runCatching { prefs.edit().putString(key,value).apply() }
    fun get(key: String, def: String?=null) = runCatching { prefs.getString(key,def) }.getOrDefault(def)
    fun remove(key: String)                 = runCatching { prefs.edit().remove(key).apply() }
    fun contains(key: String): Boolean      = runCatching { prefs.contains(key) }.getOrDefault(false)
    fun clearAll()                          = runCatching { prefs.edit().clear().apply() }
}
