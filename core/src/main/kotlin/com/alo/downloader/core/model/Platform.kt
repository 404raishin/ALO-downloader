package com.alo.downloader.core.model

/**
 * A site ALO knows about. Each platform has its own download folder, its own cookie
 * slot and (optionally) a web login page used to capture cookies.
 */
enum class Platform(
    val id: String,
    val displayName: String,
    /** Sub folder under `Download/ALO/`. */
    val folderName: String,
    /** Registrable domains; sub domains match automatically. */
    val domains: List<String>,
    /** Page opened by the built-in WebView login, or null when login is not supported. */
    val loginUrl: String? = null,
    /** Cookie names whose presence means "the user is logged in". */
    val authCookies: List<String> = emptyList(),
) {
    YOUTUBE(
        id = "youtube",
        displayName = "YouTube",
        folderName = "YouTube",
        domains = listOf("youtube.com", "youtu.be", "youtube-nocookie.com", "music.youtube.com"),
        loginUrl = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fwww.youtube.com",
        authCookies = listOf("SAPISID", "__Secure-3PSID", "LOGIN_INFO"),
    ),
    INSTAGRAM(
        id = "instagram",
        displayName = "Instagram",
        folderName = "Instagram",
        domains = listOf("instagram.com", "instagr.am"),
        loginUrl = "https://www.instagram.com/accounts/login/",
        authCookies = listOf("sessionid"),
    ),
    X(
        id = "x",
        displayName = "X",
        folderName = "X",
        domains = listOf("x.com", "twitter.com", "t.co", "fxtwitter.com", "vxtwitter.com", "fixupx.com"),
        loginUrl = "https://x.com/i/flow/login",
        authCookies = listOf("auth_token"),
    ),
    TIKTOK(
        id = "tiktok",
        displayName = "TikTok",
        folderName = "TikTok",
        domains = listOf("tiktok.com"),
        loginUrl = "https://www.tiktok.com/login",
        authCookies = listOf("sessionid", "sessionid_ss"),
    ),
    FACEBOOK(
        id = "facebook",
        displayName = "Facebook",
        folderName = "Facebook",
        domains = listOf("facebook.com", "fb.watch", "fb.com"),
        loginUrl = "https://m.facebook.com/login/",
        authCookies = listOf("c_user", "xs"),
    ),
    THREADS(
        id = "threads",
        displayName = "Threads",
        folderName = "Threads",
        domains = listOf("threads.net", "threads.com"),
        loginUrl = "https://www.threads.com/login",
        authCookies = listOf("sessionid"),
    ),
    REDDIT(
        id = "reddit",
        displayName = "Reddit",
        folderName = "Reddit",
        domains = listOf("reddit.com", "redd.it"),
        loginUrl = "https://www.reddit.com/login/",
        authCookies = listOf("reddit_session", "token_v2"),
    ),
    PINTEREST(
        id = "pinterest",
        displayName = "Pinterest",
        folderName = "Pinterest",
        domains = listOf("pinterest.com", "pin.it", "pinterest.co.uk", "pinterest.jp", "pinterest.de", "pinterest.fr"),
        loginUrl = "https://www.pinterest.com/login/",
        authCookies = listOf("_pinterest_sess"),
    ),
    TUMBLR(
        id = "tumblr",
        displayName = "Tumblr",
        folderName = "Tumblr",
        domains = listOf("tumblr.com"),
        loginUrl = "https://www.tumblr.com/login",
        authCookies = listOf("logged_in"),
    ),
    BILIBILI(
        id = "bilibili",
        displayName = "Bilibili",
        folderName = "Bilibili",
        domains = listOf("bilibili.com", "b23.tv", "bilibili.tv"),
        loginUrl = "https://passport.bilibili.com/login",
        authCookies = listOf("SESSDATA"),
    ),
    WEIBO(
        id = "weibo",
        displayName = "Weibo",
        folderName = "Weibo",
        domains = listOf("weibo.com", "weibo.cn"),
        loginUrl = "https://passport.weibo.com/sso/signin",
        authCookies = listOf("SUB"),
    ),
    PIXIV(
        id = "pixiv",
        displayName = "Pixiv",
        folderName = "Pixiv",
        domains = listOf("pixiv.net"),
        loginUrl = "https://accounts.pixiv.net/login",
        authCookies = listOf("PHPSESSID"),
    ),
    DEVIANTART(
        id = "deviantart",
        displayName = "DeviantArt",
        folderName = "DeviantArt",
        domains = listOf("deviantart.com"),
    ),
    IMGUR(
        id = "imgur",
        displayName = "Imgur",
        folderName = "Imgur",
        domains = listOf("imgur.com"),
    ),
    FLICKR(
        id = "flickr",
        displayName = "Flickr",
        folderName = "Flickr",
        domains = listOf("flickr.com", "flic.kr"),
    ),
    VIMEO(
        id = "vimeo",
        displayName = "Vimeo",
        folderName = "Vimeo",
        domains = listOf("vimeo.com"),
        loginUrl = "https://vimeo.com/log_in",
        authCookies = listOf("vimeo"),
    ),
    TWITCH(
        id = "twitch",
        displayName = "Twitch",
        folderName = "Twitch",
        domains = listOf("twitch.tv"),
        loginUrl = "https://www.twitch.tv/login",
        authCookies = listOf("auth-token"),
    ),
    SOUNDCLOUD(
        id = "soundcloud",
        displayName = "SoundCloud",
        folderName = "SoundCloud",
        domains = listOf("soundcloud.com", "on.soundcloud.com"),
    ),
    TELEGRAM(
        id = "telegram",
        displayName = "Telegram",
        folderName = "Telegram",
        domains = listOf("t.me", "telegram.me", "telegram.dog"),
    ),
    GENERIC(
        id = "generic",
        displayName = "Other",
        folderName = "Other",
        domains = emptyList(),
    );

    val supportsWebLogin: Boolean get() = loginUrl != null

    companion object {
        fun fromId(id: String?): Platform = entries.firstOrNull { it.id == id } ?: GENERIC

        /** Platforms that can store cookies (web login or cookies.txt import). */
        val cookiePlatforms: List<Platform> get() = entries.filter { it.supportsWebLogin || it == GENERIC }
    }
}
