package com.nocturne.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nocturne.player.data.FavoritesStore
import com.nocturne.player.data.LocalLibraryScanner
import com.nocturne.player.data.LocalTrack
import com.nocturne.player.data.StemType
import com.nocturne.player.playback.MusicPlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

private val Night = Color(0xFF080D19)
private val Panel = Color(0xFF101827)
private val PanelSoft = Color(0xFF151F31)
private val Mint = Color(0xFFA6F0DC)
private val Periwinkle = Color(0xFF92A9FF)
private val Ivory = Color(0xFFF4F2ED)
private val Muted = Color(0xFF929BAD)
private val ThinLine = Color(0x1AFFFFFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = false
        setContent { NocturneApp() }
    }
}

@Composable
private fun NocturneApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf<List<LocalTrack>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("YOUR MUSIC. YOUR SPACE.") }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentId by remember { mutableStateOf<String?>(null) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }
    var seekDraft by remember { mutableFloatStateOf(0f) }
    var section by remember { mutableStateOf("MUSIC") }
    var libraryFilter by remember { mutableStateOf("ALL") }
    var query by remember { mutableStateOf("") }
    var shuffleOn by remember { mutableStateOf(false) }
    var repeatOn by remember { mutableStateOf(false) }

    val controllerFuture = remember(context) {
        MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        ).buildAsync()
    }
    DisposableEffect(controllerFuture) {
        controllerFuture.addListener({
            runCatching { controllerFuture.get() }
                .onSuccess { connected ->
                    controller = connected
                    currentId = connected.currentMediaItem?.mediaId
                    isPlaying = connected.isPlaying
                }
                .onFailure { status = "پخش‌کننده آماده نشد؛ برنامه را دوباره باز کن." }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            controller?.release()
            controller = null
        }
    }

    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun scanLibrary() {
        if (scanning) return
        scope.launch {
            scanning = true
            status = "در حال اسکن کتابخانهٔ موسیقی…"
            try {
                tracks = withContext(Dispatchers.IO) { LocalLibraryScanner.scan(context) }
                status = if (tracks.isEmpty()) "فایلی پیدا نشد؛ دسترسی و پوشهٔ Music را بررسی کن." else "${tracks.size} آهنگ محلی پیدا شد · آفلاین"
            } catch (error: Exception) {
                status = "اسکن انجام نشد. دسترسی فایل‌های صوتی را بررسی کن."
            } finally {
                scanning = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanLibrary() else status = "برای نمایش آهنگ‌های گوشی، دسترسی صوتی لازم است."
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            scanLibrary()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    DisposableEffect(controller) {
        val c = controller
        if (c == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentId = mediaItem?.mediaId
                positionMs = 0
                seekDraft = 0f
                durationMs = (c.duration.takeUnless { it == C.TIME_UNSET } ?: 0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                durationMs = (c.duration.takeUnless { it == C.TIME_UNSET } ?: 0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                isPlaying = c.isPlaying
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                durationMs = (c.duration.takeUnless { it == C.TIME_UNSET } ?: 0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
        }
        c.addListener(listener)
        onDispose { c.removeListener(listener) }
    }

    LaunchedEffect(controller, isPlaying, currentId) {
        while (controller != null) {
            val c = controller
            if (c != null) {
                positionMs = c.currentPosition.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                val d = c.duration
                if (d != C.TIME_UNSET && d >= 0L) durationMs = d.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            delay(if (isPlaying) 450L else 900L)
        }
    }

    val currentTrack = tracks.firstOrNull { it.id.toString() == currentId }
    val availableGenres = tracks.flatMap { track ->
        track.extra.genre.orEmpty().split(",").map { it.trim() }.filter(String::isNotBlank)
    }.distinctBy { it.lowercase(Locale.ROOT) }.sortedBy { it.lowercase(Locale.ROOT) }
    val visibleTracks = tracks.filter { track ->
        val matchesFilter = when {
            libraryFilter == "LIKED" -> track.isFavorite
            libraryFilter.startsWith("GENRE:") -> track.extra.genre.orEmpty().split(",")
                .any { it.trim().equals(libraryFilter.removePrefix("GENRE:"), ignoreCase = true) }
            else -> true
        }
        val q = query.trim().lowercase(Locale.getDefault())
        matchesFilter && (q.isBlank() || listOf(track.title, track.artist, track.album, track.extra.genre.orEmpty(), track.extra.composers.joinToString(" "))
            .any { it.lowercase(Locale.getDefault()).contains(q) })
    }

    fun playTrack(track: LocalTrack) {
        val c = controller
        if (c == null) {
            status = "پلیر در حال آماده‌شدن است؛ یک لحظه دیگر تلاش کن."
            return
        }
        val mediaItems = tracks.map { item ->
            val metadata = MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.artist)
                .setAlbumTitle(item.album)
                .setArtworkUri(item.albumArtUri)
                .build()
            MediaItem.Builder()
                .setMediaId(item.id.toString())
                .setUri(item.uri)
                .setMediaMetadata(metadata)
                .build()
        }
        val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        c.setMediaItems(mediaItems, index, 0L)
        c.prepare()
        c.play()
        currentId = track.id.toString()
        isPlaying = true
        status = "در حال پخش از کتابخانهٔ محلی"
    }

    fun toggleFavorite(track: LocalTrack) {
        val liked = FavoritesStore.toggle(context, track.id)
        tracks = tracks.map { if (it.id == track.id) it.copy(isFavorite = liked) else it }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Mint,
        secondary = Periwinkle,
        background = Night,
        surface = Panel,
        onPrimary = Night,
        onBackground = Ivory,
        onSurface = Ivory
    )) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF17243A), Night, Color(0xFF080D19))))
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = if (currentTrack != null) 120.dp else 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Header(
                        scanning = scanning,
                        onScan = {
                            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) scanLibrary()
                            else permissionLauncher.launch(permission)
                        }
                    )
                }
                item {
                    NowPlayingCard(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onPlayPause = {
                            val c = controller
                            if (c == null) return@NowPlayingCard
                            if (c.isPlaying) c.pause() else if (c.currentMediaItem == null) tracks.firstOrNull()?.let(::playTrack) else c.play()
                        },
                        onPrevious = { controller?.seekToPreviousMediaItem() },
                        onNext = { controller?.seekToNextMediaItem() },
                        onSeek = { fraction -> controller?.let { c -> if (durationMs > 0) c.seekTo((fraction * durationMs).toLong()) } },
                        shuffleOn = shuffleOn,
                        repeatOn = repeatOn,
                        onShuffle = { shuffleOn = !shuffleOn; controller?.shuffleModeEnabled = shuffleOn },
                        onRepeat = { repeatOn = !repeatOn; controller?.repeatMode = if (repeatOn) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF },
                        onFavorite = { currentTrack?.let(::toggleFavorite) }
                    )
                }
                item { SectionTabs(section = section, onSelect = { section = it }) }
                when (section) {
                    "MUSIC" -> {
                        item {
                            LibraryHeader(
                                count = tracks.size,
                                status = status,
                                query = query,
                                onQuery = { query = it },
                                filter = libraryFilter,
                                onFilter = { libraryFilter = it },
                                genres = availableGenres
                            )
                        }
                        if (scanning && tracks.isEmpty()) {
                            item { LoadingCard() }
                        } else if (visibleTracks.isEmpty()) {
                            item {
                                EmptyLibraryCard(
                                    hasTracks = tracks.isNotEmpty(),
                                    onScan = {
                                        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) scanLibrary()
                                        else permissionLauncher.launch(permission)
                                    }
                                )
                            }
                        } else {
                            items(visibleTracks, key = { it.id }) { track ->
                                TrackRow(
                                    track = track,
                                    isCurrent = track.id.toString() == currentId,
                                    onClick = { playTrack(track) },
                                    onFavorite = { toggleFavorite(track) }
                                )
                            }
                        }
                    }
                    "MUSIC DNA" -> item { MusicDnaPanel(trackCount = tracks.size, tracks = tracks) }
                    else -> item { StemLabPanel() }
                }
                item {
                    Text(
                        text = "NOCTURNE  •  OFFLINE FIRST",
                        color = Muted.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            if (currentTrack != null) {
                MiniPlayer(
                    track = currentTrack,
                    isPlaying = isPlaying,
                    onPlayPause = { if (controller?.isPlaying == true) controller?.pause() else controller?.play() },
                    onNext = { controller?.seekToNextMediaItem() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun Header(scanning: Boolean, onScan: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Mint, Periwinkle))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.MusicNote, contentDescription = null, tint = Night, modifier = Modifier.size(27.dp)) }
        Column(modifier = Modifier.weight(1f)) {
            Text("NOCTURNE", color = Ivory, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
            Text("YOUR MUSIC. YOUR SPACE.", color = Muted, fontSize = 9.sp, letterSpacing = 1.7.sp)
        }
        IconButton(onClick = onScan, enabled = !scanning) {
            if (scanning) CircularProgressIndicator(modifier = Modifier.size(19.dp), strokeWidth = 2.dp, color = Mint)
            else Icon(Icons.Default.Refresh, contentDescription = "Scan library", tint = Mint)
        }
    }
}

@Composable
private fun NowPlayingCard(
    track: LocalTrack?,
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    shuffleOn: Boolean,
    repeatOn: Boolean,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onFavorite: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = 0.94f)),
        modifier = Modifier.fillMaxWidth().border(1.dp, ThinLine, RoundedCornerShape(30.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("NOW PLAYING", color = Mint, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                    Text("LOCAL LIBRARY", color = Muted, fontSize = 9.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 3.dp))
                }
                if (track != null) {
                    IconButton(onClick = onFavorite) {
                        Icon(if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favorite", tint = if (track.isFavorite) Color(0xFFF1A6BD) else Muted)
                    }
                }
            }
            Spacer(Modifier.height(17.dp))
            AlbumArtwork(track?.albumArtUri, modifier = Modifier.size(210.dp))
            Spacer(Modifier.height(19.dp))
            Text(
                text = track?.title ?: "شبِ خودت را انتخاب کن",
                color = Ivory,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                text = track?.artist ?: "موسیقی‌ات را اضافه کن و از لحظه لذت ببر",
                color = Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp)
            )
            if (track != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 11.dp)) {
                    MetadataPill(track.extra.genre ?: "GENRE · —")
                    MetadataPill(track.extra.bpm?.let { "${it.toInt()} BPM" } ?: "BPM · —")
                    MetadataPill(track.extra.musicalKey ?: "KEY · —")
                }
            }
            Spacer(Modifier.height(18.dp))
            Waveform(isPlaying = isPlaying, modifier = Modifier.fillMaxWidth().height(35.dp))
            Slider(
                value = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                onValueChange = onSeek,
                enabled = track != null && durationMs > 0,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = Mint,
                    activeTrackColor = Mint,
                    inactiveTrackColor = Color(0xFF344156)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(positionMs.toLong()), color = Muted, fontSize = 10.sp)
                Text(formatTime(durationMs.toLong()), color = Muted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onShuffle) { Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (shuffleOn) Mint else Muted) }
                IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Ivory, modifier = Modifier.size(31.dp)) }
                Box(
                    modifier = Modifier.size(60.dp).clip(CircleShape).background(Mint).clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play", tint = Night, modifier = Modifier.size(34.dp)) }
                IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Ivory, modifier = Modifier.size(31.dp)) }
                IconButton(onClick = onRepeat) { Icon(Icons.Default.Repeat, contentDescription = "Repeat", tint = if (repeatOn) Mint else Muted) }
            }
        }
    }
}

@Composable
private fun AlbumArtwork(uri: Uri?, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input) }
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier.clip(RoundedCornerShape(24.dp)).background(
            Brush.linearGradient(listOf(Color(0xFFC5D8D1), Color(0xFF445B75), Color(0xFF101B30)))
        ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "Album artwork", modifier = Modifier.fillMaxSize())
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val minSide = size.minDimension
                for (i in 1..10) {
                    drawCircle(
                        color = Color(0xFF081321).copy(alpha = 0.10f + i * 0.012f),
                        radius = minSide * i / 22f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx()),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.47f)
                    )
                }
                drawCircle(color = Mint.copy(alpha = 0.8f), radius = minSide * 0.035f)
            }
            Text("N", color = Ivory, fontSize = 73.sp, fontWeight = FontWeight.Light, modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 12.dp))
        }
    }
}

@Composable
private fun Waveform(isPlaying: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val count = 52
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        for (i in 0 until count) {
            val wave = abs(sin(i * 0.49)).toFloat()
            val h = size.height * (0.18f + wave * if (isPlaying) 0.76f else 0.48f)
            val x = i * (barWidth + gap)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Mint, Periwinkle.copy(alpha = 0.7f))),
                topLeft = androidx.compose.ui.geometry.Offset(x, (size.height - h) / 2),
                size = androidx.compose.ui.geometry.Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth, barWidth),
                alpha = if (i < 18 && isPlaying) 0.95f else 0.50f
            )
        }
    }
}

@Composable
private fun MetadataPill(text: String) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.045f)).border(1.dp, ThinLine, RoundedCornerShape(999.dp)).padding(horizontal = 9.dp, vertical = 6.dp)
    ) { Text(text.uppercase(), color = Muted, fontSize = 9.sp, letterSpacing = 0.6.sp, maxLines = 1) }
}

@Composable
private fun SectionTabs(section: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        val tabs = listOf("MUSIC" to Icons.Default.LibraryMusic, "MUSIC DNA" to Icons.Default.GraphicEq, "STEM LAB" to Icons.Default.QueueMusic)
        tabs.forEach { (label, icon) ->
            val selected = section == label
            Surface(
                shape = RoundedCornerShape(13.dp),
                color = if (selected) Mint.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.035f),
                modifier = Modifier.weight(1f).border(1.dp, if (selected) Mint.copy(alpha = 0.28f) else ThinLine, RoundedCornerShape(13.dp)).clickable { onSelect(label) }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)) {
                    Icon(icon, contentDescription = null, tint = if (selected) Mint else Muted, modifier = Modifier.size(19.dp))
                    Text(label, color = if (selected) Mint else Muted, fontSize = 9.sp, letterSpacing = 0.7.sp, modifier = Modifier.padding(top = 5.dp), maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryHeader(
    count: Int,
    status: String,
    query: String,
    onQuery: (String) -> Unit,
    filter: String,
    onFilter: (String) -> Unit,
    genres: List<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Your library", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ivory)
                Text(status, color = Muted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(count.toString(), color = Mint, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Text("TRACKS", color = Muted, fontSize = 8.sp, letterSpacing = 1.4.sp)
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search songs, artists, albums…", color = Muted, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Muted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Ivory,
                unfocusedTextColor = Ivory,
                focusedBorderColor = Mint.copy(alpha = 0.65f),
                unfocusedBorderColor = ThinLine,
                cursorColor = Mint,
                focusedContainerColor = Color.White.copy(alpha = 0.025f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.025f)
            ),
            shape = RoundedCornerShape(15.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(selected = filter == "ALL", onClick = { onFilter("ALL") }, label = { Text("All tracks") })
            }
            item {
                FilterChip(selected = filter == "LIKED", onClick = { onFilter("LIKED") }, label = { Text("♥ Favourites") })
            }
            items(genres, key = { it }) { genre ->
                val genreFilter = "GENRE:" + genre.lowercase(Locale.ROOT)
                FilterChip(
                    selected = filter == genreFilter,
                    onClick = { onFilter(genreFilter) },
                    label = { Text(genre, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
    }
}

@Composable
private fun TrackRow(track: LocalTrack, isCurrent: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    Surface(
        color = if (isCurrent) Mint.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.025f),
        shape = RoundedCornerShape(17.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, if (isCurrent) Mint.copy(alpha = 0.19f) else ThinLine, RoundedCornerShape(17.dp)).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            AlbumArtwork(track.albumArtUri, Modifier.size(48.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, color = if (isCurrent) Mint else Ivory, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                Text(track.album, color = Muted.copy(alpha = 0.72f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
            Text(formatTime(track.durationMs), color = Muted, fontSize = 10.sp)
            IconButton(onClick = onFavorite, modifier = Modifier.size(34.dp)) {
                Icon(if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Toggle favourite", tint = if (track.isFavorite) Color(0xFFF1A6BD) else Muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(hasTracks: Boolean, onScan: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (hasTracks) Icons.Default.Search else Icons.Default.Album, contentDescription = null, tint = Mint, modifier = Modifier.size(34.dp))
            Text(if (hasTracks) "No matches" else "Your library is waiting", color = Ivory, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 11.dp))
            Text(if (hasTracks) "Try a different search or show all tracks." else "Scan local audio files to start listening.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            if (!hasTracks) {
                Button(onClick = onScan, colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Night), modifier = Modifier.padding(top = 16.dp)) {
                    Text("Scan music library", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = Mint, strokeWidth = 2.dp, modifier = Modifier.size(23.dp))
            Column {
                Text("Scanning local music", color = Ivory, fontWeight = FontWeight.SemiBold)
                Text("Nothing is being uploaded.", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MusicDnaPanel(trackCount: Int, tracks: List<LocalTrack>) {
    val identifiedCount = tracks.count { it.extra.genre != null || it.extra.bpm != null || it.extra.musicalKey != null || it.extra.composers.isNotEmpty() }
    val genreCounts = tracks.flatMap { track ->
        track.extra.genre.orEmpty().split(",").map { it.trim() }.filter(String::isNotBlank)
    }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5)
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Text("Music DNA", color = Ivory, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Turn a folder of files into a searchable map of your musical taste.", color = Muted, fontSize = 12.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(23.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Mint, modifier = Modifier.size(28.dp))
                Text("Metadata enrichment is the next engine to connect", color = Ivory, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Genre tags from the device media index are available offline. Online track identification and audio analysis are not connected yet.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(2.dp))
                DnaStat("LOCAL TRACKS SCANNED", trackCount.toString())
                DnaStat("TRACKS WITH LOCAL DNA DATA", identifiedCount.toString())
                DnaStat("LOCAL GENRES FOUND", genreCounts.size.toString())
                DnaStat("TRACKS WITH COMPOSER TAGS", tracks.count { it.extra.composers.isNotEmpty() }.toString())
                genreCounts.forEach { (genre, count) ->
                    DnaStat(genre.uppercase(Locale.getDefault()), "$count TRACKS")
                }
                DnaStat("OFFLINE AUDIO ANALYSIS", "MODEL NOT CONNECTED")
            }
        }
        Text("Planned filters", color = Ivory, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetadataPill("GENRE"); MetadataPill("BPM RANGE"); MetadataPill("KEY") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetadataPill("INSTRUMENTS"); MetadataPill("ENERGY"); MetadataPill("MOOD") }
        Text("Filters become active after the metadata providers and on-device audio analysis model are integrated.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun DnaStat(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 0.7.sp, modifier = Modifier.weight(1f))
        Text(value, color = Mint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StemLabPanel() {
    val stems = listOf(
        StemType.VOCALS to "Vocals",
        StemType.DRUMS to "Drums & percussion",
        StemType.BASS to "Bass",
        StemType.OTHER to "Other instruments",
        StemType.GUITAR to "Guitar (advanced)",
        StemType.PIANO to "Piano (advanced)",
        StemType.STRINGS to "Strings (advanced)"
    )
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Text("Stem Lab", color = Ivory, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Separate a finished song into parts for practice, remixing and deeper listening.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(23.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Mint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Mint, modifier = Modifier.size(28.dp))
                }
                Text("AI stem separation", color = Ivory, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("The stem engine is planned, but no separation model is bundled or running yet. We will integrate and benchmark a licensed model before enabling this button.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                stems.forEach { (type, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (type.ordinal < 4) Mint else Periwinkle))
                        Text(label, color = Ivory, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(if (type.ordinal < 4) "CORE TARGET" else "ADVANCED", color = Muted, fontSize = 8.sp, letterSpacing = 0.5.sp)
                    }
                }
                Surface(color = Color(0xFF201F2A), shape = RoundedCornerShape(12.dp)) {
                    Text("MODEL NOT CONNECTED", color = Color(0xFFE8C99B), fontSize = 10.sp, letterSpacing = 1.1.sp, modifier = Modifier.fillMaxWidth().padding(13.dp))
                }
            }
        }
        Text("Design requirement: normal playback must remain light and responsive. Stem separation will run only when requested and will be tested for on-device memory, heat and battery use.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun MiniPlayer(track: LocalTrack, isPlaying: Boolean, onPlayPause: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xF00B111D),
        tonalElevation = 8.dp,
        modifier = modifier.fillMaxWidth().navigationBarsPadding().border(1.dp, ThinLine)
    ) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AlbumArtwork(track.albumArtUri, Modifier.size(42.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, color = Ivory, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
            IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play or pause", tint = Mint) }
            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, contentDescription = "Next track", tint = Ivory) }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    if (milliseconds <= 0L) return "0:00"
    val seconds = milliseconds / 1000
    return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}
