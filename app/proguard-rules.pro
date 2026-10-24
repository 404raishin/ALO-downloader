# youtubedl-android maps yt-dlp JSON with Jackson.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-keep class org.apache.commons.compress.archivers.zip.** { *; }
-dontwarn org.apache.commons.**

# Chaquopy calls back into these classes from Python.
-keep class com.alo.downloader.engine.gallerydl.** { *; }
-keep class com.chaquo.python.** { *; }

# TDLib JSON client.
-keep class org.drinkless.tdlib.** { *; }
-keep class dev.g000sha256.tdl.** { *; }

-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
