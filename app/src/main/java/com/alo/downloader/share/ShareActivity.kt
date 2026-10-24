package com.alo.downloader.share

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.alo.downloader.R
import com.alo.downloader.core.link.LinkExtractor
import com.alo.downloader.download.DownloadManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Invisible activity behind "Share → ALO". It finds the link(s), queues them with the
 * default options and closes again, so the user stays in the app they shared from.
 */
@AndroidEntryPoint
class ShareActivity : AppCompatActivity() {
    @Inject lateinit var downloadManager: DownloadManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(SharedText.from(intent))
    }

    private fun handle(text: String?) {
        val links = LinkExtractor.extractAll(text)
        if (links.isEmpty()) {
            Toast.makeText(this, R.string.no_link_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        lifecycleScope.launch {
            val ids = downloadManager.enqueue(links.map { DownloadManager.Request(it, priority = 1) })
            Toast.makeText(
                this@ShareActivity,
                resources.getQuantityString(R.plurals.queued_count, ids.size, ids.size),
                Toast.LENGTH_SHORT,
            ).show()
            finish()
        }
    }
}
