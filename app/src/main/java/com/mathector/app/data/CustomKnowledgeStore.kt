package com.mathector.app.data

import android.annotation.SuppressLint
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The question database stores names; register the persisted taxonomy before reading questions. */
class CustomKnowledgeStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("mathector-knowledge", Context.MODE_PRIVATE)

    fun reload() = synchronized(lock) { KnowledgeCatalog.installCustomPoints(read()) }

    // The KTX edit extension does not expose commit success; publish only after the disk write succeeds.
    @SuppressLint("UseKtx")
    fun add(group: String, name: String): CustomKnowledgePoint = synchronized(lock) {
        val current = read()
        val point = KnowledgeCatalog.validateCustomPoint(group, name, current)
        val updated = current + point
        val serialized = JSONArray().apply {
            updated.forEach { put(JSONObject().put("group", it.group).put("name", it.name)) }
        }.toString()
        check(preferences.edit().putString("custom-points", serialized).commit()) { "知识点保存失败，请重试" }
        KnowledgeCatalog.installCustomPoints(updated)
        point
    }

    private fun read(): List<CustomKnowledgePoint> = runCatching {
        val array = JSONArray(preferences.getString("custom-points", "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                runCatching { KnowledgeCatalog.validateCustomPoint(entry.optString("group"), entry.optString("name"), this) }
                    .getOrNull()?.let { add(it) }
            }
        }
    }.getOrDefault(emptyList())

    companion object { private val lock = Any() }
}
