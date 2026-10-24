package com.alo.downloader.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YtDlpOutputTest {

    @Test
    fun `percent with known eta`() {
        assertEquals(42.5f, YtDlpOutput.percent("[download]  42.5% of   3.10MiB at  1.20MiB/s ETA 00:02")!!, 0.001f)
    }

    @Test
    fun `percent with unknown eta and fragments`() {
        assertEquals(7.0f, YtDlpOutput.percent("[download]   7.0% of ~  12.00MiB at  300.00KiB/s ETA Unknown (frag 1/20)")!!, 0.001f)
        assertEquals(100f, YtDlpOutput.percent("[download] 100% of    3.10MiB in 00:00:02 at 1.43MiB/s")!!, 0.001f)
    }

    @Test
    fun `no percent on other lines`() {
        assertNull(YtDlpOutput.percent("[download] Destination: /work/clip.mp4"))
        assertNull(YtDlpOutput.percent("[youtube] aDONt3ylEos: Downloading webpage"))
    }

    @Test
    fun stages() {
        assertEquals(EngineStage.EXTRACTING, YtDlpOutput.stage("[youtube] Extracting URL: https://youtube.com/shorts/aDONt3ylEos"))
        assertEquals(EngineStage.EXTRACTING, YtDlpOutput.stage("[youtube] aDONt3ylEos: Downloading player 0004de42-main"))
        assertEquals(EngineStage.SOLVING_CHALLENGES, YtDlpOutput.stage("[youtube] [jsc:quickjs] Solving JS challenges using quickjs"))
        assertEquals(EngineStage.DOWNLOADING, YtDlpOutput.stage("[download] Destination: /work/clip.f616.mp4"))
        assertEquals(EngineStage.MERGING, YtDlpOutput.stage("[Merger] Merging formats into \"/work/clip.mp4\""))
        assertEquals(EngineStage.POST_PROCESSING, YtDlpOutput.stage("[EmbedThumbnail] ffmpeg: Adding thumbnail"))
        assertNull(YtDlpOutput.stage("[info] aDONt3ylEos: Downloading 1 format(s): 616+251"))
        assertNull(YtDlpOutput.stage("Deleting original file /work/clip.f251.webm"))
    }
}
