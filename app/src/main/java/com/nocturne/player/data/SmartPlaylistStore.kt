package com.nocturne.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Stores named, rule-based playlists locally; rules are re-evaluated against the current library. */
data class SmartPlaylist(
    val id: String,
    val name: String,
    val filterRules: List<String>
)

object SmartPlaylistStore {
    private const val PREFS = "nocturne_smart_playlists_v1"
    private const val KEY_PLAYLISTS = "playlists"

    fun all(context: Context): List<SmartPlaylist> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
                    val name = item.optString("name").trim().takeIf(String::isNotBlank) ?: continue
                    val rules = item.optJSONArray("filterRules")?.let { array ->
                        buildList {
                            for (ruleIndex in 0 until array.length()) {
                                array.optString(ruleIndex).takeIf(String::isNotBlank)?.let(::add)
                            }
                        }
                    } ?: item.optString("filterRule").takeIf(String::isNotBlank)?.let(::listOf)
                    if (rules.isNullOrEmpty()) continue
                    add(SmartPlaylist(id, name, rules))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, playlist: SmartPlaylist) {
        save(context, all(context).filterNot { it.id == playlist.id } + playlist)
    }

    fun remove(context: Context, id: String) {
        save(context, all(context).filterNot { it.id == id })
    }

    fun update(context: Context, playlist: SmartPlaylist) {
        save(context, all(context).map { if (it.id == playlist.id) playlist else it })
    }

    fun move(context: Context, id: String, offset: Int) {
        val playlists = all(context).toMutableList()
        val from = playlists.indexOfFirst { it.id == id }
        if (from < 0 || playlists.isEmpty()) return
        val to = (from + offset).coerceIn(0, playlists.lastIndex)
        if (from == to) return
        val playlist = playlists.removeAt(from)
        playlists.add(to, playlist)
        save(context, playlists)
    }

    private fun save(context: Context, playlists: List<SmartPlaylist>) {
        val array = JSONArray()
        playlists.forEach { playlist ->
            array.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("filterRules", JSONArray(playlist.filterRules))
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PLAYLISTS, array.toString())
            .apply()
    }
}
