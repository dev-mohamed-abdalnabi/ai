package com.flowstudio.gitagent

import java.net.URLEncoder
import java.security.MessageDigest

/** Same hash git computes for a blob, so unchanged files can be skipped. */
fun gitBlobSha(data: ByteArray): String {
    val md = MessageDigest.getInstance("SHA-1")
    md.update("blob ${data.size}\u0000".toByteArray(Charsets.UTF_8))
    md.update(data)
    return md.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Cleans zip entry names. Returns originalName -> repo path.
 * Skips directories, junk (__MACOSX, .git, .DS_Store) and any path containing "..".
 * If stripTop and every file lives under one single top folder, that folder is removed.
 */
fun normalizeZipPaths(names: List<String>, stripTop: Boolean): Map<String, String> {
    val cleaned = LinkedHashMap<String, String>()
    for (n in names) {
        if (n.endsWith("/")) continue
        val parts = n.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) continue
        if (parts[0] == "__MACOSX" || parts.any { it == ".git" } || parts.last() == ".DS_Store") continue
        cleaned[n] = parts.joinToString("/")
    }
    if (stripTop && cleaned.isNotEmpty()) {
        val tops = cleaned.values.map { it.substringBefore('/') }.toSet()
        if (tops.size == 1 && cleaned.values.all { it.contains('/') }) {
            for (k in cleaned.keys.toList()) cleaned[k] = cleaned.getValue(k).substringAfter('/')
        }
    }
    return cleaned
}

fun fileMode(path: String): String =
    if (path.endsWith(".sh") || path.substringAfterLast('/') == "gradlew") "100755" else "100644"

fun enc(path: String): String =
    path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
