package com.alo.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.alo.downloader.core.engine.EngineJob
import com.alo.downloader.core.model.DownloadOptions
import com.alo.downloader.core.model.Platform
import java.io.File
import java.util.UUID

/**
 * Small media files committed under `testdata/`. CI passes the raw URL of the exact commit
 * being tested (`-Pandroid.testInstrumentationRunnerArguments.fixtureBase=…`).
 */
object Fixtures {
    private const val DEFAULT_BASE = "https://raw.githubusercontent.com/404raishin/ALO-downloader/main/testdata"

    val base: String
        get() = InstrumentationRegistry.getArguments().getString("fixtureBase")?.trimEnd('/') ?: DEFAULT_BASE

    val clipUrl get() = "$base/alo-test-clip.mp4"
    val imageUrl get() = "$base/alo-test-image.png"

    val context: Context get() = ApplicationProvider.getApplicationContext()

    fun job(url: String, options: DownloadOptions = quietOptions(), platform: Platform = Platform.GENERIC): EngineJob {
        val id = "test-" + UUID.randomUUID()
        return EngineJob(
            taskId = id,
            url = url,
            platform = platform,
            workDir = File(context.cacheDir, id).apply { mkdirs() },
            options = options,
        )
    }

    /** Options without post-processing that needs remote metadata the fixtures do not have. */
    fun quietOptions() = DownloadOptions(
        embedThumbnail = false,
        embedMetadata = false,
        embedChapters = false,
        useArchive = false,
        container = com.alo.downloader.core.model.VideoContainer.DEFAULT,
    )
}
