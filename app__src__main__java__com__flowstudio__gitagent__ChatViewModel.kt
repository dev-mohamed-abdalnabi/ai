package com.flowstudio.gitagent

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

sealed class UiMsg {
    abstract val id: Long
    data class User(override val id: Long, val text: String, val files: List<String>) : UiMsg()
    data class Bot(override val id: Long, val text: String) : UiMsg()
    data class Step(override val id: Long, val tool: String, val ok: Boolean, val summary: String) : UiMsg()
    data class Err(override val id: Long, val text: String) : UiMsg()
}

class ConfirmReq(val text: String, val result: CompletableDeferred<Boolean>)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    val messages = mutableStateListOf<UiMsg>()
    val pending = mutableStateListOf<Attachment>()

    var busy by mutableStateOf(false)
        private set
    var statusText by mutableStateOf("")
        private set
    var confirmReq by mutableStateOf<ConfirmReq?>(null)
        private set
    var testResult by mutableStateOf("")

    private val all = mutableListOf<Attachment>()
    private val history = mutableListOf<JSONObject>()
    private var nextId = 1L
    private val fileCounter = AtomicInteger(0)

    init {
        val f = File(app.filesDir, "last_crash.txt")
        if (f.exists()) {
            val first = try { f.readText().lineSequence().firstOrNull().orEmpty() } catch (e: Exception) { "" }
            f.delete()
            messages.add(UiMsg.Err(nid(), "التطبيق وقف بسبب خطأ المرة اللي فاتت: $first"))
        }
    }

    private fun nid() = nextId++

    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            for (u in uris) {
                try {
                    var name = "file"
                    ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (ni >= 0) name = c.getString(ni) ?: name
                        }
                    }
                    val id = "f${fileCounter.incrementAndGet()}"
                    val dir = File(ctx.cacheDir, "att").apply { mkdirs() }
                    val dest = File(dir, "${id}_${System.currentTimeMillis()}")
                    val input = ctx.contentResolver.openInputStream(u) ?: throw IllegalStateException("مقدرتش أفتح الملف")
                    input.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                    val att = Attachment(id, name, dest.length(), dest)
                    withContext(Dispatchers.Main) { pending.add(att) }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { messages.add(UiMsg.Err(nid(), "مقدرتش أقرأ الملف: ${e.message}")) }
                }
            }
        }
    }

    fun removePending(a: Attachment) {
        pending.remove(a)
        a.file.delete()
    }

    private suspend fun askConfirm(text: String): Boolean {
        val d = CompletableDeferred<Boolean>()
        confirmReq = ConfirmReq(text, d)
        return try { d.await() } finally { confirmReq = null }
    }

    fun answerConfirm(ok: Boolean) {
        confirmReq?.result?.complete(ok)
    }

    fun testGithub(token: String) {
        testResult = "…"
        viewModelScope.launch {
            testResult = try {
                val u = withContext(Dispatchers.IO) { GitHub(token.trim()).whoami() }
                "✅ متصل كـ $u"
            } catch (e: Exception) {
                "❌ ${e.message}"
            }
        }
    }

    /** Returns true if the message was accepted. */
    fun send(text: String): Boolean {
        if (busy || (text.isBlank() && pending.isEmpty())) return false
        if (store.orKey.isBlank()) {
            messages.add(UiMsg.Err(nid(), "حط مفتاح OpenRouter من الإعدادات الأول"))
            return false
        }
        val files = pending.toList()
        all.addAll(files)
        pending.clear()
        messages.add(UiMsg.User(nid(), text, files.map { it.name }))
        val content = buildString {
            append(text)
            if (files.isNotEmpty()) append("\n\n[Attached: " + files.joinToString { "${it.id}=${it.name}" } + "]")
        }
        history.add(JSONObject().put("role", "user").put("content", content.ifBlank { "(attachments)" }))
        busy = true
        viewModelScope.launch {
            try {
                val agent = Agent(store, { all.toList() }, { askConfirm(it) }, { statusText = it })
                val reply = agent.run(history) { n, ok, s -> messages.add(UiMsg.Step(nid(), n, ok, s)) }
                if (reply.isNotBlank()) messages.add(UiMsg.Bot(nid(), reply))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.add(UiMsg.Err(nid(), e.message ?: "خطأ غير معروف"))
            } finally {
                busy = false
                statusText = ""
            }
        }
        return true
    }
}
