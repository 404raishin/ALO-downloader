package com.alo.downloader.download

import com.alo.downloader.core.model.Platform
import com.alo.downloader.data.cookie.CookieRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies per-task credentials to engines: a short-lived cookies.txt decrypted from the
 * vault, deleted again as soon as the task ends.
 */
@Singleton
class TaskCredentials @Inject constructor(private val cookies: CookieRepository) {
    class Prepared(val cookieFile: File?) {
        fun cleanup() {
            cookieFile?.delete()
        }
    }

    suspend fun prepare(platform: Platform, dir: File): Prepared = Prepared(cookies.writeCookieFile(platform, dir))
}
