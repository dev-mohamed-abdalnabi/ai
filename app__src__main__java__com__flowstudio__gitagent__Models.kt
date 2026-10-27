package com.flowstudio.gitagent

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ModelInfo(val id: String, val name: String, val ctx: Int, val free: Boolean, val tools: Boolean)

object Models {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    fun parse(raw: String): List<ModelInfo> {
        val arr = JSONObject(raw).getJSONArray("data")
        val out = ArrayList<ModelInfo>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("id")
            if (id.isBlank()) continue
            val pr = o.optJSONObject("pricing")
            val free = id.endsWith(":free") ||
                (pr != null && pr.optString("prompt") == "0" && pr.optString("completion") == "0")
            val sp = o.optJSONArray("supported_parameters")
            var tools = true // unknown => don't hide it
            if (sp != null) {
                tools = false
                for (j in 0 until sp.length()) if (sp.optString(j) == "tools") tools = true
            }
            out.add(ModelInfo(id, o.optString("name", id), o.optInt("context_length", 0), free, tools))
        }
        return out.sortedBy { it.name.lowercase() }
    }

    fun readCache(dir: File): List<ModelInfo> = try {
        val f = File(dir, "models_cache.json")
        if (f.exists()) parse(f.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /** Blocking: call from Dispatchers.IO. */
    fun fetch(dir: File): List<ModelInfo> {
        val req = Request.Builder().url("https://openrouter.ai/api/v1/models")
            .header("User-Agent", "FlowAgent").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val raw = r.body?.string() ?: ""
            val list = parse(raw)
            if (list.isEmpty()) throw IOException("empty list")
            File(dir, "models_cache.json").writeText(raw)
            return list
        }
    }
}
