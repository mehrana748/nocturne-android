package com.nocturne.player.data

import android.content.Context

object FavoritesStore {
    private const val PREFS = "nocturne_favorites_v1"
    private const val KEY_IDS = "favorite_media_ids"

    fun all(context: Context): Set<Long> = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getStringSet(KEY_IDS, emptySet())
        .orEmpty()
        .mapNotNull { it.toLongOrNull() }
        .toSet()

    fun isFavorite(context: Context, id: Long): Boolean = id in all(context)

    fun toggle(context: Context, id: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
        val key = id.toString()
        val nowFavorite = if (ids.contains(key)) {
            ids.remove(key)
            false
        } else {
            ids.add(key)
            true
        }
        prefs.edit().putStringSet(KEY_IDS, ids).apply()
        return nowFavorite
    }
}
