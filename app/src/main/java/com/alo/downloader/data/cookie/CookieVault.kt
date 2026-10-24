package com.alo.downloader.data.cookie

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.alo.downloader.core.cookie.CookieEntry
import com.alo.downloader.core.cookie.NetscapeCookies
import com.alo.downloader.core.model.Platform
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class CookieSource { WEB_LOGIN, IMPORT }

data class StoredCookies(
    val platform: Platform,
    val cookies: List<CookieEntry>,
    val savedAt: Long,
    val source: CookieSource,
)

/**
 * Per-platform cookie storage backed by EncryptedSharedPreferences (AES-256, key in the
 * Android Keystore). Also holds other small secrets such as the Telegram API hash.
 */
@Singleton
class CookieVault @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs: SharedPreferences? by lazy {
        runCatching {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context,
                FILE,
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.onFailure { Log.e(TAG, "Encrypted storage unavailable; cookies will not be saved", it) }.getOrNull()
    }

    /** False when the device keystore is broken; the UI warns instead of storing plain text. */
    val isAvailable: Boolean get() = prefs != null

    fun get(platform: Platform): StoredCookies? {
        val p = prefs ?: return null
        val text = p.getString(key(platform), null) ?: return null
        val cookies = NetscapeCookies.parse(text)
        if (cookies.isEmpty()) return null
        return StoredCookies(
            platform = platform,
            cookies = cookies,
            savedAt = p.getLong(key(platform) + SAVED_AT, 0),
            source = runCatching { CookieSource.valueOf(p.getString(key(platform) + SOURCE, null) ?: "") }
                .getOrDefault(CookieSource.IMPORT),
        )
    }

    fun put(platform: Platform, cookies: List<CookieEntry>, source: CookieSource) {
        val p = prefs ?: return
        p.edit()
            .putString(key(platform), NetscapeCookies.serialize(cookies))
            .putLong(key(platform) + SAVED_AT, System.currentTimeMillis())
            .putString(key(platform) + SOURCE, source.name)
            .apply()
    }

    fun clear(platform: Platform) {
        prefs?.edit()?.remove(key(platform))?.remove(key(platform) + SAVED_AT)?.remove(key(platform) + SOURCE)?.apply()
    }

    fun getSecret(name: String): String? = prefs?.getString("secret.$name", null)

    fun putSecret(name: String, value: String?) {
        prefs?.edit()?.apply { if (value == null) remove("secret.$name") else putString("secret.$name", value) }?.apply()
    }

    private fun key(platform: Platform) = "cookies.${platform.id}"

    private companion object {
        const val FILE = "alo_cookies"
        const val TAG = "CookieVault"
        const val SAVED_AT = ".savedAt"
        const val SOURCE = ".source"
    }
}
