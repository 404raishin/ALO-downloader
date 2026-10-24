package com.alo.downloader.core.telegram

import java.net.URI

/** A parsed t.me / telegram.me link. */
sealed interface TelegramLink {
    /** Post in a public channel or group: t.me/name/123 */
    data class PublicPost(val username: String, val messageId: Long, val single: Boolean) : TelegramLink

    /** Post in a private channel/supergroup: t.me/c/1234567890/123 (needs a logged-in account). */
    data class PrivatePost(val chatId: Long, val messageId: Long) : TelegramLink

    /** A whole public channel: t.me/name */
    data class Channel(val username: String) : TelegramLink

    /** Invite link: t.me/+hash or t.me/joinchat/hash */
    data class Invite(val hash: String) : TelegramLink

    companion object {
        private val hosts = setOf("t.me", "telegram.me", "telegram.dog")
        private val reserved = setOf("s", "c", "joinchat", "addstickers", "addemoji", "share", "proxy", "socks", "login", "iv", "setlanguage", "bg", "addtheme", "invoice", "boost")
        private val username = Regex("^[A-Za-z][A-Za-z0-9_]{3,31}$")

        fun parse(url: String): TelegramLink? {
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
            if (host !in hosts) return null
            var segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
            val query = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.map { it.substringBefore('=') }
            val single = "single" in query
            if (segments.firstOrNull() == "s") segments = segments.drop(1) // web preview of a channel
            val first = segments.firstOrNull() ?: return null

            if (first.startsWith("+")) return Invite(first.removePrefix("+"))
            if (first == "joinchat") return segments.getOrNull(1)?.let(::Invite)
            if (first == "c") {
                val internal = segments.getOrNull(1)?.toLongOrNull() ?: return null
                // The message id is the last segment; a topic id may sit in between.
                val message = segments.drop(2).lastOrNull()?.toLongOrNull() ?: return null
                return PrivatePost(toChatId(internal), message)
            }
            if (first.lowercase() in reserved || !username.matches(first)) return null
            val message = segments.drop(1).lastOrNull()?.toLongOrNull()
            return if (message != null) PublicPost(first, message, single) else Channel(first)
        }

        /** Converts the id in t.me/c/<id>/… to the TDLib supergroup chat id (-100…). */
        fun toChatId(internal: Long): Long = -(1_000_000_000_000L + internal)
    }
}
