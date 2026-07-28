package com.nothing.lyricwidget

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nothing.lyricwidget.api.LrcLibClient
import com.nothing.lyricwidget.model.LyricLine
import com.nothing.lyricwidget.service.MusicDetectionService
import com.nothing.lyricwidget.service.MusicNotificationListenerService
import com.nothing.lyricwidget.service.GlyphSongController
import com.nothing.lyricwidget.utils.LyricRepository
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var glyphSongController: GlyphSongController
    private val trackNameState = mutableStateOf("")
    private val artistNameState = mutableStateOf("")
    private val albumNameState = mutableStateOf("")
    private val lyricIndexState = mutableIntStateOf(-1)
    private val lyricLinesState = mutableStateOf<List<LyricLine>>(emptyList())
    private val albumArtState = mutableStateOf<Bitmap?>(null)
    private val isPlayingState = mutableStateOf(false)
    private val detectionMethodState = mutableStateOf("")
    private val lyricsOnlyState = mutableStateOf(false)
    private val aodAlbumArtState = mutableStateOf(false)
    private val aodFontColorState = mutableStateOf(Color.White)
    private val aodFontSizeState = mutableStateOf(30f)
    private val positionMsState = mutableStateOf(0L)
    private val durationMsState = mutableStateOf(0L)
    private val glyphSongEnabledState = mutableStateOf(false)
    private val glyphHardwareReadyState = mutableStateOf(false)
    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            glyphSongEnabledState.value = true
        } else {
            Toast.makeText(this, "Audio permission is required for rhythm-synced Glyphs", Toast.LENGTH_LONG).show()
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            trackNameState.value = LyricRepository.currentTrack
            artistNameState.value = LyricRepository.currentArtist
            albumNameState.value = LyricRepository.currentAlbum
            isPlayingState.value = LyricRepository.isPlaying
            lyricLinesState.value = LyricRepository.lyricLines
            albumArtState.value = LyricRepository.currentAlbumArt
            detectionMethodState.value = if (isNotificationListenerEnabled()) "Media session" else "Access required"
            lyricIndexState.intValue = LyricRepository.currentLyricIndex
            // Real elapsed/total time from the mirrored media session (backed by the
            // Media3/ExoPlayer session in AutoMediaService) instead of a static placeholder.
            positionMsState.value = LyricRepository.getPlaybackPositionMs()
            durationMsState.value = LyricRepository.getDurationMs()
            if (::glyphSongController.isInitialized) {
                glyphSongController.update(
                    enabled = glyphSongEnabledState.value,
                    playing = LyricRepository.isPlaying,
                    playbackPositionMs = positionMsState.value
                )
            }
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        glyphSongController = GlyphSongController(applicationContext) { ready ->
            runOnUiThread { glyphHardwareReadyState.value = ready }
        }
        glyphSongController.initialize()
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        val serviceIntent = Intent(this, MusicDetectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)

        setContent {
            MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme(
                background = Color(0xFF161C26),
                surface = Color(0xD9202836),
                primary = Color(0xFF78E3C8),
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                if (lyricsOnlyState.value) {
                    LyricsOnlyScreen(
                        lyricIndex = lyricIndexState.intValue,
                        lyricLines = lyricLinesState.value,
                        albumArt = albumArtState.value,
                        showAlbumArt = aodAlbumArtState.value,
                        lyricColor = aodFontColorState.value,
                        lyricSizeSp = aodFontSizeState.value,
                        onExit = { setLyricsOnlyMode(false) }
                    )
                } else {
                    MainScreen(
                        trackName = trackNameState.value,
                        artistName = artistNameState.value,
                        albumName = albumNameState.value,
                        lyricIndex = lyricIndexState.intValue,
                        lyricLines = lyricLinesState.value,
                        albumArt = albumArtState.value,
                        isPlaying = isPlayingState.value,
                        detectionMethod = detectionMethodState.value,
                        positionMs = positionMsState.value,
                        durationMs = durationMsState.value,
                        onGrantPermissionClick = ::openNotificationAccessSettings,
                        onLyricsOnlyClick = { setLyricsOnlyMode(true) },
                        showAodAlbumArt = aodAlbumArtState.value,
                        onAodAlbumArtClick = { aodAlbumArtState.value = !aodAlbumArtState.value },
                        aodFontColor = aodFontColorState.value,
                        aodFontSizeSp = aodFontSizeState.value,
                        onAodFontColorChange = { aodFontColorState.value = it },
                        onAodFontSizeChange = { aodFontSizeState.value = it },
                        glyphSongEnabled = glyphSongEnabledState.value,
                        glyphHardwareReady = glyphHardwareReadyState.value,
                        onGlyphSongToggle = ::toggleGlyphSong,
                        onPrevious = MusicNotificationListenerService::skipToPrevious,
                        onPlayPause = { if (isPlayingState.value) MusicNotificationListenerService.pause() else MusicNotificationListenerService.play() },
                        onNext = MusicNotificationListenerService::skipToNext
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(pollRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(pollRunnable)
        if (::glyphSongController.isInitialized) {
            glyphSongController.update(false, false, positionMsState.value)
        }
    }

    override fun onDestroy() {
        if (::glyphSongController.isInitialized) glyphSongController.release()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (lyricsOnlyState.value) setLyricsOnlyMode(false) else super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && lyricsOnlyState.value) applyAodWindowMode()
    }

    private fun setLyricsOnlyMode(enabled: Boolean) {
        lyricsOnlyState.value = enabled
        requestedOrientation = if (enabled) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (enabled) {
            applyAodWindowMode()
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }

    private fun applyAodWindowMode() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val component = ComponentName(this, MusicNotificationListenerService::class.java)
        return Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(component.flattenToString()) == true
    }

    private fun openNotificationAccessSettings() {
        try {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", packageName, null)
            })
        }
    }

    private fun toggleGlyphSong() {
        if (glyphSongEnabledState.value) {
            glyphSongEnabledState.value = false
            if (::glyphSongController.isInitialized) {
                glyphSongController.update(false, false, positionMsState.value)
            }
            return
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            glyphSongEnabledState.value = true
        } else {
            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun fetchLyricsManually(track: String, artist: String) {
        Thread {
            val response = LrcLibClient.fetchLyrics(track, artist)
            val lyricsText = response?.syncedLyrics
            runOnUiThread {
                if (lyricsText.isNullOrBlank()) {
                    Toast.makeText(this, "No synced lyrics found for this song", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                LyricRepository.updateTrack(this, track, artist, response.albumName.orEmpty(), (response.duration ?: 0.0).toLong() * 1000, false, 0L)
                LyricRepository.setLyrics(this, lyricsText)
                Toast.makeText(this, "Lyrics loaded", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
}

@Composable
private fun MainScreen(
    trackName: String,
    artistName: String,
    albumName: String,
    lyricIndex: Int,
    lyricLines: List<LyricLine>,
    albumArt: Bitmap?,
    isPlaying: Boolean,
    detectionMethod: String,
    positionMs: Long,
    durationMs: Long,
    onGrantPermissionClick: () -> Unit,
    onLyricsOnlyClick: () -> Unit,
    showAodAlbumArt: Boolean,
    onAodAlbumArtClick: () -> Unit,
    aodFontColor: Color,
    aodFontSizeSp: Float,
    onAodFontColorChange: (Color) -> Unit,
    onAodFontSizeChange: (Float) -> Unit,
    glyphSongEnabled: Boolean,
    glyphHardwareReady: Boolean,
    onGlyphSongToggle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    var volumePreview by remember { mutableStateOf(0.48f) }
    var showSettings by remember { mutableStateOf(false) }
    val discShape = CircleShape

    // Real progress from the mirrored media session, falling back gracefully when
    // no duration is known yet (e.g. right after a track change).
    val progressFraction = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val timeLabel = "${formatMs(positionMs)}/${formatMs(durationMs)}"

    // Spins the disc at a steady rate while music is playing; stops in place the
    // instant playback pauses (the coroutine below is simply cancelled).
    val discRotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                discRotation.animateTo(
                    targetValue = discRotation.value + 360f,
                    animationSpec = tween(durationMillis = 8000, easing = LinearEasing)
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().padding(horizontal = 8.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(26.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM", color = Color(0xFF8E8E93), fontSize = 10.sp, letterSpacing = 2.sp)
                    Text(detectionMethod.uppercase(), color = Color.White, fontSize = 13.sp, letterSpacing = 1.sp)
                }
                Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            Box(modifier = Modifier.fillMaxWidth().height(500.dp).padding(top = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = progressFraction,
                    color = Color(0xFFD9D9D9),
                    trackColor = Color.Transparent,
                    strokeWidth = 10.dp,
                    modifier = Modifier.requiredSize(444.dp).graphicsLayer { rotationZ = 42f }
                )
                CurvedTimeLabel(timeLabel, Modifier.requiredSize(453.dp).offset(y = (-3).dp))
                Icon(
                    Icons.Filled.PlayCircleOutline,
                    contentDescription = "Playback speed",
                    tint = Color(0xFFD9D9D9),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 14.dp, end = 2.dp)
                        .size(31.dp)
                )
                Surface(
                    color = Color(0xFF1B1C1E),
                    shape = discShape,
                    modifier = Modifier
                        .requiredSize(405.dp)
                        .graphicsLayer { rotationZ = discRotation.value }
                        .border(1.dp, Color(0xFF38393B), discShape)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Split the diagonal guide around the center label so text can sit
                        // cleanly on the same axis without crossing the artwork.
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val inset = size.minDimension * 0.10f
                            val gapStart = size.minDimension * 0.39f
                            val gapEnd = size.minDimension * 0.61f
                            drawLine(
                                color = Color(0xFF3A3B3E),
                                start = androidx.compose.ui.geometry.Offset(inset, size.height - inset),
                                end = androidx.compose.ui.geometry.Offset(gapStart, size.height - gapStart),
                                strokeWidth = 1.5f
                            )
                            drawLine(
                                color = Color(0xFF3A3B3E),
                                start = androidx.compose.ui.geometry.Offset(gapEnd, size.height - gapEnd),
                                end = androidx.compose.ui.geometry.Offset(size.width - inset, inset),
                                strokeWidth = 1.5f
                            )
                        }
                        Surface(color = Color.Black, shape = discShape, modifier = Modifier.size(132.dp).border(1.dp, Color(0xFF535457), discShape)) {
                            if (albumArt != null) {
                                Image(bitmap = albumArt.asImageBitmap(), contentDescription = "Album art", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(discShape))
                            } else {
                                Box(contentAlignment = Alignment.Center) { Text("NL", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                        Box(modifier = Modifier.size(24.dp).background(Color.Black, discShape).border(2.dp, Color.White, discShape))
                        Text(
                            artistName.ifBlank { "UNKNOWN ARTIST" }.take(18).uppercase(),
                            color = Color(0xFFD7D7D7),
                            fontSize = 10.sp,
                            letterSpacing = 0.8.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 41.dp, bottom = 95.dp)
                                .width(123.dp)
                                .graphicsLayer { rotationZ = -45f }
                        )
                        Text(
                            albumName.ifBlank { "UNKNOWN ALBUM" }.take(16).uppercase(),
                            color = Color(0xFFD7D7D7),
                            fontSize = 10.sp,
                            letterSpacing = 0.8.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 41.dp, top = 95.dp)
                                .width(123.dp)
                                .graphicsLayer { rotationZ = -45f }
                        )
                    }
                }
                Icon(
                    Icons.Filled.FiberManualRecord,
                    contentDescription = "Recording indicator",
                    tint = Color(0xFFD82132),
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 6.dp).size(49.dp)
                )
                if (glyphSongEnabled) {
                    GlyphSongPreview(
                        playing = isPlaying,
                        hardwareReady = glyphHardwareReady,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 6.dp).size(58.dp)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 36.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color.Transparent, shape = discShape, modifier = Modifier.size(52.dp).border(1.dp, Color(0xFF35363A), discShape)) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Lyrics", tint = Color.White, modifier = Modifier.padding(13.dp))
                }
                Surface(color = Color.Transparent, shape = RoundedCornerShape(28.dp), modifier = Modifier.border(1.dp, Color(0xFF35363A), RoundedCornerShape(28.dp))) { Text(trackName.ifBlank { "NOTHING LYRICS" }.take(20).uppercase(), color = Color.White, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 28.dp, vertical = 13.dp)) }
                Surface(color = Color.Transparent, shape = discShape, modifier = Modifier.size(52.dp).border(1.dp, Color(0xFF35363A), discShape)) {
                    Icon(if (showAodAlbumArt) Icons.Filled.Star else Icons.Outlined.StarOutline, contentDescription = "Toggle AOD artwork", tint = Color.White, modifier = Modifier.padding(13.dp).clickable(onClick = onAodAlbumArtClick))
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFF202124), shape = discShape, modifier = Modifier.size(74.dp).clickable(onClick = onPrevious)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.padding(22.dp))
                }
                Surface(color = Color.White, shape = RoundedCornerShape(42.dp), modifier = Modifier.size(width = 170.dp, height = 74.dp).clickable(onClick = onPlayPause)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play", tint = Color.Black, modifier = Modifier.size(34.dp))
                    }
                }
                Surface(color = Color(0xFF202124), shape = discShape, modifier = Modifier.size(74.dp).clickable(onClick = onNext)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.padding(22.dp))
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Volume down", tint = Color.White, modifier = Modifier.size(18.dp))
                Slider(value = volumePreview, onValueChange = { volumePreview = it }, modifier = Modifier.weight(1f).padding(horizontal = 10.dp), colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color(0xFFD82132), activeTrackColor = Color(0xFFD82132), inactiveTrackColor = Color(0xFF3A3B3E)))
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume up", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 26.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DarkMode, contentDescription = "AOD mode", tint = Color.White, modifier = Modifier.size(24.dp).clickable(onClick = onLyricsOnlyClick))
                Icon(Icons.Filled.Image, contentDescription = "Toggle album art", tint = if (showAodAlbumArt) Color(0xFFD82132) else Color.White, modifier = Modifier.size(24.dp).clickable(onClick = onAodAlbumArtClick))
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(24.dp).clickable { showSettings = true })
                Icon(Icons.Filled.GraphicEq, contentDescription = "Toggle Glyph Song", tint = if (glyphSongEnabled) Color(0xFFD82132) else Color.White, modifier = Modifier.size(24.dp).clickable(onClick = onGlyphSongToggle))
            }
            if (trackName.isBlank()) TextButton(onClick = onGrantPermissionClick, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("ENABLE MEDIA ACCESS", color = Color(0xFF9D9DA0), fontSize = 10.sp) }
        }
    }

    if (showSettings) {
        AodSettingsDialog(
            fontColor = aodFontColor,
            fontSizeSp = aodFontSizeSp,
            onFontColorChange = onAodFontColorChange,
            onFontSizeChange = onAodFontSizeChange,
            glyphSongEnabled = glyphSongEnabled,
            glyphHardwareReady = glyphHardwareReady,
            onGlyphSongToggle = onGlyphSongToggle,
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun GlyphSongPreview(
    playing: Boolean,
    hardwareReady: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "glyphSongPreview")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "glyphSongPhase"
    )

    fun pulse(offset: Float): Float {
        if (!playing) return 0.25f
        return 0.25f + 0.75f * abs(sin(phase + offset))
    }

    Canvas(
        modifier = modifier
            .background(Color.Black, CircleShape)
            .border(1.dp, Color(0xFF35363A), CircleShape)
            .padding(9.dp)
    ) {
        val stroke = 3.dp.toPx()
        val arcStyle = Stroke(width = stroke, cap = StrokeCap.Round)
        drawArc(
            color = Color.White.copy(alpha = pulse(0f)),
            startAngle = 205f,
            sweepAngle = 130f,
            useCenter = false,
            style = arcStyle
        )
        drawLine(
            color = Color.White.copy(alpha = pulse(2.1f)),
            start = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.18f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawArc(
            color = Color.White.copy(alpha = pulse(4.2f)),
            startAngle = 20f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.56f, size.height * 0.10f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.36f, size.height * 0.36f),
            style = arcStyle
        )
        drawCircle(
            color = if (hardwareReady) Color(0xFFD82132) else Color(0xFF5A5A5D),
            radius = 2.5.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.82f)
        )
    }
}

@Composable
private fun AodSettingsDialog(
    fontColor: Color,
    fontSizeSp: Float,
    onFontColorChange: (Color) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    glyphSongEnabled: Boolean,
    glyphHardwareReady: Boolean,
    onGlyphSongToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF171719),
        title = { Text("AOD SETTINGS", color = Color.White, letterSpacing = 1.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("LYRIC COLOR", color = Color(0xFF9D9DA0), fontSize = 11.sp, letterSpacing = 1.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Color.White to "WHITE", Color(0xFFD82132) to "RED", Color(0xFFB4D8FF) to "ICE").forEach { (color, label) ->
                        OutlinedButton(onClick = { onFontColorChange(color) }, border = androidx.compose.foundation.BorderStroke(1.dp, if (fontColor == color) color else Color(0xFF4A4A4D))) {
                            Text(label, color = color, fontSize = 10.sp)
                        }
                    }
                }
                Text("LYRIC SIZE  ${fontSizeSp.toInt()}SP", color = Color(0xFF9D9DA0), fontSize = 11.sp, letterSpacing = 1.sp)
                Slider(value = fontSizeSp, onValueChange = onFontSizeChange, valueRange = 22f..42f, colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0xFF3A3A3D)))
                Text("GLYPH SONG", color = Color(0xFF9D9DA0), fontSize = 11.sp, letterSpacing = 1.sp)
                OutlinedButton(
                    onClick = onGlyphSongToggle,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (glyphSongEnabled) Color.White else Color(0xFF4A4A4D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (glyphSongEnabled) "GLYPH SYNC ON" else "GLYPH SYNC OFF", color = Color.White, fontSize = 11.sp, letterSpacing = 1.sp)
                }
                Text(
                    if (glyphHardwareReady) "PHONE (3A) GLYPH CONNECTED" else "PREVIEW MODE · GLYPH DEBUG ACCESS REQUIRED",
                    color = if (glyphHardwareReady) Color.White else Color(0xFF8E8E93),
                    fontSize = 10.sp
                )
                Text("Suggestion: white at 30sp gives the clearest AOD reading with low OLED brightness.", color = Color(0xFFB6B6BA), fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE", color = Color.White) } }
    )
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun CurvedTimeLabel(time: String, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val inset = 4.dp.toPx()
        val path = android.graphics.Path().apply {
            addArc(android.graphics.RectF(inset, inset, size.width - inset, size.height - inset), 198f, 88f)
        }
        val timeText = "$time  ·  "
        val speedText = "1X"
        val timePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.LTGRAY
            textSize = 11.sp.toPx()
            letterSpacing = 0.11f
        }
        val speedPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#D82132")
            textSize = 11.sp.toPx()
            letterSpacing = 0.11f
        }
        val pathLength = android.graphics.PathMeasure(path, false).length
        val timeWidth = timePaint.measureText(timeText)
        val speedWidth = speedPaint.measureText(speedText)
        val startOffset = ((pathLength - timeWidth - speedWidth) / 2f).coerceAtLeast(0f)
        val verticalOffset = -7.dp.toPx()
        val canvas = drawContext.canvas.nativeCanvas
        canvas.drawTextOnPath(timeText, path, startOffset, verticalOffset, timePaint)
        canvas.drawTextOnPath(speedText, path, startOffset + timeWidth, verticalOffset, speedPaint)
    }
}

@Composable
private fun LyricsOnlyScreen(
    lyricIndex: Int,
    lyricLines: List<LyricLine>,
    albumArt: Bitmap?,
    showAlbumArt: Boolean,
    lyricColor: Color,
    lyricSizeSp: Float,
    onExit: () -> Unit
) {
    val lyricListState = rememberLazyListState()
    val artworkMotion = rememberInfiniteTransition(label = "aodArtworkMotion")
    val artworkDrift by artworkMotion.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aodArtworkDrift"
    )
    val density = LocalDensity.current
    val driftX = with(density) { 5.dp.toPx() } * artworkDrift
    val driftY = with(density) { 3.dp.toPx() } * -artworkDrift

    LaunchedEffect(lyricIndex, lyricLines.size) {
        if (lyricIndex >= 0) lyricListState.centerItem(lyricIndex)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onExit)) {
        if (showAlbumArt && albumArt != null) {
            Row(modifier = Modifier.fillMaxSize()) {
                AodLyricsList(lyricListState, lyricIndex, lyricLines, lyricColor, lyricSizeSp, Modifier.weight(0.62f))
                Box(
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val artworkShape = RoundedCornerShape(24.dp)
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .offset(x = (-20).dp)
                            .background(Color.Black, artworkShape)
                            .clip(artworkShape)
                    ) {
                        Image(
                            bitmap = albumArt.asImageBitmap(),
                            contentDescription = "Album artwork",
                            contentScale = ContentScale.Crop,
                            // Dark monochrome artwork remains visible without becoming an OLED hotspot.
                            colorFilter = ColorFilter.colorMatrix(
                                ColorMatrix(floatArrayOf(
                                    0.117f, 0.393f, 0.040f, 0f, 0f,
                                    0.117f, 0.393f, 0.040f, 0f, 0f,
                                    0.117f, 0.393f, 0.040f, 0f, 0f,
                                    0f, 0f, 0f, 0.82f, 0f
                                ))
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = driftX
                                    translationY = driftY
                                    rotationX = artworkDrift * 1.1f
                                    rotationY = artworkDrift * 2.2f
                                    rotationZ = artworkDrift * 0.25f
                                    scaleX = 1f + artworkDrift * 0.012f
                                    scaleY = 1f + artworkDrift * 0.012f
                                    cameraDistance = 10f * density.density
                                }
                        )
                    }
                }
            }
        } else {
            AodLyricsList(lyricListState, lyricIndex, lyricLines, lyricColor, lyricSizeSp, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun AodLyricsList(
    lyricListState: LazyListState,
    lyricIndex: Int,
    lyricLines: List<LyricLine>,
    lyricColor: Color,
    lyricSizeSp: Float,
    modifier: Modifier
) {
    LazyColumn(
        state = lyricListState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 120.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        if (lyricLines.isEmpty()) {
            item { Text("...", color = lyricColor, fontSize = lyricSizeSp.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        } else {
            items(lyricLines.size) { index ->
                val active = index == lyricIndex
                Text(
                    text = lyricLines[index].text.ifBlank { "♪" },
                    color = if (active) lyricColor else lyricColor.copy(alpha = 0.42f),
                    fontSize = if (active) lyricSizeSp.sp else (lyricSizeSp - 6f).coerceAtLeast(16f).sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    lineHeight = if (active) (lyricSizeSp + 8f).sp else (lyricSizeSp + 1f).sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onGrantPermissionClick: () -> Unit, modifier: Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("READY FOR THE NEXT TRACK", color = Color(0xFF787878), fontSize = 12.sp, letterSpacing = 1.sp)
        Text("Enable notification access for reliable playback detection.", color = Color(0xFFB0B0B0), fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, bottom = 18.dp))
        OutlinedButton(onClick = onGrantPermissionClick, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("ENABLE ACCESS") }
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = Color(0xFFFF4D67), strokeWidth = 2.dp)
        Text("Looking for synced lyrics", color = Color(0xFF9A9A9A), modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun lyricFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFF78E3C8),
    unfocusedBorderColor = Color(0xFF465268),
    focusedLabelColor = Color(0xFF78E3C8),
    unfocusedLabelColor = Color(0xFF9AA6B8),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White
)

private suspend fun LazyListState.centerItem(index: Int) {
    var item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) {
        // A newly loaded song has no visible lyric yet, so establish the initial position once.
        scrollToItem(index)
        item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    }
    val viewportCenter = layoutInfo.viewportStartOffset + layoutInfo.viewportSize.height / 2
    val distance = item.offset + item.size / 2f - viewportCenter
    if (abs(distance) > 1f) {
        animateScrollBy(distance, animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing))
    }
}
