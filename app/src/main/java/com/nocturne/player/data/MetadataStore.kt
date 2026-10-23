package com.nocturne.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local cache for future metadata enrichment. In this first build it stores structure only;
 * no remote identification provider or AI analyzer has been connected yet.
 */
object MetadataStore {
    private const val PREFS = "nocturne_music_dna_v1"

    fun get(context: Context, mediaId: Long): TrackExtraMetadata {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(mediaId.toString(), null)
            ?: return TrackExtraMetadata()
        return decode(raw)
    }

    /** Load the local cache once before a scan instead of reading SharedPreferences for each row. */
    fun all(context: Context): Map<Long, TrackExtraMetadata> {
        val values = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
        return values.mapNotNull { (key, value) ->
            val id = key.toLongOrNull() ?: return@mapNotNull null
            val raw = value as? String ?: return@mapNotNull null
            id to decode(raw)
        }.toMap()
    }

    private fun decode(raw: String): TrackExtraMetadata = runCatching {
            val obj = JSONObject(raw)
            TrackExtraMetadata(
                genre = obj.optString("genre").takeIf(String::isNotBlank),
                subgenres = obj.optJSONArray("subgenres").toStringList(),
                bpm = obj.optDouble("bpm").takeIf { !it.isNaN() && it > 0.0 },
                musicalKey = obj.optString("musicalKey").takeIf(String::isNotBlank),
                mode = obj.optString("mode").takeIf(String::isNotBlank),
                instruments = obj.optJSONArray("instruments").toStringList(),
                composers = obj.optJSONArray("composers").toStringList(),
                producers = obj.optJSONArray("producers").toStringList(),
                source = obj.optString("source").takeIf(String::isNotBlank),
                confidence = obj.optDouble("confidence").takeIf { !it.isNaN() && it in 0.0..1.0 }?.toFloat(),
                analyzedAtEpochMs = obj.optLong("analyzedAtEpochMs").takeIf { it > 0 }
            )
        }.getOrDefault(TrackExtraMetadata())

    fun save(context: Context, mediaId: Long, data: TrackExtraMetadata) {
        val obj = JSONObject()
            .put("genre", data.genre ?: "")
            .put("subgenres", JSONArray(data.subgenres))
            .put("bpm", data.bpm ?: JSONObject.NULL)
            .put("musicalKey", data.musicalKey ?: "")
            .put("mode", data.mode ?: "")
            .put("instruments", JSONArray(data.instruments))
            .put("composers", JSONArray(data.composers))
            .put("producers", JSONArray(data.producers))
            .put("source", data.source ?: "")
            .put("confidence", data.confidence ?: JSONObject.NULL)
            .put("analyzedAtEpochMs", data.analyzedAtEpochMs ?: 0L)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(mediaId.toString(), obj.toString()).apply()
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList { for (i in 0 until length()) optString(i).takeIf(String::isNotBlank)?.let(::add) }
    }
}
