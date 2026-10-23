package com.nocturne.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** Reads only the device's local media index; it does not upload audio or metadata. */
object LocalLibraryScanner {
    fun scan(context: Context): List<LocalTrack> {
        val resolver = context.contentResolver
        val volumeName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.VOLUME_EXTERNAL else "external"
        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(volumeName)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val genresByTrackId = queryGenres(context, volumeName)
        val projection = buildList {
            addAll(
                listOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.YEAR,
                    MediaStore.Audio.Media.MIME_TYPE,
                    MediaStore.Audio.Media.IS_MUSIC
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.MediaColumns.COMPOSER)
        }.toTypedArray()
        val result = mutableListOf<LocalTrack>()
        val favoriteIds = FavoritesStore.all(context)
        val cachedMetadata = MetadataStore.all(context)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        resolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val composerCol = cursor.getColumnIndex(MediaStore.MediaColumns.COMPOSER)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val audioUri = ContentUris.withAppendedId(collection, id)
                val artUri = if (albumId > 0L) {
                    Uri.parse("content://media/external/audio/albumart/$albumId")
                } else null
                val year = if (cursor.isNull(yearCol)) null else cursor.getInt(yearCol).takeIf { it > 0 }
                result += LocalTrack(
                    id = id,
                    title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Unknown title",
                    artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist",
                    album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown album",
                    durationMs = cursor.getLong(durationCol),
                    uri = audioUri,
                    albumArtUri = artUri,
                    year = year,
                    mimeType = if (cursor.isNull(mimeCol)) null else cursor.getString(mimeCol),
                    extra = run {
                        val localComposers = if (composerCol >= 0) {
                            cursor.getString(composerCol)?.split(Regex("[;,]"))?.map(String::trim)
                                ?.filter(String::isNotBlank).orEmpty()
                        } else emptyList()
                        val localGenre = genresByTrackId[id]
                        val cached = cachedMetadata[id]
                        if (cached == null) {
                            TrackExtraMetadata(
                                genre = localGenre,
                                composers = localComposers,
                                source = if (localGenre != null || localComposers.isNotEmpty()) "MediaStore" else null
                            )
                        } else if ((cached.genre.isNullOrBlank() && localGenre != null) ||
                            (cached.composers.isEmpty() && localComposers.isNotEmpty())
                        ) {
                            cached.copy(
                                genre = cached.genre?.takeIf(String::isNotBlank) ?: localGenre,
                                composers = cached.composers.ifEmpty { localComposers },
                                source = cached.source ?: "MediaStore"
                            )
                        } else cached
                    },
                    isFavorite = id in favoriteIds
                )
            }
        }
        return result
    }

    /** Reads genre membership from the device's MediaStore index without opening or uploading audio files. */
    private fun queryGenres(context: Context, volumeName: String): Map<Long, String> {
        val genresByTrack = mutableMapOf<Long, LinkedHashSet<String>>()
        runCatching {
            val resolver = context.contentResolver
            resolver.query(
                MediaStore.Audio.Genres.getContentUri(volumeName),
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null, null, MediaStore.Audio.Genres.NAME
            )?.use { genres ->
                val idColumn = genres.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
                val nameColumn = genres.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
                while (genres.moveToNext()) {
                    val genreId = genres.getLong(idColumn)
                    val name = genres.getString(nameColumn)?.trim()?.takeIf(String::isNotBlank) ?: continue
                    resolver.query(
                        MediaStore.Audio.Genres.Members.getContentUri(volumeName, genreId),
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                        null, null, null
                    )?.use { members ->
                        val audioIdColumn = members.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.AUDIO_ID)
                        while (members.moveToNext()) {
                            val audioId = members.getLong(audioIdColumn)
                            genresByTrack.getOrPut(audioId) { linkedSetOf() }.add(name)
                        }
                    }
                }
            }
        }
        return genresByTrack.mapValues { (_, names) -> names.joinToString(", ") }
    }
}
