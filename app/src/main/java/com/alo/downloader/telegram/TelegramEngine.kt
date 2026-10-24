package com.alo.downloader.telegram

import com.alo.downloader.core.engine.DownloadEngine
import com.alo.downloader.core.engine.EngineJob
import com.alo.downloader.core.engine.EngineProgress
import com.alo.downloader.core.engine.EngineResult
import com.alo.downloader.core.engine.ErrorClassifier
import com.alo.downloader.core.engine.ProgressListener
import com.alo.downloader.core.model.DownloadError
import com.alo.downloader.core.model.EngineType
import com.alo.downloader.core.model.FailureReason
import com.alo.downloader.core.telegram.TelegramEmbed
import com.alo.downloader.core.telegram.TelegramEmbedParser
import com.alo.downloader.core.telegram.TelegramLink
import com.alo.downloader.core.util.FileNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Telegram downloads. Public posts are read from Telegram's web preview without any
 * account; private channels/groups and files the preview does not expose go through
 * TDLib with the user's own login.
 */
@Singleton
class TelegramEngine @Inject constructor(
    private val http: OkHttpClient,
    private val repository: TelegramRepository,
) : DownloadEngine {
    override val type = EngineType.TELEGRAM

    override suspend fun download(job: EngineJob, listener: ProgressListener): EngineResult = withContext(Dispatchers.IO) {
        val link = TelegramLink.parse(job.url)
            ?: return@withContext failure(FailureReason.UNSUPPORTED_URL, "Not a Telegram post link")
        try {
            when (link) {
                is TelegramLink.PublicPost -> downloadPublic(link, job, listener)
                is TelegramLink.PrivatePost -> downloadWithTdlib(job, listener)
                    ?: failure(FailureReason.LOGIN_REQUIRED, "Log in to Telegram in ALO to download from private chats")
                // TODO: download a whole channel (TDLib getChatHistory paging) and join via invite links.
                is TelegramLink.Channel, is TelegramLink.Invite ->
                    failure(FailureReason.UNSUPPORTED_URL, "Share a link to a single post (t.me/channel/123)")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TelegramException) {
            EngineResult.Failure(ErrorClassifier.toError("${e.code} ${e.message}", type).let {
                if (e.code == 401) it.copy(reason = FailureReason.LOGIN_REQUIRED) else it
            })
        } catch (e: Exception) {
            EngineResult.Failure(ErrorClassifier.toError(e.message ?: e.toString(), type))
        }
    }

    private suspend fun downloadPublic(link: TelegramLink.PublicPost, job: EngineJob, listener: ProgressListener): EngineResult {
        val client = clientFor(job)
        val embed = runCatching { fetchEmbed(client, link) }.getOrNull()
        if (embed != null && embed.mediaUrls.isNotEmpty()) {
            val base = "${link.username}_${link.messageId}"
            val files = embed.mediaUrls.mapIndexed { i, url ->
                val ext = URI(url).path.orEmpty().substringAfterLast('.', "").takeIf { it.length in 2..4 } ?: if (url.contains("video")) "mp4" else "jpg"
                val name = if (embed.mediaUrls.size == 1) "$base.$ext" else "${base}_${i + 1}.$ext"
                fetchFile(client, url, File(job.workDir, name)) { p ->
                    listener.onProgress(EngineProgress(percent = (i + p) * 100f / embed.mediaUrls.size, filesDone = i, title = embed.text?.take(80)))
                }
            }
            return EngineResult.Success(files, title = titleOf(embed, link))
        }
        // The preview hides documents and large videos; TDLib can still get them.
        downloadWithTdlib(job, listener)?.let { return it }
        return if (embed?.needsApp == true || embed == null) {
            failure(FailureReason.LOGIN_REQUIRED, "This media is only available through a Telegram account. Log in to Telegram in ALO.")
        } else {
            failure(FailureReason.NO_MEDIA, "No photo or video in this post")
        }
    }

    /** Returns null when no logged-in TDLib session is available. */
    private suspend fun downloadWithTdlib(job: EngineJob, listener: ProgressListener): EngineResult? {
        val session = repository.readySession() ?: return null
        val messages = session.messagesForLink(job.url)
        val media = messages.flatMap { session.mediaOf(it) }
        if (media.isEmpty()) return failure(FailureReason.NO_MEDIA, "No downloadable media in this message")
        val files = media.mapIndexed { i, m ->
            coroutineContext.ensureActive()
            val cached = try {
                session.download(m.fileId) { p ->
                    listener.onProgress(EngineProgress(percent = (i + p) * 100f / media.size, filesDone = i))
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { session.cancelDownload(m.fileId) }
                throw e
            }
            val target = File(job.workDir, FileNames.sanitize(m.fileName))
            cached.copyTo(target, overwrite = true)
            target
        }
        return EngineResult.Success(files, title = media.first().fileName.substringBeforeLast('.'))
    }

    private fun fetchEmbed(client: OkHttpClient, link: TelegramLink.PublicPost): TelegramEmbed {
        val request = Request.Builder().url(TelegramEmbedParser.embedUrl(link)).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return TelegramEmbedParser.parse(response.body?.string().orEmpty())
        }
    }

    private suspend fun fetchFile(client: OkHttpClient, url: String, target: File, onProgress: (Float) -> Unit): File {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for media")
            val body = response.body ?: throw IOException("Empty response")
            val total = body.contentLength()
            target.parentFile?.mkdirs()
            val partial = File(target.path + ".part")
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) onProgress(read.toFloat() / total)
                    }
                }
            }
            if (!partial.renameTo(target)) throw IOException("Cannot write ${target.name}")
            return target
        }
    }

    private fun clientFor(job: EngineJob): OkHttpClient {
        val proxy = job.options.proxy?.let(::toProxy) ?: return http
        return http.newBuilder().proxy(proxy).build()
    }

    private fun toProxy(url: String): Proxy? = runCatching {
        val uri = URI(url)
        val type = if (uri.scheme.startsWith("socks")) Proxy.Type.SOCKS else Proxy.Type.HTTP
        Proxy(type, InetSocketAddress.createUnresolved(uri.host, uri.port))
    }.getOrNull()

    private fun titleOf(embed: TelegramEmbed, link: TelegramLink.PublicPost): String =
        embed.text?.lineSequence()?.firstOrNull()?.take(80) ?: "${embed.channelTitle ?: link.username} #${link.messageId}"

    private fun failure(reason: FailureReason, message: String) = EngineResult.Failure(DownloadError(reason, message, type))

    override fun cancel(taskId: String) = Unit // cancellation follows the coroutine

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
