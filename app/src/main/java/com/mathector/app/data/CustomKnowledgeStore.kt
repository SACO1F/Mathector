package com.mathector.app.data

import android.annotation.SuppressLint
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The question database stores names; register the persisted taxonomy before reading questions. */
class CustomKnowledgeStore(context: Context, name: String = "mathector-knowledge") {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun reload() = synchronized(lock) { KnowledgeCatalog.installCustomPoints(read()) }
    fun snapshot(): List<CustomKnowledgePoint> = synchronized(lock) { read() }

    @SuppressLint("UseKtx")
    fun merge(points: List<CustomKnowledgePoint>): List<CustomKnowledgePoint> = synchronized(lock) {
        val current = read().toMutableList()
        val added = mutableListOf<CustomKnowledgePoint>()
        points.forEach { point ->
            if(current.none { it.name == point.name }) {
                val valid = KnowledgeCatalog.validateCustomPoint(point.group, point.name, current)
                current += valid; added += valid
            }
        }
        write(current)
        KnowledgeCatalog.installCustomPoints(current)
        added
    }

    /** Roll back only points inserted by a failed restore, retaining unrelated local additions. */
    fun rollbackMerge(added: List<CustomKnowledgePoint>) = synchronized(lock) {
        if(added.isNotEmpty()) {
            val remaining = read().filter { it !in added }
            write(remaining)
            KnowledgeCatalog.installCustomPoints(remaining)
        }
    }

    @SuppressLint("UseKtx")
    private fun write(points: List<CustomKnowledgePoint>) {
        val serialized = JSONArray().apply { points.forEach { put(JSONObject().put("group", it.group).put("name", it.name)) } }.toString()
        check(preferences.edit().putString("custom-points", serialized).commit()) { "知识点保存失败，请重试" }
    }

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
