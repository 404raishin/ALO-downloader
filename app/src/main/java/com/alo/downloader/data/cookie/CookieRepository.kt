package com.alo.downloader.data.cookie

import android.webkit.CookieManager
import com.alo.downloader.core.cookie.CookieEntry
import com.alo.downloader.core.cookie.NetscapeCookies
import com.alo.downloader.core.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class AccountStatus(
    val platform: Platform,
    val cookieCount: Int,
    val loggedIn: Boolean,
    val savedAt: Long?,
    val source: CookieSource?,
)

data class ImportResult(val total: Int, val perPlatform: Map<Platform, Int>)

@Singleton
class CookieRepository @Inject constructor(private val vault: CookieVault) {

    private val _accounts = MutableStateFlow<List<AccountStatus>>(emptyList())
    val accounts: StateFlow<List<AccountStatus>> = _accounts.asStateFlow()

    val encryptionAvailable: Boolean get() = vault.isAvailable

    suspend fun refreshStatus() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis() / 1000
        _accounts.value = Platform.cookiePlatforms.map { p ->
            val stored = vault.get(p)
            AccountStatus(
                platform = p,
                cookieCount = stored?.cookies?.size ?: 0,
                loggedIn = stored != null && NetscapeCookies.hasAuthCookie(p, stored.cookies, now),
                savedAt = stored?.savedAt,
                source = stored?.source,
            )
        }
    }

    fun cookiesFor(platform: Platform): List<CookieEntry> {
        val now = System.currentTimeMillis() / 1000
        return vault.get(platform)?.cookies.orEmpty().filterNot { it.isExpired(now) }
    }

    fun hasCookies(platform: Platform): Boolean = cookiesFor(platform).isNotEmpty()

    /**
     * Writes a cookies.txt for one task. Generic links get the cookies imported for unknown
     * domains; known platforms get their own slot only.
     */
    suspend fun writeCookieFile(platform: Platform, dir: File): File? = withContext(Dispatchers.IO) {
        val cookies = cookiesFor(platform)
        if (cookies.isEmpty()) return@withContext null
        dir.mkdirs()
        File(dir, "cookies.txt").apply { writeText(NetscapeCookies.serialize(cookies)) }
    }

    suspend fun importCookiesTxt(text: String): ImportResult = withContext(Dispatchers.IO) {
        val parsed = NetscapeCookies.parse(text)
        val groups = NetscapeCookies.groupByPlatform(parsed)
        groups.forEach { (platform, cookies) ->
            val merged = NetscapeCookies.merge(vault.get(platform)?.cookies.orEmpty(), cookies)
            vault.put(platform, merged, CookieSource.IMPORT)
        }
        refreshStatus()
        ImportResult(parsed.size, groups.mapValues { it.value.size })
    }

    /**
     * Copies the cookies the built-in WebView holds for [platform] into the vault.
     * Returns true when an authentication cookie was found (i.e. the user is logged in).
     */
    suspend fun captureFromWebView(platform: Platform): Boolean = withContext(Dispatchers.Main) {
        val manager = CookieManager.getInstance()
        manager.flush()
        val now = System.currentTimeMillis() / 1000
        val captured = hostsFor(platform).flatMap { host ->
            NetscapeCookies.fromHeader(manager.getCookie("https://$host/"), host, now)
        }
        // The same cookie shows up for www. and the bare domain; keep one copy.
        val unique = NetscapeCookies.merge(emptyList(), captured)
        val loggedIn = NetscapeCookies.hasAuthCookie(platform, unique, now)
        if (unique.isNotEmpty()) {
            withContext(Dispatchers.IO) { vault.put(platform, unique, CookieSource.WEB_LOGIN) }
        }
        refreshStatus()
        loggedIn
    }

    /** True when the WebView currently holds an auth cookie for [platform]. */
    fun webViewLoggedIn(platform: Platform): Boolean {
        val manager = CookieManager.getInstance()
        val now = System.currentTimeMillis() / 1000
        val cookies = hostsFor(platform).flatMap { NetscapeCookies.fromHeader(manager.getCookie("https://$it/"), it, now) }
        return platform.authCookies.isNotEmpty() && NetscapeCookies.hasAuthCookie(platform, cookies, now)
    }

    /** Forgets the stored cookies and logs the WebView out of the platform. */
    suspend fun clear(platform: Platform) {
        withContext(Dispatchers.IO) { vault.clear(platform) }
        withContext(Dispatchers.Main) { expireWebViewCookies(platform) }
        refreshStatus()
    }

    private suspend fun expireWebViewCookies(platform: Platform) {
        val manager = CookieManager.getInstance()
        for (host in hostsFor(platform)) {
            val header = manager.getCookie("https://$host/") ?: continue
            header.split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }.forEach { name ->
                listOf(host, ".${host.removePrefix("www.")}").forEach { domain ->
                    suspendCancellableCoroutine { cont ->
                        manager.setCookie("https://$host/", "$name=; Max-Age=0; Domain=$domain; Path=/") { cont.resume(Unit) }
                    }
                }
            }
        }
        manager.flush()
    }

    private fun hostsFor(platform: Platform): List<String> = platform.domains.flatMap { d ->
        if (d.count { it == '.' } >= 2) listOf(d) else listOf(d, "www.$d", "m.$d")
    }.distinct()
}
