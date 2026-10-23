package com.nocturne.player.data

import android.content.Context
import android.net.Uri

/** Adapter contract for a legal metadata-identification provider (for example AcoustID/MusicBrainz). */
interface MetadataEnrichmentProvider {
    suspend fun identify(context: Context, audioUri: Uri, localTrack: LocalTrack): TrackExtraMetadata
}

/** Adapter contract for an on-device or optional cloud stem-separation model. */
interface StemSeparationEngine {
    val supportedStems: Set<StemType>
    suspend fun separate(context: Context, audioUri: Uri, outputDirectory: String): StemSeparationResult
}

enum class StemType { VOCALS, DRUMS, BASS, OTHER, GUITAR, PIANO, STRINGS }

data class StemSeparationResult(
    val outputFiles: Map<StemType, String>,
    val modelName: String,
    val elapsedMs: Long
)
