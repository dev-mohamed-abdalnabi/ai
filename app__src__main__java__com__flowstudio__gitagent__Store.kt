package com.flowstudio.gitagent

import android.content.Context

class Store(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("flow_agent", Context.MODE_PRIVATE)

    var orKey: String
        get() = p.getString("or_key", "") ?: ""
        set(v) { p.edit().putString("or_key", v.trim()).apply() }

    var ghToken: String
        get() = p.getString("gh_token", "") ?: ""
        set(v) { p.edit().putString("gh_token", v.trim()).apply() }

    var model: String
        get() = p.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) { p.edit().putString("model", v.trim().ifBlank { DEFAULT_MODEL }).apply() }

    companion object {
        const val DEFAULT_MODEL = "openai/gpt-4o-mini"
    }
}
