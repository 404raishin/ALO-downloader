package com.alo.downloader.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.AuthorizationState
import dev.g000sha256.tdl.dto.AuthorizationStateClosed
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import dev.g000sha256.tdl.dto.AuthorizationStateWaitCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitOtherDeviceConfirmation
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPassword
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPhoneNumber
import dev.g000sha256.tdl.dto.AuthorizationStateWaitTdlibParameters
import dev.g000sha256.tdl.dto.File as TdFile
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVideoNote
import dev.g000sha256.tdl.dto.MessageVoiceNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** Where the TDLib login flow currently is, independent of TDLib's own classes. */
sealed interface TelegramAuth {
    data object NotConfigured : TelegramAuth
    data object Starting : TelegramAuth
    data object WaitPhone : TelegramAuth
    data class WaitCode(val phone: String?) : TelegramAuth
    data class WaitPassword(val hint: String?) : TelegramAuth
    data class WaitOtherDevice(val link: String) : TelegramAuth
    data object Ready : TelegramAuth
    data class Unsupported(val state: String) : TelegramAuth
    data object Closed : TelegramAuth
}

class TelegramException(val code: Int, message: String) : Exception(message)

/** A downloadable file inside a Telegram message. */
data class TelegramMedia(val fileId: Int, val fileName: String, val size: Long)

/**
 * Thin coroutine wrapper around one TDLib client: login flow, resolving message links and
 * downloading message media. The user supplies their own API id/hash from my.telegram.org.
 */
class TdlibSession(
    private val databaseDir: File,
    private val apiId: Int,
    private val apiHash: String,
    private val deviceModel: String,
    private val systemVersion: String,
    private val appVersion: String,
    private val language: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client: TdlClient = TdlClient.create()
    private val _auth = MutableStateFlow<TelegramAuth>(TelegramAuth.Starting)
    val auth: StateFlow<TelegramAuth> = _auth.asStateFlow()

    init {
        scope.launch {
            client.authorizationStateUpdates.collect { update -> onState(update.authorizationState) }
        }
        scope.launch {
            // Updates only start flowing after the first request.
            client.getAuthorizationState().getOrNull()?.let { onState(it) }
        }
    }

    private suspend fun onState(state: AuthorizationState) {
        _auth.value = when (state) {
            is AuthorizationStateWaitTdlibParameters -> {
                client.setTdlibParameters(
                    false,
                    databaseDir.absolutePath,
                    File(databaseDir, "files").absolutePath,
                    ByteArray(0),
                    true,
                    true,
                    true,
                    false,
                    apiId,
                    apiHash,
                    language,
                    deviceModel,
                    systemVersion,
                    appVersion,
                ).getOrThrow()
                TelegramAuth.Starting
            }
            is AuthorizationStateWaitPhoneNumber -> TelegramAuth.WaitPhone
            is AuthorizationStateWaitCode -> TelegramAuth.WaitCode(state.codeInfo.phoneNumber)
            is AuthorizationStateWaitPassword -> TelegramAuth.WaitPassword(state.passwordHint.takeIf { it.isNotBlank() })
            is AuthorizationStateWaitOtherDeviceConfirmation -> TelegramAuth.WaitOtherDevice(state.link)
            is AuthorizationStateReady -> TelegramAuth.Ready
            is AuthorizationStateClosed -> TelegramAuth.Closed
            else -> TelegramAuth.Unsupported(state::class.simpleName ?: "unknown")
        }
    }

    suspend fun sendPhone(phone: String) {
        client.setAuthenticationPhoneNumber(phone, null).getOrThrow()
    }

    suspend fun sendCode(code: String) {
        client.checkAuthenticationCode(code).getOrThrow()
    }

    suspend fun sendPassword(password: String) {
        client.checkAuthenticationPassword(password).getOrThrow()
    }

    suspend fun logOut() {
        client.logOut().getOrThrow()
    }

    suspend fun close() {
        runCatching { client.close() }
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    suspend fun awaitReady(timeoutMs: Long = 15_000): Boolean =
        withTimeoutOrNull(timeoutMs) { auth.filter { it is TelegramAuth.Ready || it is TelegramAuth.Closed }.first() } is TelegramAuth.Ready

    /** Resolves a t.me link to its message (and the rest of its album, if any). */
    suspend fun messagesForLink(url: String): List<Message> {
        val info = client.getMessageLinkInfo(url).getOrThrow()
        val message = info.message ?: throw TelegramException(404, "Message not found or not accessible")
        if (message.mediaAlbumId == 0L) return listOf(message)
        val around = client.getChatHistory(message.chatId, message.id, -10, 20, false).getOrNull()?.messages.orEmpty().filterNotNull()
        val album = around.filter { it.mediaAlbumId == message.mediaAlbumId }.sortedBy { it.id }
        return album.ifEmpty { listOf(message) }
    }

    suspend fun publicMessage(username: String, messageId: Long): Message {
        val chat = client.searchPublicChat(username).getOrThrow()
        // TDLib message ids are server ids shifted by 20 bits.
        return client.getMessage(chat.id, messageId shl 20).getOrThrow()
    }

    fun mediaOf(message: Message): List<TelegramMedia> {
        val base = "telegram_${message.chatId}_${message.id shr 20}"
        return when (val c = message.content) {
            is MessageVideo -> listOf(media(c.video.video, c.video.fileName.ifBlank { "$base.mp4" }))
            is MessagePhoto -> listOfNotNull(c.photo.sizes.maxByOrNull { it.width * it.height }?.let { media(it.photo, "$base.jpg") })
            is MessageDocument -> listOf(media(c.document.document, c.document.fileName.ifBlank { base }))
            is MessageAudio -> listOf(media(c.audio.audio, c.audio.fileName.ifBlank { "$base.mp3" }))
            is MessageAnimation -> listOf(media(c.animation.animation, c.animation.fileName.ifBlank { "$base.mp4" }))
            is MessageVoiceNote -> listOf(media(c.voiceNote.voice, "$base.ogg"))
            is MessageVideoNote -> listOf(media(c.videoNote.video, "$base.mp4"))
            else -> emptyList()
        }
    }

    private fun media(file: TdFile, name: String) = TelegramMedia(file.id, name, file.size.takeIf { it > 0 } ?: file.expectedSize)

    /**
     * Downloads a file through TDLib and reports progress (0..1). Returns the local path of
     * the finished file inside TDLib's cache.
     */
    suspend fun download(fileId: Int, onProgress: (Float) -> Unit): File {
        val progressJob = scope.launch {
            client.fileUpdates.filter { it.file.id == fileId }.collect { u ->
                val total = u.file.size.takeIf { it > 0 } ?: u.file.expectedSize
                if (total > 0) onProgress(u.file.local.downloadedSize.toFloat() / total)
            }
        }
        try {
            val file = client.downloadFile(fileId, 1, 0, 0, true).getOrThrow()
            if (!file.local.isDownloadingCompleted) throw TelegramException(500, "Download incomplete")
            return File(file.local.path)
        } finally {
            progressJob.cancel()
        }
    }

    suspend fun cancelDownload(fileId: Int) {
        client.cancelDownloadFile(fileId, false)
    }

    private fun <T> TdlResult<T>.getOrThrow(): T = when (this) {
        is TdlResult.Success -> result
        is TdlResult.Failure -> throw TelegramException(code, message)
    }

    private fun <T> TdlResult<T>.getOrNull(): T? = (this as? TdlResult.Success)?.result
}
