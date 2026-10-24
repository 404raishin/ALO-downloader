# ALO Downloader

[![Build](https://github.com/404raishin/ALO-downloader/actions/workflows/build.yml/badge.svg)](https://github.com/404raishin/ALO-downloader/actions/workflows/build.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

**ALO** is an all-in-one media downloader for Android. Tap **Share** in Instagram, X, YouTube,
Telegram or your browser, pick **ALO**, and the download starts by itself. ALO detects the
link, picks the right engine (yt-dlp, gallery-dl or Telegram), falls back to another engine
if the first one fails, and saves the files to `Download/ALO/<Platform>`.

Its design and feature set take inspiration from the open-source YouTube downloader Seal,
extended to many platforms. All code, the name and the icon are original.

> **Distribution:** ALO is published **only on GitHub Releases and F-Droid**. It is not, and
> will not be, on Google Play. Any other source is unofficial.

---

## Features

### Downloading (Seal-style core)
- Share-sheet, text-selection ("Download with ALO") and clipboard links start downloads automatically
- Optional format sheet before downloading: video/audio, quality presets, exact video and
  audio streams, subtitles, playlist item selection, engine override and scheduled start time
- Audio extraction (MP3, M4A, Opus, FLAC, WAV) with square album-art cover
- Subtitles (manual and auto-generated, language filter, embed or SRT file)
- Embedded thumbnail, metadata and chapters
- Playlists and batch downloads (paste or share several links at once)
- SponsorBlock: mark segments as chapters or remove them
- Custom command templates for yt-dlp or gallery-dl (add to or replace ALO's options)
- History / library with search and filters, archive-based de-duplication
  (yt-dlp `--download-archive`, gallery-dl SQLite archive)
- Speed limit, proxy (HTTP/SOCKS), concurrent downloads, concurrent fragments, aria2c,
  force IPv4, Wi-Fi-only mode
- Notifications with progress and pause / resume / cancel / retry buttons
- Settings for appearance, download, network, storage, accounts, engines and about

### Routing layer
| Link | First engine | Fallback |
|---|---|---|
| YouTube, Vimeo, Twitch, SoundCloud, Bilibili, TikTok videos, Facebook videos, Reddit videos | yt-dlp | gallery-dl |
| Instagram reels / TV | yt-dlp | gallery-dl |
| Instagram posts, carousels, stories, profiles | gallery-dl | yt-dlp |
| X status links | yt-dlp (video) | gallery-dl (images) |
| X profiles / media tabs | gallery-dl | yt-dlp |
| Pinterest, Pixiv, DeviantArt, Imgur, Flickr, Tumblr, Weibo, Reddit galleries, TikTok photo posts, direct image links | gallery-dl | yt-dlp |
| `t.me` posts | Telegram | yt-dlp |
| Anything else | yt-dlp | gallery-dl |

- Automatic fallback; the error shown is the most actionable one across engines
- One unified queue for every engine (Room database + foreground WorkManager worker)
- Per-platform folders: `Download/ALO/Instagram`, `ALO/X`, `ALO/YouTube`, … via MediaStore
  (Android 10+), the public Download folder (Android 7–9), or any folder chosen with the
  Storage Access Framework. Per-platform sub-folders can be switched off.

### Private content
- Built-in WebView login per platform; cookies are captured automatically once the session
  cookie appears
- `cookies.txt` (Netscape format) import, split per platform automatically
- Per-platform storage with refresh and clear, encrypted with EncryptedSharedPreferences
  (AES-256, key in the Android Keystore); cookies are written to a short-lived file only
  while a download runs
- Clear failure reasons (login required, login expired, private, rate limited, geo-blocked,
  not found, unsupported…) with a one-tap **Log in** button
- Instagram login shows a warning to use a secondary account

### Telegram
- Public channel posts: parsed from Telegram's public web preview, no account needed
- Private channels and groups, documents and large files: TDLib user login with **your own**
  API ID / hash from [my.telegram.org](https://my.telegram.org/apps); the local TDLib
  database is encrypted with a random key stored in the encrypted vault

### Engine updates
- One-tap update of **yt-dlp** (stable or nightly channel) through youtubedl-android
- One-tap update of **gallery-dl**: the newest wheel is downloaded from PyPI, its SHA-256 is
  verified and the embedded Python loads it ahead of the bundled copy (can be reverted)
- Optional weekly automatic update on Wi-Fi

### Extras
- In-app gallery (grid with video thumbnails) and viewer/player (zoomable images, Media3
  for video and audio, share / open with)
- Quick Settings tile and home screen widget: download the link in the clipboard
- Scheduled downloads (pick a start time in the format sheet)
- Material 3 / Material You dynamic color, fallback palettes, dark and pure-black themes
- English and Simplified Chinese UI, switchable in-app

---

## Installing

Download the APK from [Releases](https://github.com/404raishin/ALO-downloader/releases):

| APK | Devices |
|---|---|
| `…-arm64-v8a-release.apk` | almost all phones from the last ~8 years |
| `…-armeabi-v7a-release.apk` | older 32-bit phones |
| `…-x86_64-release.apk` | emulators, Chromebooks |
| `…-universal-release.apk` | any device (much larger) |

Requires Android 7.0 (API 24) or newer. Every push to the repository also produces debug
APKs as a workflow artifact on the [Actions](https://github.com/404raishin/ALO-downloader/actions) tab.

---

## Building

Requirements: JDK 17, Android SDK with platform 36, and **Python 3.11 on `PATH`** (Chaquopy
uses it at build time to `pip install` gallery-dl into the APK).

```bash
./gradlew :core:test          # fast JVM unit tests (engine args, router, cookies, queue, …)
./gradlew assembleDebug       # APKs in app/build/outputs/apk/debug/
python3 tools/check_strings.py  # English / Chinese strings stay in sync
```

### CI
`.github/workflows/build.yml` runs on every push and pull request: string check, unit tests,
`assembleDebug`, and uploads the debug APKs as an artifact. Pushing a tag `v*` additionally
builds `assembleRelease` and creates a GitHub Release with per-ABI APKs and `SHA256SUMS.txt`.

Release signing uses these repository secrets (without them the release APK is signed with
the debug key so it is still installable):

| Secret | Content |
|---|---|
| `ALO_KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `ALO_KEYSTORE_PASSWORD` | keystore password |
| `ALO_KEY_ALIAS` | key alias |
| `ALO_KEY_PASSWORD` | key password |

Locally you can put the same values into `keystore.properties` (`storeFile`, `storePassword`,
`keyAlias`, `keyPassword`); it is git-ignored.

> **Note on how this project was built.** The cloud sandbox used to write this code could not
> reach Google's Maven repository or download the Android SDK. The pure-Kotlin `:core` module
> (and the Python bridge and TDLib wrapper) were compiled and tested locally; the Android app
> itself is compiled, tested and packaged by GitHub Actions, where all four phases built green.

### Project layout
```
core/   pure Kotlin, no Android: models, EngineRouter, FallbackRunner, ErrorClassifier,
        yt-dlp / gallery-dl argument builders, cookies.txt parser, command tokenizer,
        yt-dlp JSON parser, queue planner, Telegram link/embed parsers, version helpers
app/    Android app (MVVM + Hilt + Room + DataStore + Coroutines/Flow + WorkManager)
  data/        Room (tasks, files, templates), DataStore settings, encrypted cookie vault
  download/    DownloadManager (API), DownloadExecutor (queue), worker, notifications, storage
  engine/      yt-dlp (youtubedl-android) and gallery-dl (Chaquopy) engines, EngineRegistry
  telegram/    TDLib session, Telegram engine and repository
  update/      engine updater and weekly worker
  share/       share-sheet / clipboard entry point
  shortcut/    Quick Settings tile and home screen widget
  ui/          Jetpack Compose screens (home, library, viewer, settings, accounts, …)
  src/main/python/alo_gallerydl.py   bridge between Kotlin and gallery-dl
```

---

## Cookie guide (private content)

Some content is only visible when logged in: private Instagram accounts you follow, X
accounts behind a login wall, age-restricted or members-only YouTube videos, Bilibili HD
streams, and so on. ALO uses *your* session cookies to fetch them.

**Option A – log in inside ALO (easiest)**
1. Settings → **Accounts and cookies** → pick the platform → **Log in**
   (or tap **Log in to …** on a failed download).
2. Log in normally in the built-in browser. When ALO sees the session cookie it shows
   *“Logged in. Tap to save and close”*. Tap **Save** at any time to store the cookies.
3. Retry the download.

**Option B – import `cookies.txt`**
1. On a computer, log in to the site and export cookies in **Netscape** format with an
   extension such as *Get cookies.txt LOCALLY* (export only the sites you need).
2. Copy the file to the phone, then Settings → **Accounts and cookies** → **Import cookies.txt**.
3. Cookies are sorted to the right platform by domain; unknown domains go to *Other*.

This is the recommended way for **YouTube**: Google often refuses sign-ins inside embedded
browsers.

**Refresh / clear.** *Refresh* re-reads the built-in browser's session (and opens the login
page if it has expired). *Clear and log out* deletes the stored cookies and logs the built-in
browser out of that site.

**Errors.** *Login expired* means cookies were sent but the site rejected them – log in again.
*Rate limited* means the site is throttling you – wait, lower concurrent downloads, or raise
the gallery-dl request delay in Settings → Network.

> ⚠️ **Instagram:** frequent automated downloads can trigger rate limits, checkpoints or even
> a ban on the logged-in account. **Use a secondary account**, never your main one.

Security: cookies are stored encrypted (EncryptedSharedPreferences), are excluded from
Android backups, never leave the device, and are written to a temporary file only while a
download is running.

### Telegram login
Settings → Accounts and cookies → **Telegram account**. Create an application on
[my.telegram.org/apps](https://my.telegram.org/apps), enter its **API ID** and **API hash**,
then your phone number, the login code and (if enabled) your two-step verification password.
Public posts never need this.

### Command templates
Settings → **Command templates**. A template is a list of extra yt-dlp or gallery-dl
arguments, e.g. `-f "bv*[height<=720]+ba" --embed-chapters` or `--range 1-10`. ALO always
adds the URL, the output folder, cookies and the archive itself. Turn on *Replace ALO
options* to use only your arguments for format selection and post-processing.

---

## Status

### Done
- **Phase 1** – Gradle Kotlin DSL + version catalog, Material 3 theme with dynamic color,
  share intent, yt-dlp integration, unified queue (Room + foreground WorkManager),
  notifications with pause/resume/cancel/retry
- **Phase 2** – format/quality sheet, audio-only, subtitles, thumbnail/metadata/chapters,
  playlists and batches, SponsorBlock, command templates, library/history, archive
  de-duplication, speed limit/proxy/concurrency/Wi-Fi only, settings screens, cookie system
  (WebView login, cookies.txt import, encrypted per-platform storage, refresh/clear, clear
  error reasons with login shortcut, Instagram warning), English + Simplified Chinese
- **Phase 3** – gallery-dl via Chaquopy, routing layer with automatic fallback,
  per-platform folders via MediaStore or SAF
- **Phase 4** – Telegram (public posts without login, private chats via TDLib), one-tap
  yt-dlp and gallery-dl updates with weekly auto-update, in-app gallery and player,
  Quick Settings tile, home screen widget, scheduled downloads
- GitHub Actions: debug APK artifact on every push, signed release + GitHub Release on tags
- Unit tests for the core modules (engine argument builders, error classifier, router,
  fallback runner, cookies, download queue planner, Telegram parsers, …)

### Not done yet (TODO)
- Downloading a whole Telegram channel or joining via invite links (single posts only)
- Parallel gallery-dl jobs (one embedded interpreter, so gallery-dl tasks run one at a time;
  yt-dlp and Telegram tasks still run in parallel)
- Byte-level resume for gallery-dl and Telegram (pause stops the task; resume restarts the
  current file, and the archive skips files already saved)
- F-Droid submission (fastlane metadata is included; see known issues)
- Instrumented/UI tests (only JVM unit tests exist)

### Known issues
- **Not yet verified on a physical device.** Everything compiles and the core logic is unit
  tested, but the end-to-end download flows have only been checked through CI builds.
- APKs are large because they embed two Python runtimes (youtubedl-android's and
  Chaquopy's), FFmpeg, aria2c and TDLib. Install the per-ABI APK, not the universal one.
- Google sign-in (YouTube) usually refuses embedded browsers; use cookies.txt import.
- Android's WebView does not expose cookie expiry dates, so captured cookies get a 180-day
  lifetime; real expiry is detected when a site rejects them (*Login expired*).
- Android 15+ limits `dataSync` foreground services to about 6 hours per day; very long
  queues may be paused by the system and resume later.
- Instagram and X change their APIs often; when downloads break, update the engines in
  Settings → Download engines.
- Prebuilt binaries inside youtubedl-android, Chaquopy and TDLib may need extra work to
  satisfy F-Droid's reproducible-build policy.
- On Android 12 and older the in-app language switch relies on AppCompat's stored locale
  and may need an app restart.

---

## Disclaimer

ALO Downloader is a tool. **Only download content you own or have permission to access and
save.** You are responsible for complying with the terms of service of the websites you use
and with the copyright law of your country. The developers do not host any content and are
not affiliated with YouTube, Instagram, X, Telegram, Seal or any other site or project
mentioned here.

## Credits

[yt-dlp](https://github.com/yt-dlp/yt-dlp) ·
[gallery-dl](https://github.com/mikf/gallery-dl) ·
[youtubedl-android](https://github.com/JunkFood02/youtubedl-android) ·
[FFmpeg](https://ffmpeg.org) · [aria2](https://aria2.github.io) ·
[Chaquopy](https://chaquo.com/chaquopy/) · [TDLib](https://core.telegram.org/tdlib) via
[tdl-coroutines](https://github.com/g000sha256/tdl-coroutines) ·
[Coil](https://coil-kt.github.io/coil/) · [Media3](https://developer.android.com/media/media3).
Design inspired by [Seal](https://github.com/JunkFood02/Seal).

## License

ALO Downloader is free software, licensed under the
[GNU General Public License v3.0](LICENSE) (youtubedl-android is GPL as well).
