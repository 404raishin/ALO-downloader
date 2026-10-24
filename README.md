# ALO Downloader

All-in-one media downloader for Android. Share a link from Instagram, X, YouTube, Telegram
and many more sites to ALO and it picks the right engine and downloads automatically.

> Work in progress — see the Status section once all phases land.

## Building

The Android build runs on GitHub Actions (`.github/workflows/build.yml`); every push produces
debug APKs as a workflow artifact. Locally: JDK 17, Android SDK 36, then `./gradlew assembleDebug`.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
