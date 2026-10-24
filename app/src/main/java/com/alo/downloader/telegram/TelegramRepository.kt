package com.alo.downloader.telegram

import android.content.Context
import android.os.Build
import com.alo.downloader.BuildConfig
import com.alo.downloader.data.cookie.CookieVault
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the TDLib session. The user brings their own API id/hash (my.telegram.org), which
 * is kept in the encrypted vault together with the cookies.
 */
@Singleton
class TelegramRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: CookieVault,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var session: TdlibSession? = null
    private val _auth = MutableStateFlow<TelegramAuth>(TelegramAuth.NotConfigured)
    val auth: StateFlow<TelegramAuth> = _auth.asStateFlow()

    val dataDir: File get() = File(context.filesDir, "tdlib")

    val apiId: String? get() = vault.getSecret(KEY_API_ID)
    val isConfigured: Boolean get() = !apiId.isNullOrBlank() && !vault.getSecret(KEY_API_HASH).isNullOrBlank()

    /** Starts TDLib if credentials exist; cheap when it is already running. */
    suspend fun ensureSession(): TdlibSession? = mutex.withLock {
        session?.let { return@withLock it }
        val id = vault.getSecret(KEY_API_ID)?.toIntOrNull() ?: return@withLock null
        val hash = vault.getSecret(KEY_API_HASH)?.takeIf { it.isNotBlank() } ?: return@withLock null
        val created = runCatching {
            TdlibSession(
                databaseDir = dataDir.apply { mkdirs() },
                apiId = id,
                apiHash = hash,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                systemVersion = "Android ${Build.VERSION.RELEASE}",
                appVersion = "ALO ${BuildConfig.VERSION_NAME}",
                language = Locale.getDefault().language.ifBlank { "en" },
            )
        }.getOrElse {
            _auth.value = TelegramAuth.Unsupported(it.message ?: "TDLib failed to start")
            return@withLock null
        }
        session = created
        scope.launch { created.auth.collectLatest { _auth.value = it } }
        created
    }

    suspend fun configure(apiId: String, apiHash: String) {
        vault.putSecret(KEY_API_ID, apiId.trim())
        vault.putSecret(KEY_API_HASH, apiHash.trim())
        restart()
        ensureSession()
    }

    suspend fun sendPhone(phone: String) = ensureSession()?.sendPhone(phone)
    suspend fun sendCode(code: String) = ensureSession()?.sendCode(code)
    suspend fun sendPassword(password: String) = ensureSession()?.sendPassword(password)

    /** Ready session or null when the user is not logged in. */
    suspend fun readySession(): TdlibSession? {
        if (!isConfigured) return null
        val s = ensureSession() ?: return null
        return if (s.awaitReady()) s else null
    }

    suspend fun logOut() {
        runCatching { session?.logOut() }
        restart()
        dataDir.deleteRecursively()
        _auth.value = if (isConfigured) TelegramAuth.Starting else TelegramAuth.NotConfigured
        if (isConfigured) ensureSession()
    }

    /** Forgets API credentials and the local session. */
    suspend fun reset() {
        runCatching { session?.logOut() }
        restart()
        dataDir.deleteRecursively()
        vault.putSecret(KEY_API_ID, null)
        vault.putSecret(KEY_API_HASH, null)
        _auth.value = TelegramAuth.NotConfigured
    }

    private suspend fun restart() = mutex.withLock {
        session?.close()
        session = null
    }

    private companion object {
        const val KEY_API_ID = "telegram.api_id"
        const val KEY_API_HASH = "telegram.api_hash"
    }
}
