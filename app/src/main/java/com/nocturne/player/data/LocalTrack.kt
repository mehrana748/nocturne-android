package com.nocturne.player.data

import android.net.Uri

/** A single audio item indexed by Android's MediaStore. */
data class LocalTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri,
    val albumArtUri: Uri?,
    val year: Int?,
    val mimeType: String?,
    val extra: TrackExtraMetadata = TrackExtraMetadata(),
    val isFavorite: Boolean = false
)

data class TrackExtraMetadata(
    val genre: String? = null,
    val subgenres: List<String> = emptyList(),
    val bpm: Double? = null,
    val musicalKey: String? = null,
    val mode: String? = null,
    val instruments: List<String> = emptyList(),
    val composers: List<String> = emptyList(),
    val producers: List<String> = emptyList(),
    val source: String? = null,
    val confidence: Float? = null,
    val analyzedAtEpochMs: Long? = null
)
