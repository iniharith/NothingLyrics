package com.nothing.lyricwidget.service

import android.media.MediaMetadata
import android.media.session.MediaController
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata as Media3Metadata
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ControllerInfo
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nothing.lyricwidget.R
import com.nothing.lyricwidget.utils.LyricRepository
import java.io.ByteArrayOutputStream

class AutoMediaService : MediaLibraryService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mirrorPlayer = LyricMirrorPlayer(Looper.myLooper()!!)
    private var mediaLibrarySession: MediaLibrarySession? = null
    @Volatile private var currentItem: MediaItem? = null

    @Volatile private var lastTitle: String = ""
    @Volatile private var lastArtist: String = ""
    @Volatile private var lastArtworkData: ByteArray? = null
    @Volatile private var lastLyricLine: String = ""
    @Volatile private var lastAlbum: String = ""
    @Volatile private var positionUpdateActive = false

    private val positionUpdateRunnable = object : Runnable {
        override fun run() {
            if (!positionUpdateActive) return
            mirrorPlayer.publishPosition()
            mainHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this
        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            mirrorPlayer,
            object : MediaLibrarySession.Callback {
                override fun onGetLibraryRoot(
                    session: MediaLibrarySession,
                    browser: ControllerInfo,
                    params: LibraryParams?
                ): ListenableFuture<LibraryResult<MediaItem>> {
                    return Futures.immediateFuture(LibraryResult.ofItem(rootItem(), params))
                }

                override fun onGetChildren(
                    session: MediaLibrarySession,
                    browser: ControllerInfo,
                    parentId: String,
                    page: Int,
                    pageSize: Int,
                    params: LibraryParams?
                ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                    val items = if (parentId == ROOT_ID) ImmutableList.of(currentItem ?: waitingItem()) else ImmutableList.of()
                    return Futures.immediateFuture(LibraryResult.ofItemList(items, params))
                }
            }
        ).build()

        if (LyricRepository.currentTrack.isNotBlank()) {
            publishSnapshot(
                title = LyricRepository.currentTrack,
                artist = LyricRepository.currentArtist,
                album = LyricRepository.currentAlbum,
                artworkData = null
            )
        }
        startPositionUpdates()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        stopPositionUpdates()
        if (activeService === this) activeService = null
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        mirrorPlayer.release()
        super.onDestroy()
    }

    private fun startPositionUpdates() {
        positionUpdateActive = true
        mainHandler.post(positionUpdateRunnable)
    }

    private fun stopPositionUpdates() {
        positionUpdateActive = false
        mainHandler.removeCallbacks(positionUpdateRunnable)
    }

    private fun publishSnapshot(
        title: String,
        artist: String,
        album: String,
        artworkData: ByteArray?
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { publishSnapshot(title, artist, album, artworkData) }
            return
        }

        val trackChanged = title != lastTitle || artist != lastArtist
        lastTitle = title
        lastArtist = artist
        if (artworkData != null) lastArtworkData = artworkData
        if (trackChanged) lastLyricLine = ""

        renderCurrentItem(album)
    }

    private fun updateLyricLine(lyricText: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateLyricLine(lyricText) }
            return
        }
        if (lastTitle.isBlank() || lyricText == lastLyricLine) return
        lastLyricLine = lyricText
        renderCurrentItem(lastAlbum)
    }

    private fun renderCurrentItem(album: String) {
        lastAlbum = album
        try {
            val nextIndex = LyricRepository.currentLyricIndex + 1
            val nextLine = if (nextIndex < LyricRepository.lyricLines.size) LyricRepository.lyricLines[nextIndex].text else ""

            val displayTitle = lastLyricLine.ifBlank { lastTitle }
            val displaySubtitle = nextLine.ifBlank { lastArtist }
            val displayDescription = buildString {
                append(displayTitle)
                if (nextLine.isNotBlank()) append("\n").append(nextLine)
                append("\n— ").append(lastTitle).append(" · ").append(lastArtist)
            }

            val mediaMetadata = Media3Metadata.Builder()
                .setTitle(displayTitle)
                .setSubtitle(displaySubtitle)
                .setDescription(displayDescription)
                .setArtist(lastArtist)
                .setAlbumTitle(lastTitle)
                .setIsPlayable(true)
                .setIsBrowsable(true)
            lastArtworkData?.let {
                mediaMetadata.setArtworkData(it, Media3Metadata.PICTURE_TYPE_FRONT_COVER)
            }
            val item = MediaItem.Builder()
                .setMediaId(STEADY_MEDIA_ID)
                .setMediaMetadata(mediaMetadata.build())
                .build()

            currentItem = item
            mirrorPlayer.publishItem(item)
        } catch (exception: Exception) {
            Log.e("AutoMediaService", "Unable to publish external media state", exception)
        }
    }

    companion object {
        private const val ROOT_ID = "pulse_lyrics_now_playing"
        private const val STEADY_MEDIA_ID = "pulse_lyrics_now_playing_item"
        @Volatile
        private var activeService: AutoMediaService? = null

        fun publish(controller: MediaController) {
            val metadata = controller.metadata ?: return
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            if (title.isBlank()) return
            val artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)

            activeService?.publishSnapshot(
                title = title,
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                artworkData = artwork?.toJpeg()
            )
        }

        fun publishLyric(lyricText: String) {
            activeService?.updateLyricLine(lyricText)
        }

        private fun Bitmap.toJpeg(): ByteArray? {
            return try {
                ByteArrayOutputStream().use { output ->
                    if (compress(Bitmap.CompressFormat.JPEG, 85, output)) output.toByteArray() else null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun rootItem(): MediaItem = MediaItem.Builder()
        .setMediaId(ROOT_ID)
        .setMediaMetadata(
            Media3Metadata.Builder()
                .setTitle("Now Playing")
                .setIsPlayable(true)
                .setIsBrowsable(true)
                .build()
        )
        .build()

    private fun waitingItem(): MediaItem = MediaItem.Builder()
        .setMediaId("nothing_lyrics_waiting")
        .setMediaMetadata(
            Media3Metadata.Builder()
                .setTitle("Waiting for music")
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .build()
        )
        .build()
}
