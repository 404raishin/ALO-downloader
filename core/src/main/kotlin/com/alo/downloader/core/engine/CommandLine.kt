package com.alo.downloader.core.engine

/**
 * Splits a user-written command template into arguments, shell style: whitespace separates
 * arguments, single quotes are literal, double quotes allow `\"` escapes, and a backslash
 * escapes the next character outside quotes. Lines starting with `#` are comments.
 */
object CommandLine {

    class ParseException(message: String) : IllegalArgumentException(message)

    fun split(template: String): List<String> {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var inArg = false
        var quote: Char? = null
        var i = 0
        val text = template.lineSequence().filterNot { it.trimStart().startsWith("#") }.joinToString("\n")
        while (i < text.length) {
            val c = text[i]
            when {
                quote == '\'' -> if (c == '\'') quote = null else current.append(c)
                quote == '"' -> when {
                    c == '"' -> quote = null
                    c == '\\' && i + 1 < text.length && text[i + 1] in "\"\\$`" -> { current.append(text[i + 1]); i++ }
                    else -> current.append(c)
                }
                c == '\'' || c == '"' -> { quote = c; inArg = true }
                c == '\\' && i + 1 < text.length -> {
                    if (text[i + 1] != '\n') current.append(text[i + 1])
                    inArg = true
                    i++
                }
                c.isWhitespace() -> if (inArg) { args += current.toString(); current.clear(); inArg = false }
                else -> { current.append(c); inArg = true }
            }
            i++
        }
        if (quote != null) throw ParseException("Unterminated $quote quote")
        if (inArg) args += current.toString()
        // People paste whole commands; ALO supplies the program name and the URL itself.
        if (args.firstOrNull() in setOf("yt-dlp", "gallery-dl", "youtube-dl")) args.removeAt(0)
        return args.filterNot { it == "{url}" || it == "%(url)s" || it == "\$URL" }
    }

    /** Joins arguments back into a string that [split] reads identically. */
    fun join(args: List<String>): String = args.joinToString(" ") { arg ->
        if (arg.isNotEmpty() && arg.none { it.isWhitespace() || it in "'\"\\#" }) arg
        else "'" + arg.replace("'", "'\\''") + "'"
    }
}
