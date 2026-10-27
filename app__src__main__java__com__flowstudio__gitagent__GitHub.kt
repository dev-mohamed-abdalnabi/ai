package com.flowstudio.gitagent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

class GhException(message: String) : Exception(message)

class UploadItem(val path: String, val size: Long, val read: () -> ByteArray)

private class Entry(val path: String, val sha: String?, val updated: Boolean)

fun zipItems(zf: ZipFile, strip: Boolean): List<UploadItem> {
    val names = zf.entries().asSequence().filter { !it.isDirectory }.map { it.name }.toList()
    return normalizeZipPaths(names, strip).map { (orig, clean) ->
        val e = zf.getEntry(orig)
        UploadItem(clean, e.size) { zf.getInputStream(e).use { it.readBytes() } }
    }
}

class GitHub(private val token: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json".toMediaType()
    private var login: String? = null

    private fun request(method: String, path: String, body: JSONObject? = null): Pair<Int, String> {
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                val rb = if (method == "GET") null else (body?.toString() ?: "{}").toRequestBody(jsonType)
                val req = Request.Builder()
                    .url(if (path.startsWith("http")) path else "https://api.github.com$path")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "FlowAgent")
                    .method(method, rb)
                    .build()
                client.newCall(req).execute().use { r ->
                    val text = r.body?.string() ?: ""
                    if (r.code in 500..599 && attempt < 2) {
                        Thread.sleep(1500L * (attempt + 1)); return@repeat
                    }
                    if ((r.code == 403 || r.code == 429) && text.contains("rate limit", true) && attempt < 2) {
                        Thread.sleep(20000L); return@repeat
                    }
                    return Pair(r.code, text)
                }
            } catch (e: IOException) {
                last = e
                Thread.sleep(1000L * (attempt + 1))
            }
        }
        throw GhException("مشكلة اتصال بـ GitHub: ${last?.message}")
    }

    private fun ok(r: Pair<Int, String>, what: String): String {
        if (r.first in 200..299) return r.second
        val msg = try { JSONObject(r.second).optString("message", r.second) } catch (e: Exception) { r.second.take(200) }
        val hint = when (r.first) {
            401 -> " (التوكن غلط أو منتهي)"
            403 -> " (صلاحيات التوكن ناقصة أو Rate limit)"
            404 -> " (مش موجود أو التوكن ملوش صلاحية على المستودع)"
            else -> ""
        }
        throw GhException("$what فشل [${r.first}]: $msg$hint")
    }

    private fun get(path: String, what: String) = ok(request("GET", path), what)
    private fun send(method: String, path: String, body: JSONObject, what: String) = ok(request(method, path, body), what)

    fun whoami(): String {
        login?.let { return it }
        val l = JSONObject(get("/user", "whoami")).getString("login")
        login = l
        return l
    }

    private fun full(repoIn: String): String {
        val r = repoIn.trim().removePrefix("https://github.com/").removeSuffix(".git").trim('/')
        if (r.isEmpty()) throw GhException("اسم المستودع فاضي")
        return if (r.contains('/')) r else "${whoami()}/$r"
    }

    fun listRepos(limit: Int): String {
        val raw = get("/user/repos?per_page=${limit.coerceIn(1, 100)}&sort=updated&affiliation=owner,collaborator,organization_member", "list repos")
        val arr = JSONArray(raw)
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            sb.append(o.getString("full_name")).append(if (o.getBoolean("private")) " [private]" else " [public]")
                .append(" branch=").append(o.optString("default_branch")).append('\n')
        }
        return if (sb.isEmpty()) "(no repositories)" else sb.toString()
    }

    fun createRepo(name: String, isPrivate: Boolean, description: String): String {
        val b = JSONObject().put("name", name.trim()).put("private", isPrivate).put("description", description)
        val o = JSONObject(send("POST", "/user/repos", b, "create repo"))
        return "Created ${o.getString("full_name")} (${if (o.getBoolean("private")) "private" else "public"}) ${o.getString("html_url")}"
    }

    fun listFiles(repoIn: String, path: String, branch: String): String {
        val q = if (branch.isBlank()) "" else "?ref=${URLEncoder.encode(branch, "UTF-8")}"
        val raw = get("/repos/${full(repoIn)}/contents/${enc(path.trim('/'))}$q", "list files")
        if (raw.trimStart().startsWith("{")) return "That path is a file, not a folder."
        val arr = JSONArray(raw)
        if (arr.length() == 0) return "(empty)"
        val sb = StringBuilder()
        for (i in 0 until minOf(arr.length(), 300)) {
            val o = arr.getJSONObject(i)
            sb.append(o.getString("type")).append(' ').append(o.getString("path")).append(" (").append(o.optLong("size")).append(")\n")
        }
        return sb.toString()
    }

    fun readFile(repoIn: String, path: String, branch: String): String {
        val q = if (branch.isBlank()) "" else "?ref=${URLEncoder.encode(branch, "UTF-8")}"
        val o = JSONObject(get("/repos/${full(repoIn)}/contents/${enc(path.trim('/'))}$q", "read file"))
        if (o.optString("type") != "file") return "Not a file."
        val c = o.optString("content", "")
        if (c.isBlank()) return if (o.optLong("size") > 0) "File too large to read through the API." else "(empty file)"
        return String(Base64.getMimeDecoder().decode(c), Charsets.UTF_8).take(20000)
    }

    suspend fun uploadFiles(
        repoIn: String, branchIn: String, targetDir: String, message: String,
        files: List<UploadItem>, deleteMissing: Boolean, onProgress: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        if (files.isEmpty()) throw GhException("مفيش ملفات للرفع")
        if (files.size > 5000) throw GhException("عدد الملفات كبير (${files.size}). الحد 5000.")
        files.firstOrNull { it.size > 90_000_000L }?.let { throw GhException("الملف ${it.path} أكبر من 90MB") }
        val repo = full(repoIn)
        val info = JSONObject(get("/repos/$repo", "get repo"))
        val defaultBranch = info.optString("default_branch", "main")
        val branch = branchIn.ifBlank { defaultBranch }
        val prefix = targetDir.trim().trim('/').let { if (it.isEmpty()) "" else "$it/" }
        var pending = files.map { UploadItem(prefix + it.path, it.size, it.read) }
        var initial = 0

        var refResp = request("GET", "/repos/$repo/git/ref/heads/${enc(branch)}")
        if (refResp.first == 404 || refResp.first == 409) {
            if (branch != defaultBranch) throw GhException("الفرع $branch مش موجود")
            val first = pending.first()
            val b = JSONObject().put("message", message)
                .put("content", Base64.getEncoder().encodeToString(first.read()))
            send("PUT", "/repos/$repo/contents/${enc(first.path)}", b, "init repo")
            pending = pending.drop(1)
            initial = 1
            if (pending.isEmpty()) return@withContext "Committed 1 file to $repo@$branch (repository was empty)."
            refResp = request("GET", "/repos/$repo/git/ref/heads/${enc(branch)}")
        }
        val head = JSONObject(ok(refResp, "get branch")).getJSONObject("object").getString("sha")
        val baseTree = JSONObject(get("/repos/$repo/git/commits/$head", "get commit"))
            .getJSONObject("tree").getString("sha")
        val treeObj = JSONObject(get("/repos/$repo/git/trees/$baseTree?recursive=1", "get tree"))
        val existing = HashMap<String, String>()
        val arr = treeObj.optJSONArray("tree") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("type") == "blob") existing[o.getString("path")] = o.getString("sha")
        }
        val truncated = treeObj.optBoolean("truncated", false)

        val sem = Semaphore(4)
        val done = AtomicInteger(0)
        val total = pending.size
        val results = coroutineScope {
            pending.map { f ->
                async {
                    sem.withPermit {
                        val data = f.read()
                        val sha = gitBlobSha(data)
                        val old = existing[f.path]
                        val res = if (old == sha) {
                            Entry(f.path, null, false)
                        } else {
                            val body = JSONObject()
                                .put("content", Base64.getEncoder().encodeToString(data))
                                .put("encoding", "base64")
                            val bsha = JSONObject(send("POST", "/repos/$repo/git/blobs", body, "blob ${f.path}")).getString("sha")
                            Entry(f.path, bsha, old != null)
                        }
                        onProgress("${done.incrementAndGet()}/$total")
                        res
                    }
                }
            }.awaitAll()
        }

        val changed = results.filter { it.sha != null }
        val newPaths = pending.map { it.path }.toHashSet()
        val deletions = if (deleteMissing && !truncated)
            existing.keys.filter { it.startsWith(prefix) && it !in newPaths } else emptyList()
        val initNote = if (initial == 1) "Initial file committed first. " else ""
        if (changed.isEmpty() && deletions.isEmpty()) {
            return@withContext "${initNote}No new changes: ${results.size} files identical to $repo@$branch."
        }
        val tree = JSONArray()
        for (e in changed) {
            tree.put(JSONObject().put("path", e.path).put("mode", fileMode(e.path)).put("type", "blob").put("sha", e.sha))
        }
        for (p in deletions) {
            tree.put(JSONObject().put("path", p).put("mode", "100644").put("type", "blob").put("sha", JSONObject.NULL))
        }
        val newTree = JSONObject(
            send("POST", "/repos/$repo/git/trees", JSONObject().put("base_tree", baseTree).put("tree", tree), "create tree")
        ).getString("sha")
        val commit = JSONObject(
            send("POST", "/repos/$repo/git/commits",
                JSONObject().put("message", message).put("tree", newTree).put("parents", JSONArray().put(head)), "create commit")
        )
        val csha = commit.getString("sha")
        send("PATCH", "/repos/$repo/git/refs/heads/${enc(branch)}", JSONObject().put("sha", csha), "update branch")
        val added = changed.count { !it.updated }
        val updated = changed.count { it.updated }
        "${initNote}Committed ${csha.take(7)} to $repo@$branch: $added added, $updated updated, " +
            "${results.size - changed.size} unchanged, ${deletions.size} deleted.\nhttps://github.com/$repo/commit/$csha" +
            if (truncated) "\n(note: repo tree was truncated, unchanged-detection partial)" else ""
    }

    suspend fun deletePath(repoIn: String, path: String, branchIn: String, message: String): String =
        withContext(Dispatchers.IO) {
            val p = path.trim().trim('/')
            if (p.isEmpty()) throw GhException("المسار فاضي")
            val repo = full(repoIn)
            val defaultBranch = JSONObject(get("/repos/$repo", "get repo")).optString("default_branch", "main")
            val branch = branchIn.ifBlank { defaultBranch }
            val head = JSONObject(get("/repos/$repo/git/ref/heads/${enc(branch)}", "get branch"))
                .getJSONObject("object").getString("sha")
            val baseTree = JSONObject(get("/repos/$repo/git/commits/$head", "get commit"))
                .getJSONObject("tree").getString("sha")
            val treeObj = JSONObject(get("/repos/$repo/git/trees/$baseTree?recursive=1", "get tree"))
            val arr = treeObj.optJSONArray("tree") ?: JSONArray()
            val tree = JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ep = o.getString("path")
                if (o.optString("type") == "blob" && (ep == p || ep.startsWith("$p/"))) {
                    tree.put(JSONObject().put("path", ep).put("mode", "100644").put("type", "blob").put("sha", JSONObject.NULL))
                }
            }
            if (tree.length() == 0) throw GhException("المسار $p مش موجود في $repo@$branch")
            val newTree = JSONObject(
                send("POST", "/repos/$repo/git/trees", JSONObject().put("base_tree", baseTree).put("tree", tree), "create tree")
            ).getString("sha")
            val commit = JSONObject(
                send("POST", "/repos/$repo/git/commits",
                    JSONObject().put("message", message).put("tree", newTree).put("parents", JSONArray().put(head)), "create commit")
            )
            val csha = commit.getString("sha")
            send("PATCH", "/repos/$repo/git/refs/heads/${enc(branch)}", JSONObject().put("sha", csha), "update branch")
            "Deleted ${tree.length()} file(s) under $p in $repo@$branch (commit ${csha.take(7)})."
        }
}
