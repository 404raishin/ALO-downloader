package com.alo.downloader.engine.gallerydl

import android.content.Context
import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Owns the embedded Python interpreter (Chaquopy) that runs gallery-dl. */
@Singleton
class GalleryDlRuntime @Inject constructor(@ApplicationContext private val context: Context) {

    /** Updated gallery-dl wheels downloaded in-app live here and override the bundled copy. */
    val wheelDir: File get() = File(context.filesDir, "gallery-dl").apply { mkdirs() }

    @Volatile var initError: String? = null
        private set

    private val module: PyObject? by lazy {
        runCatching {
            if (!Python.isStarted()) Python.start(AndroidPlatform(context))
            Python.getInstance().getModule("alo_gallerydl")
        }.onFailure {
            Log.e(TAG, "Python start failed", it)
            initError = it.message ?: it.toString()
        }.getOrNull()
    }

    fun bridge(): PyObject? = module

    fun version(): String? = runCatching { module?.callAttr("version", wheelDir.absolutePath)?.toString() }.getOrNull()

    private companion object { const val TAG = "GalleryDlRuntime" }
}

/** Called from Python; method names must match alo_gallerydl.py. */
interface GalleryDlCallback {
    fun onThread(id: Long)
    fun onLine(stream: String, line: String)
}
