package com.flowstudio.gitagent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class Attachment(val id: String, val name: String, val size: Long, val file: File) {
    val isZip: Boolean get() = name.lowercase().endsWith(".zip")
}

class AgentException(message: String) : Exception(message)

/** Cancellable OkHttp call: cancelling the coroutine cancels the network request. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}

private const val MAX_STEPS = 12
private val DANGEROUS = setOf("github_create_repo", "github_upload_attachment", "github_delete_path")

private fun str(d: String) = JSONObject().put("type", "string").put("description", d)
private fun bool(d: String) = JSONObject().put("type", "boolean").put("description", d)
private fun int(d: String) = JSONObject().put("type", "integer").put("description", d)

private fun tool(name: String, desc: String, props: JSONObject, required: List<String>): JSONObject =
    JSONObject().put("type", "function").put(
        "function", JSONObject().put("name", name).put("description", desc).put(
            "parameters", JSONObject().put("type", "object").put("properties", props)
                .put("required", JSONArray(required))
        )
    )

private val TOOLS: JSONArray = JSONArray()
    .put(tool("github_whoami", "Return the GitHub login of the configured token.", JSONObject(), emptyList()))
    .put(tool("github_list_repos", "List the user's repositories (most recently updated first).",
        JSONObject().put("limit", int("max repos, default 30")), emptyList()))
    .put(tool("github_create_repo", "Create a new empty GitHub repository for the user.",
        JSONObject().put("name", str("repo name")).put("private", bool("default true")).put("description", str("optional")),
        listOf("name")))
    .put(tool("github_list_files", "List files/folders of a path in a repo.",
        JSONObject().put("repo", str("name or owner/name")).put("path", str("folder, empty for root")).put("branch", str("optional")),
        listOf("repo")))
    .put(tool("github_read_file", "Read a text file from a repo (first 20000 chars).",
        JSONObject().put("repo", str("name or owner/name")).put("path", str("file path")).put("branch", str("optional")),
        listOf("repo", "path")))
    .put(tool("github_upload_attachment",
        "Upload a user attachment to a repo as ONE commit. ZIP files are extracted and each file is committed at its path; unchanged files are skipped. The user is asked to confirm in the app.",
        JSONObject()
            .put("attachment_id", str("id of the attachment, e.g. f1"))
            .put("repo", str("name or owner/name"))
            .put("branch", str("optional, default branch if empty"))
            .put("target_dir", str("optional sub folder inside the repo, empty for root"))
            .put("commit_message", str("commit message"))
            .put("extract_zip", bool("default true; false uploads the zip itself as a file"))
            .put("strip_top_folder", bool("default true; removes the single wrapping folder of a zip"))
            .put("delete_missing", bool("default false; DANGEROUS: delete repo files under target_dir that are not in the zip")),
        listOf("attachment_id", "repo", "commit_message")))
    .put(tool("github_delete_path", "Delete a file or folder from a repo in one commit (user confirms in the app).",
        JSONObject().put("repo", str("name or owner/name")).put("path", str("file or folder"))
            .put("branch", str("optional")).put("commit_message", str("commit message")),
        listOf("repo", "path", "commit_message")))

class Agent(
    private val store: Store,
    private val attachments: () -> List<Attachment>,
    private val confirm: suspend (String) -> Boolean,
    private val status: (String) -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun systemPrompt(): String {
        val list = attachments().joinToString("\n") { "- ${it.id}: ${it.name} (${it.size / 1024} KB${if (it.isZip) ", zip" else ""})" }
            .ifBlank { "(none)" }
        return """
You are Flow Agent, an assistant inside an Android app. Your main job: take the files/zips the user attached and upload them to the user's GitHub repositories as commits (updates), using the tools. You can also list repos, browse/read files, create repos and delete paths.
Rules:
- Act, don't over-ask. If the repo is not named, call github_list_repos; if one clearly matches the project/zip name use it, otherwise ask the user ONE short question.
- The app itself shows a confirmation dialog for create/upload/delete, so do not ask for confirmation in chat.
- Never invent results. Report exactly what the tool returned (commit link, counts, errors) and say what to fix when a tool fails (e.g. token scopes: repo + workflow).
- Reply in the user's language (default Egyptian Arabic), short and direct.
Attachments available now:
$list
""".trimIndent()
    }

    suspend fun run(history: MutableList<JSONObject>, onStep: (String, Boolean, String) -> Unit): String {
        val start = history.size
        val gh = GitHub(store.ghToken)
        try {
            repeat(MAX_STEPS) {
                status("بفكر…")
                val msg = callModel(history)
                val text = if (msg.isNull("content")) "" else msg.optString("content", "")
                val calls = msg.optJSONArray("tool_calls")
                val a = JSONObject().put("role", "assistant")
                    .put("content", if (text.isBlank()) JSONObject.NULL else text)
                if (calls == null || calls.length() == 0) {
                    history.add(a)
                    return text
                }
                a.put("tool_calls", calls)
                history.add(a)
                for (i in 0 until calls.length()) {
                    val c = calls.getJSONObject(i)
                    val fn = c.getJSONObject("function")
                    val name = fn.getString("name")
                    val args = try {
                        JSONObject(fn.optString("arguments", "{}").ifBlank { "{}" })
                    } catch (e: Exception) { JSONObject() }
                    status("بنفّذ: $name")
                    val (okFlag, out) = execute(gh, name, args)
                    onStep(name, okFlag, out.take(160))
                    history.add(
                        JSONObject().put("role", "tool").put("tool_call_id", c.getString("id"))
                            .put("content", out.take(12000))
                    )
                }
            }
        } catch (e: Exception) {
            while (history.size > start) history.removeAt(history.lastIndex)
            throw e
        }
        return "وصلت للحد الأقصى من الخطوات ($MAX_STEPS). قولي أكمل لو عايز."
    }

    private suspend fun callModel(history: List<JSONObject>): JSONObject = withContext(Dispatchers.IO) {
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt()))
        history.forEach { msgs.put(it) }
        val body = JSONObject().put("model", store.model).put("messages", msgs)
            .put("tools", TOOLS).put("tool_choice", "auto").put("max_tokens", 4096)
        val req = Request.Builder().url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer ${store.orKey}")
            .header("X-Title", "Flow Agent")
            .header("HTTP-Referer", "https://github.com/flowstudio/flow-agent")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(req).await().use { r ->
                val text = r.body?.string() ?: ""
                if (!r.isSuccessful) {
                    val m = try { JSONObject(text).getJSONObject("error").optString("message", text) } catch (e: Exception) { text.take(300) }
                    val hint = when (r.code) {
                        401 -> " — مفتاح OpenRouter غلط"
                        402 -> " — رصيد OpenRouter خلص"
                        404 -> " — الموديل ده مش متاح أو مش بيدعم Tools"
                        429 -> " — زحمة، جرب بعد شوية"
                        else -> ""
                    }
                    throw AgentException("OpenRouter [${r.code}]: $m$hint")
                }
                val j = JSONObject(text)
                if (j.has("error")) throw AgentException("OpenRouter: ${j.getJSONObject("error").optString("message")}")
                val choices = j.optJSONArray("choices")
                if (choices == null || choices.length() == 0) throw AgentException("OpenRouter رجّع رد فاضي")
                choices.getJSONObject(0).getJSONObject("message")
            }
        } catch (e: IOException) {
            throw AgentException("مفيش اتصال بـ OpenRouter: ${e.message}")
        }
    }

    private fun describe(name: String, a: JSONObject): String = when (name) {
        "github_create_repo" ->
            "إنشاء مستودع ${a.optString("name")} (${if (a.optBoolean("private", true)) "خاص" else "عام"})"
        "github_upload_attachment" -> {
            val att = attachments().firstOrNull { it.id == a.optString("attachment_id") }
            "رفع ${att?.name ?: a.optString("attachment_id")} على ${a.optString("repo")}" +
                " (فرع: ${a.optString("branch").ifBlank { "الافتراضي" }}، مجلد: ${a.optString("target_dir").ifBlank { "الجذر" }})" +
                "\nرسالة الكوميت: ${a.optString("commit_message")}" +
                if (a.optBoolean("delete_missing", false)) "\n⚠ هيمسح أي ملفات في المجلد مش موجودة في الـ ZIP" else ""
        }
        "github_delete_path" -> "مسح ${a.optString("path")} من ${a.optString("repo")}"
        else -> name
    }

    private suspend fun execute(gh: GitHub, name: String, a: JSONObject): Pair<Boolean, String> {
        if (store.ghToken.isBlank()) return Pair(false, "GitHub token مش متسجل. اطلب من المستخدم يحطه من الإعدادات.")
        if (name in DANGEROUS && !confirm(describe(name, a))) return Pair(false, "User declined this action in the app.")
        return withContext(Dispatchers.IO) {
            try {
                Pair(true, doTool(gh, name, a))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Pair(false, "Error: ${e.message}")
            }
        }
    }

    private suspend fun doTool(gh: GitHub, name: String, a: JSONObject): String = when (name) {
        "github_whoami" -> "Logged in as ${gh.whoami()}"
        "github_list_repos" -> gh.listRepos(a.optInt("limit", 30))
        "github_create_repo" -> gh.createRepo(a.optString("name"), a.optBoolean("private", true), a.optString("description"))
        "github_list_files" -> gh.listFiles(a.optString("repo"), a.optString("path"), a.optString("branch"))
        "github_read_file" -> gh.readFile(a.optString("repo"), a.optString("path"), a.optString("branch"))
        "github_upload_attachment" -> uploadAttachment(gh, a)
        "github_delete_path" -> gh.deletePath(
            a.optString("repo"), a.optString("path"), a.optString("branch"),
            a.optString("commit_message").ifBlank { "Delete ${a.optString("path")} (Flow Agent)" }
        )
        else -> throw GhException("tool غير معروفة: $name")
    }

    private suspend fun uploadAttachment(gh: GitHub, a: JSONObject): String {
        val id = a.optString("attachment_id")
        val att = attachments().firstOrNull { it.id == id }
            ?: throw GhException("attachment_id '$id' مش موجود. المتاح: " + attachments().joinToString { "${it.id}=${it.name}" })
        val repo = a.optString("repo")
        val branch = a.optString("branch")
        val dir = a.optString("target_dir")
        val msg = a.optString("commit_message").ifBlank { "Update from Flow Agent: ${att.name}" }
        val extract = a.optBoolean("extract_zip", true)
        val strip = a.optBoolean("strip_top_folder", true)
        val del = a.optBoolean("delete_missing", false)
        return if (att.isZip && extract) {
            ZipFile(att.file).use { zf ->
                gh.uploadFiles(repo, branch, dir, msg, zipItems(zf, strip), del) { status("برفع $it") }
            }
        } else {
            gh.uploadFiles(repo, branch, dir, msg, listOf(UploadItem(att.name, att.size) { att.file.readBytes() }), false) { status("برفع $it") }
        }
    }
}
