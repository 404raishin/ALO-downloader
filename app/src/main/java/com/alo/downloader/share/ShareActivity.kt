package com.alo.downloader.share

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.alo.downloader.R
import com.alo.downloader.core.link.LinkExtractor
import com.alo.downloader.data.settings.AppSettings
import com.alo.downloader.data.settings.SettingsRepository
import com.alo.downloader.download.DownloadManager
import com.alo.downloader.ui.configure.ConfigureSheet
import com.alo.downloader.ui.home.clipboardText
import com.alo.downloader.ui.theme.AloTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Invisible activity behind "Share → ALO". By default it queues the link(s) with the
 * default options and closes, so the user stays in the app they shared from. With
 * "Choose format before downloading" on, it shows the format sheet on top instead.
 */
@AndroidEntryPoint
class ShareActivity : AppCompatActivity() {
    @Inject lateinit var downloadManager: DownloadManager
    @Inject lateinit var settingsRepository: SettingsRepository

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handled = savedInstanceState != null
        // The clipboard can only be read once the window has focus (Android 10+).
        if (intent?.action != ACTION_CLIPBOARD && !handled) {
            handled = true
            handle(SharedText.from(intent))
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !handled && intent?.action == ACTION_CLIPBOARD) {
            handled = true
            handle(clipboardText(this))
        }
    }

    private fun handle(text: String?) {
        val links = LinkExtractor.extractAll(text)
        if (links.isEmpty()) {
            Toast.makeText(this, R.string.no_link_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        lifecycleScope.launch {
            if (settingsRepository.current().configureBeforeDownload) {
                showSheet(links.joinToString("\n"))
            } else {
                queue(links)
            }
        }
    }

    private suspend fun queue(links: List<String>) {
        val ids = downloadManager.enqueue(links.map { DownloadManager.Request(it, priority = 1) })
        Toast.makeText(this, resources.getQuantityString(R.plurals.queued_count, ids.size, ids.size), Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val ACTION_CLIPBOARD = "com.alo.downloader.action.DOWNLOAD_CLIPBOARD"

        fun clipboardIntent(context: Context): Intent =
            Intent(context, ShareActivity::class.java).setAction(ACTION_CLIPBOARD)
    }

    private fun showSheet(text: String) {
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            AloTheme(settings) {
                ConfigureSheet(text = text, onDismiss = { finish() })
            }
        }
    }
}
