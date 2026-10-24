package com.alo.downloader.download

import com.alo.downloader.core.model.Platform
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Supplies per-task credentials (cookies.txt) to engines. Cookies arrive in phase 2. */
@Singleton
class TaskCredentials @Inject constructor() {
    class Prepared(val cookieFile: File?) {
        fun cleanup() {
            cookieFile?.delete()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun prepare(platform: Platform, dir: File): Prepared = Prepared(null)
}
