package com.alo.downloader.core.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object Versions {
    /**
     * Compares dotted versions numerically ("2025.10.22" vs "2025.9.1", "1.32.17" vs "1.32.2").
     * Non-numeric suffixes (".dev0", "rc1") sort before the plain release.
     */
    fun compare(a: String, b: String): Int {
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 to "" }
            val y = pb.getOrElse(i) { 0 to "" }
            if (x.first != y.first) return x.first.compareTo(y.first)
            if (x.second != y.second) return when {
                x.second.isEmpty() -> 1
                y.second.isEmpty() -> -1
                else -> x.second.compareTo(y.second)
            }
        }
        return 0
    }

    fun isNewer(candidate: String, current: String?): Boolean = current.isNullOrBlank() || compare(candidate, current) > 0

    private fun parts(v: String): List<Pair<Int, String>> = v.trim().removePrefix("v").split('.', '-', '+').map { part ->
        val digits = part.takeWhile { it.isDigit() }
        (digits.toIntOrNull() ?: 0) to part.drop(digits.length)
    }
}

data class WheelRelease(val version: String, val url: String, val sha256: String?, val fileName: String)

/** Reads `https://pypi.org/pypi/<name>/json` and picks the pure-Python wheel. */
object PyPiRelease {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): WheelRelease? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val version = root["info"]?.jsonObject?.get("version")?.jsonPrimitive?.contentOrNull ?: return null
        val files = (root["urls"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        val wheel = files.firstOrNull { f ->
            f["packagetype"]?.jsonPrimitive?.contentOrNull == "bdist_wheel" &&
                f["filename"]?.jsonPrimitive?.contentOrNull?.endsWith("py3-none-any.whl") == true
        } ?: return null
        return WheelRelease(
            version = version,
            url = wheel["url"]?.jsonPrimitive?.contentOrNull ?: return null,
            sha256 = (wheel["digests"] as? JsonObject)?.get("sha256")?.jsonPrimitive?.contentOrNull,
            fileName = wheel["filename"]?.jsonPrimitive?.contentOrNull ?: return null,
        )
    }
}
