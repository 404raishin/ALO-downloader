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
                is TelegramLink.Channel -> downloadChannel(link, job, listener)
                is TelegramLink.Invite -> downloadInvite(job, listener)
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
            val files = fetchAll(client, embed.mediaUrls, "${link.username}_${link.messageId}", job, listener)
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

    /**
     * Recent posts of a channel: through TDLib when logged in (any size, documents too),
     * otherwise from the public web preview (photos and small videos of the last ~20 posts).
     */
    private suspend fun downloadChannel(link: TelegramLink.Channel, job: EngineJob, listener: ProgressListener): EngineResult {
        val limit = job.options.maxItems.takeIf { it > 0 } ?: DEFAULT_CHANNEL_ITEMS
        repository.readySession()?.let { session ->
            val messages = session.recentMediaMessages(session.chatIdForUsername(link.username), limit)
            return downloadMessages(session, messages, job, listener, title = "${link.username} (${messages.size})")
        }
        val client = clientFor(job)
        val request = Request.Builder().url(TelegramEmbedParser.channelPreviewUrl(link.username)).header("User-Agent", USER_AGENT).build()
        val embed = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            TelegramEmbedParser.parse(response.body?.string().orEmpty())
        }
        val urls = embed.mediaUrls.takeLast(limit)
        if (urls.isEmpty()) {
            return failure(FailureReason.LOGIN_REQUIRED, "The public preview of this channel shows no media. Log in to Telegram in ALO.")
        }
        val files = fetchAll(client, urls, link.username, job, listener)
        return EngineResult.Success(files, title = embed.channelTitle ?: link.username)
    }

    /** Invite links only work for chats the account already belongs to; ALO never joins chats. */
    private suspend fun downloadInvite(job: EngineJob, listener: ProgressListener): EngineResult {
        val session = repository.readySession()
            ?: return failure(FailureReason.LOGIN_REQUIRED, "Log in to Telegram in ALO to use invite links")
        val chatId = session.chatIdForInvite(job.url)
            ?: return failure(FailureReason.PRIVATE_CONTENT, "Join this chat in Telegram first; ALO does not join chats for you")
        val limit = job.options.maxItems.takeIf { it > 0 } ?: DEFAULT_CHANNEL_ITEMS
        return downloadMessages(session, session.recentMediaMessages(chatId, limit), job, listener, title = null)
    }

    /** Returns null when no logged-in TDLib session is available. */
    private suspend fun downloadWithTdlib(job: EngineJob, listener: ProgressListener): EngineResult? {
        val session = repository.readySession() ?: return null
        return downloadMessages(session, session.messagesForLink(job.url), job, listener, title = null)
    }

    private suspend fun downloadMessages(
        session: TdlibSession,
        messages: List<dev.g000sha256.tdl.dto.Message>,
        job: EngineJob,
        listener: ProgressListener,
        title: String?,
    ): EngineResult {
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
            val name = FileNames.sanitize(m.fileName)
            val existing = File(job.workDir, name)
            // Same file already copied before a pause: keep it instead of duplicating.
            if (existing.exists() && existing.length() == cached.length()) return@mapIndexed existing
            val target = uniqueFile(job.workDir, name)
            cached.copyTo(target)
            target
        }
        return EngineResult.Success(files, title = title ?: media.first().fileName.substringBeforeLast('.'))
    }

    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        var n = 2
        while (file.exists()) file = File(dir, "${name.substringBeforeLast('.')} ($n).${name.substringAfterLast('.', "bin")}").also { n++ }
        return file
    }

    private suspend fun fetchAll(client: OkHttpClient, urls: List<String>, base: String, job: EngineJob, listener: ProgressListener): List<File> =
        urls.mapIndexed { i, url ->
            val ext = URI(url).path.orEmpty().substringAfterLast('.', "").takeIf { it.length in 2..4 }
                ?: if (url.contains("video") || url.contains(".mp4")) "mp4" else "jpg"
            val name = if (urls.size == 1) "$base.$ext" else "${base}_${i + 1}.$ext"
            fetchFile(client, url, File(job.workDir, name)) { p ->
                listener.onProgress(EngineProgress(percent = (i + p) * 100f / urls.size, filesDone = i))
            }
        }

    private fun fetchEmbed(client: OkHttpClient, link: TelegramLink.PublicPost): TelegramEmbed {
        val request = Request.Builder().url(TelegramEmbedParser.embedUrl(link)).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return TelegramEmbedParser.parse(response.body?.string().orEmpty())
        }
    }

    /** Downloads [url] to [target], resuming a `.part` file left by a paused task (HTTP Range). */
    private suspend fun fetchFile(client: OkHttpClient, url: String, target: File, onProgress: (Float) -> Unit): File {
        if (target.exists() && target.length() > 0) return target
        target.parentFile?.mkdirs()
        val partial = File(target.path + ".part")
        val offset = partial.length()
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT)
            .apply { if (offset > 0) header("Range", "bytes=$offset-") }
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 416 && offset > 0) {
                // The part file already holds everything.
                if (!partial.renameTo(target)) throw IOException("Cannot write ${target.name}")
                return target
            }
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for media")
            val body = response.body ?: throw IOException("Empty response")
            val resumed = response.code == 206 && offset > 0
            val start = if (resumed) offset else 0L
            val total = body.contentLength().let { if (it > 0) it + start else -1L }
            body.byteStream().use { input ->
                java.io.FileOutputStream(partial, resumed).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = start
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
        const val DEFAULT_CHANNEL_ITEMS = 30
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
