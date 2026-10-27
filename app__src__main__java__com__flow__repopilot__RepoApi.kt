package com.flow.repopilot

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.ZipInputStream

 data class PickedFile(val uri: Uri, val name: String, val size: Long, val mime: String)
 data class ZipItem(val path: String, val bytes: ByteArray)


internal fun safeZipPath(rawPath: String, basePath: String): String? {
    val raw = rawPath.replace('\\', '/')
    val parts = raw.split('/')
    require(!raw.startsWith('/') && !raw.startsWith("~") && parts.none { it == ".." || it == "." } && !raw.contains(':')) {
        "ملف ZIP يحتوي على مسار غير آمن: $raw"
    }
    if (parts.any { it == ".git" }) return null
    val prefix = basePath.trim().trim('/').let { if (it.isBlank()) "" else "$it/" }
    val path = prefix + raw.trimStart('/')
    require(path.isNotBlank() && path.length <= 240) { "مسار ملف غير صالح أو طويل جدًا." }
    return path
}

object RepoApi {
    private const val API = "https://api.github.com"
    private const val MAX_SINGLE_FILE = 24L * 1024L * 1024L
    private const val MAX_ZIP_TOTAL = 80L * 1024L * 1024L
    private const val MAX_ZIP_ENTRIES = 500

    fun fileInfo(resolver: ContentResolver, uri: Uri): PickedFile {
        var name = "upload.bin"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
            }
        }
        return PickedFile(uri, name, size, resolver.getType(uri) ?: "application/octet-stream")
    }

    fun uploadZipToRepository(
        token: String, owner: String, repo: String, branch: String, basePath: String,
        commitMessage: String, resolver: ContentResolver, uri: Uri,
        progress: (String) -> Unit
    ): String {
        require(token.isNotBlank()) { "أضف GitHub Token من الإعدادات أولًا." }
        validateRepo(owner, repo, branch)
        val zipItems = readZip(resolver.openInputStream(uri) ?: error("تعذر فتح الملف"), basePath, progress)
        require(zipItems.isNotEmpty()) { "ملف ZIP لا يحتوي على ملفات قابلة للرفع." }
        progress("فحص صلاحية المستودع والفرع…")
        val ref = request("GET", "$API/repos/${enc(owner)}/${enc(repo)}/git/ref/heads/${pathEncode(branch)}", token)
        val parentSha = ref.getJSONObject("object").getString("sha")
        val commit = request("GET", "$API/repos/${enc(owner)}/${enc(repo)}/git/commits/$parentSha", token)
        val baseTree = commit.getJSONObject("tree").getString("sha")
        val tree = JSONArray()
        zipItems.forEachIndexed { index, item ->
            progress("رفع الملفات: ${index + 1} / ${zipItems.size} — ${item.path}")
            val blob = request("POST", "$API/repos/${enc(owner)}/${enc(repo)}/git/blobs", token,
                JSONObject().put("content", android.util.Base64.encodeToString(item.bytes, android.util.Base64.NO_WRAP)).put("encoding", "base64"))
            val mode = if (item.path.endsWith(".sh", ignoreCase = true)) "100755" else "100644"
            tree.put(JSONObject().put("path", item.path).put("mode", mode).put("type", "blob").put("sha", blob.getString("sha")))
        }
        progress("تجهيز التحديث كمراجعة واحدة…")
        val newTree = request("POST", "$API/repos/${enc(owner)}/${enc(repo)}/git/trees", token,
            JSONObject().put("base_tree", baseTree).put("tree", tree))
        val newCommit = request("POST", "$API/repos/${enc(owner)}/${enc(repo)}/git/commits", token,
            JSONObject().put("message", commitMessage.ifBlank { "Update files from RepoPilot" })
                .put("tree", newTree.getString("sha")).put("parents", JSONArray().put(parentSha)))
        request("PATCH", "$API/repos/${enc(owner)}/${enc(repo)}/git/refs/heads/${pathEncode(branch)}", token,
            JSONObject().put("sha", newCommit.getString("sha")).put("force", false))
        return "تم رفع ${zipItems.size} ملف في commit واحد.\nCommit: ${newCommit.getString("sha").take(7)}"
    }

    fun uploadAsRelease(
        token: String, owner: String, repo: String, tag: String, notes: String,
        resolver: ContentResolver, uri: Uri, progress: (String) -> Unit
    ): String {
        require(token.isNotBlank()) { "أضف GitHub Token من الإعدادات أولًا." }
        validateRepo(owner, repo, tag)
        val info = fileInfo(resolver, uri)
        require(info.size >= 0L) { "تعذر معرفة حجم الملف من مدير الملفات؛ جرّب اختيار الملف من تطبيق الملفات الأساسي." }
        require(info.size <= 2L * 1024L * 1024L * 1024L) { "حجم الملف أكبر من الحد المدعوم لملفات الإصدارات." }
        progress("إنشاء إصدار GitHub…")
        val release = try {
            request("POST", "$API/repos/${enc(owner)}/${enc(repo)}/releases", token,
                JSONObject().put("tag_name", tag).put("name", tag).put("body", notes)
                    .put("draft", false).put("prerelease", tag.contains("alpha", true) || tag.contains("beta", true) || tag.contains("rc", true)))
        } catch (e: Exception) {
            val existing = try { request("GET", "$API/repos/${enc(owner)}/${enc(repo)}/releases/tags/${pathEncode(tag)}", token) } catch (_: Exception) { null }
            existing ?: throw e
        }
        val releaseId = release.getLong("id")
        val assets = request("GET", "$API/repos/${enc(owner)}/${enc(repo)}/releases/$releaseId/assets", token).optJSONArray("_array") ?: JSONArray()
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name") == info.name) {
                request("DELETE", "$API/repos/${enc(owner)}/${enc(repo)}/releases/assets/${asset.getLong("id")}", token)
            }
        }
        progress("رفع ${info.name} إلى الإصدار…")
        val uploadUrl = release.optString("upload_url").substringBefore("{")
        require(uploadUrl.startsWith("https://uploads.github.com/")) { "رابط رفع الإصدار غير صالح." }
        val input = resolver.openInputStream(uri) ?: error("تعذر فتح الملف")
        input.use { stream -> uploadAsset(uploadUrl + "?name=" + enc(info.name), token, info.mime, stream, info.size) }
        return "تم نشر ${info.name} ضمن الإصدار $tag.\n${release.optString("html_url")}" 
    }

    fun openRouter(token: String, model: String, task: String, context: String): String {
        require(token.isNotBlank()) { "أضف OpenRouter API Key من الإعدادات أولًا." }
        require(model.isNotBlank()) { "اكتب معرّف النموذج في الإعدادات." }
        val body = JSONObject().put("model", model).put("temperature", 0.2).put("max_tokens", 700)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", "أنت مساعد هندسي داخل RepoPilot. أجب بالعربية المصرية الواضحة وباختصار عملي. لا تدّع تنفيذ أي إجراء. لا تطلب مفاتيح API ولا تكشفها. إذا كانت البيانات غير كافية فاذكر ذلك."))
                .put(JSONObject().put("role", "user").put("content", "المهمة: $task\nالسياق الذي كتبه المستخدم: $context\nأعطني نتيجة عملية قابلة للنسخ.")))
        val response = request("POST", "https://openrouter.ai/api/v1/chat/completions", token, body, extraHeaders = mapOf("HTTP-Referer" to "https://repopilot.app", "X-Title" to "RepoPilot Android"))
        val choices = response.optJSONArray("choices") ?: error("استجابة OpenRouter لا تحتوي على choices")
        return choices.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() }
            ?: error("النموذج لم يرجع نصًا.")
    }

    private fun readZip(input: InputStream, basePath: String, progress: (String) -> Unit): List<ZipItem> {
        val result = mutableListOf<ZipItem>()
        val seenPaths = mutableSetOf<String>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val raw = entry.name.replace('\\', '/')
                val path = safeZipPath(raw, basePath) ?: continue
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var count: Int
                while (zip.read(buffer).also { count = it } > 0) {
                    out.write(buffer, 0, count); total += count
                    require(total <= MAX_ZIP_TOTAL) { "مجموع الملفات بعد فك الضغط أكبر من 80MB؛ قسّم ZIP إلى أجزاء." }
                    require(out.size().toLong() <= MAX_SINGLE_FILE) { "الملف $raw أكبر من 24MB؛ ارفعه كـ Release asset بدلًا من إدخاله داخل المستودع." }
                }
                require(seenPaths.add(path)) { "ملف ZIP يحتوي على مسار مكرر: $path" }
                result.add(ZipItem(path, out.toByteArray()))
                require(result.size <= MAX_ZIP_ENTRIES) { "ZIP يحتوي على أكثر من 500 ملف؛ قسّمه إلى أجزاء." }
                zip.closeEntry()
            }
        }
        progress("تم فحص ${result.size} ملف بأمان.")
        return result
    }

    private fun request(method: String, url: String, token: String, body: JSONObject? = null, extraHeaders: Map<String, String> = emptyMap()): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 20000
        conn.readTimeout = 45000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("User-Agent", "RepoPilot-Android")
        extraHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) {
            val message = try { JSONObject(text).optString("message", text) } catch (_: Exception) { text }
            throw IllegalStateException("GitHub/API ($code): ${message.take(500)}")
        }
        if (text.isBlank()) return JSONObject()
        return if (text.trimStart().startsWith("[")) JSONObject().put("_array", JSONArray(text)) else JSONObject(text)
    }

    fun checkGitHub(token: String): String = request("GET", "$API/user", token).optString("login", "GitHub connected")

    private fun uploadAsset(url: String, token: String, mime: String, input: InputStream, size: Long) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 30000
        conn.readTimeout = 180000
        conn.doOutput = true
        conn.setFixedLengthStreamingMode(size)
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("Content-Type", mime)
        conn.setRequestProperty("User-Agent", "RepoPilot-Android")
        conn.outputStream.use { output -> input.copyTo(output, 64 * 1024) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) throw IllegalStateException("فشل رفع ملف الإصدار ($code): ${try { JSONObject(text).optString("message", text) } catch (_: Exception) { text }.take(400)}")
    }

    private fun validateRepo(owner: String, repo: String, branchOrTag: String) {
        require(owner.matches(Regex("[A-Za-z0-9-]{1,39}"))) { "اسم المالك غير صالح." }
        require(repo.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) { "اسم المستودع غير صالح." }
        require(branchOrTag.matches(Regex("[A-Za-z0-9._/-]{1,200}")) &&
            !branchOrTag.startsWith("/") && !branchOrTag.endsWith("/") && !branchOrTag.contains("//") &&
            !branchOrTag.contains("..") && !branchOrTag.contains("@{") && !branchOrTag.endsWith(".")) {
            "اسم الفرع أو الإصدار غير صالح؛ استخدم حروفًا وأرقامًا وشرطة ونقطة و/ فقط."
        }
    }
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    private fun pathEncode(value: String) = value.split('/').joinToString("/") { enc(it) }
}
