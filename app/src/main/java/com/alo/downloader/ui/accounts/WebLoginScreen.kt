package com.alo.downloader.ui.accounts

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alo.downloader.R
import com.alo.downloader.core.model.Platform

/**
 * Built-in browser for logging in to a platform. Once the platform's session cookie shows
 * up, the cookies are copied into the encrypted vault and used for downloads.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginScreen(platform: Platform, onDone: () -> Unit, viewModel: AccountsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var detected by remember { mutableStateOf(false) }
    val warningShown by viewModel.instagramWarningShown.collectAsStateWithLifecycle()
    var showWarningDialog by remember { mutableStateOf(true) }

    val save = {
        viewModel.capture(platform) { loggedIn ->
            Toast.makeText(
                context,
                if (loggedIn) R.string.login_saved else R.string.login_saved_partial,
                Toast.LENGTH_SHORT,
            ).show()
            onDone()
        }
    }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_login_platform, platform.displayName)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.reload)) }
                    TextButton(onClick = save) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (platform == Platform.INSTAGRAM) InstagramWarning(Modifier.padding(12.dp))
            if (detected) {
                Button(onClick = save, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(stringResource(R.string.login_detected_save))
                }
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        // Some sign-in pages refuse embedded browsers that announce themselves.
                        settings.userAgentString = settings.userAgentString
                            .replace("; wv", "")
                            .replace(Regex("""Version/\d+\.\d+ """), "")
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                if (!detected && viewModel.webViewLoggedIn(platform)) detected = true
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val scheme = request?.url?.scheme
                                return scheme != "http" && scheme != "https"
                            }
                        }
                        loadUrl(platform.loginUrl ?: "about:blank")
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }

    if (platform == Platform.INSTAGRAM && !warningShown && showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false; viewModel.markInstagramWarningShown() },
            title = { Text(stringResource(R.string.instagram_warning_title)) },
            text = { Text(stringResource(R.string.instagram_warning)) },
            confirmButton = {
                TextButton(onClick = { showWarningDialog = false; viewModel.markInstagramWarningShown() }) {
                    Text(stringResource(R.string.understood))
                }
            },
        )
    }
}
