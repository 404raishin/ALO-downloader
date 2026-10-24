# ALO Downloader: session handoff

## Repo and status
- Repo: `404raishin/ALO-downloader` (public), branch `ccr-896a2864-w4t7nk`; all work is committed and pushed.
- Latest test release: **v0.1.0-beta.2** (pre-release, signed with the debug key):
  https://github.com/404raishin/ALO-downloader/releases/tag/v0.1.0-beta.2
- Package `com.alo.downloader` (debug builds: `.debug`), `versionCode` 2, `versionName` 0.1.0.
- Licence GPLv3. Distributed only via GitHub Releases and F-Droid, never Google Play.

## Stack
- Kotlin, Jetpack Compose, Material 3 with dynamic color; MVVM, Hilt, Room, DataStore, Coroutines/Flow, WorkManager.
- AGP 8.13.0, Gradle 8.14.3, Kotlin 2.2.21, KSP 2.2.21-2.0.4, Hilt 2.57.2, Compose BOM 2025.06.00.
- compile/target SDK 36, minSdk 24.
- Engines:
  - yt-dlp via `io.github.junkfood02.youtubedl-android` 0.18.1 (library + ffmpeg + aria2c).
  - gallery-dl 1.32.17 via Chaquopy 17.1.0 on Python 3.11.
  - TDLib via `dev.g000sha256:tdl-coroutines-android` 10.0.0.
- ABIs: arm64-v8a, armeabi-v7a, x86_64, with ABI splits plus a universal APK. The arm64 APK is about 91 MB.

## Modules
### `core/` (pure Kotlin, 73 unit tests)
- Models: `Platform`, `DownloadOptions` and its JSON codec, `FailureReason`.
- `EngineRouter`, `FallbackRunner`, `ErrorClassifier`.
- `YtDlpArgs`, `GalleryDlArgs`, `CommandLine` (template tokenizer).
- `NetscapeCookies`, `MediaInfoParser` (yt-dlp JSON), `QueuePlanner`.
- Telegram link and embed parsers, `Versions` and the PyPI parser.

### `app/`
- `data/`: Room (tasks, files, templates), DataStore settings, `CookieVault` (EncryptedSharedPreferences).
- `download/`: `DownloadManager`, `DownloadExecutor` (queue loop), `DownloadQueueWorker` (foreground, dataSync), notifier, `OutputStorage` (MediaStore `Download/ALO/<Platform>` or SAF).
- `engine/`: `ytdlp/` and `gallerydl/`. The Kotlin↔gallery-dl bridge is `app/src/main/python/alo_gallerydl.py`.
- `telegram/`: `TdlibSession`, `TelegramEngine`, `TelegramRepository`.
- `update/`: `EngineUpdater` and the weekly worker.
- `share/`: `ShareActivity`, which handles share, PROCESS_TEXT and the clipboard action.
- `shortcut/`: Quick Settings tile and home-screen widget.
- `ui/`: home, library (list and grid), viewer (Media3), configure sheet, settings, accounts and web login, templates, Telegram login, engines, diagnostics.

### Strings
- English and Simplified Chinese.
- `tools/add_strings.py` adds keys to both languages.
- `tools/check_strings.py` checks they stay in sync and runs in CI.

## CI (`.github/workflows/build.yml`)
- `build`: string check, unit tests, `assembleDebug`, debug APK uploaded as an artifact.
- `instrumented`: 12 tests on an API 34 x86_64 emulator, using fixtures in `testdata/` fetched from the commit under test. Covers the engines, MediaStore, the cookie vault, a UI smoke test, and the full pipeline end to end.
- `release-smoke`: installs the minified release APK on an emulator, shares two links to it via `tools/release-smoke.sh`, checks both files arrive, and prints logcat.
- Publishing a release: **Actions → Build → Run workflow** with a `release_tag`; a `-` in the tag marks it as a pre-release. Pushing tags from the cloud session is blocked.
- Signing secrets: `ALO_KEYSTORE_BASE64`, `ALO_KEYSTORE_PASSWORD`, `ALO_KEY_ALIAS`, `ALO_KEY_PASSWORD`.

## Bugs found by the emulator tests (fixed)
- Chaquopy's `PYTHONPATH` broke yt-dlp's child Python. Fixed by unsetting the `PYTHON*` variables after Chaquopy starts.
- The weekly update job replaced yt-dlp while a download was running. Fixed with a one-day initial delay; updates are refused or postponed while downloads run.
- The bundled yt-dlp reported no version. Fixed by running `yt-dlp --version` and caching the result.

## Open issue
- **Reported on the user's real phone (beta.1):** "yt-dlp / gallery-dl can't load", and a pasted link doesn't work.
- **What is known:** the release APK works on the emulator for direct links.
- **Added in beta 2:**
  - Recognition of links without `https://`.
  - Engine start errors shown on the Engines screen.
  - Settings → About → Diagnostics: a copyable report with device, Android version, ABIs, page size, engine versions or errors, and recent failures.
- **Next step:** ask the user to install beta 2 and paste the Diagnostics report, plus the link or site they used. Then fix based on what it shows.

## Remaining TODO / known issues
- gallery-dl runs only one job at a time (single interpreter).
- Joining Telegram chats from invite links is not supported, by design.
- F-Droid submission is not done yet; the fastlane metadata and icon exist.
- Not yet tested on a real phone against real sites.
- APKs are large.
- Google blocks sign-in inside the in-app browser, so YouTube login needs a `cookies.txt` import.
- Android 15 limits dataSync foreground services to about 6 hours a day.

## Logo
- The user's glossy blue "A" logo, cleaned of edge specks, on a navy `#0A1634` adaptive icon.
- Also in `docs/logo.png`, `drawable-nodpi/alo_logo.png` and the fastlane icon.
