package com.nothing.lyricwidget.service

import android.app.PendingIntent
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata as Media3Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
    private lateinit var backingPlayer: ExoPlayer
    private var mediaLibrarySession: MediaLibrarySession? = null
    @Volatile private var currentItem: MediaItem? = null

    @Volatile private var lastTitle: String = ""
    @Volatile private var lastArtist: String = ""
    @Volatile private var lastArtworkData: ByteArray? = null
    @Volatile private var lastLyricLine: String = ""
    @Volatile private var lastAlbum: String = ""
    @Volatile private var lastIsPlaying: Boolean = false
    private var lastRenderedSignature: String = ""
    private val silentUri: Uri by lazy {
        Uri.parse("android.resource://$packageName/${R.raw.silent}")
    }

    /**
     * The decoy ExoPlayer only ever plays a silent local file, so by default Android Auto's
     * transport buttons would just toggle/seek that silent playback instead of the real music
     * app — which is exactly why Play/Pause got stuck and Next never showed up (a single-item
     * playlist has no "next" to seek to). This wrapper reports those commands as always
     * available and forwards the actual taps to whatever app MusicNotificationListenerService is
     * currently tracking (Spotify, YT Music, etc.), instead of touching the decoy player.
     */
    private inner class RemoteControlPlayer(player: Player) : ForwardingPlayer(player) {
        override fun getAvailableCommands(): Player.Commands {
            return super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return when (command) {
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                else -> super.isCommandAvailable(command)
            }
        }

        override fun play() {
            MusicNotificationListenerService.play()
        }

        override fun pause() {
            MusicNotificationListenerService.pause()
        }

        override fun seekToNext() {
            MusicNotificationListenerService.skipToNext()
        }

        override fun seekToNextMediaItem() {
            MusicNotificationListenerService.skipToNext()
        }

        override fun seekToPrevious() {
            MusicNotificationListenerService.skipToPrevious()
        }

        override fun seekToPreviousMediaItem() {
            MusicNotificationListenerService.skipToPrevious()
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this
        // handleAudioFocus = false: this player only exists to publish fake "now playing"
        // metadata (title/artist swapped for lyrics) for Android Auto's Now Playing screen.
        // It must never request real audio focus, or it will duck/pause whatever the user is
        // actually listening to (Spotify, YT Music, etc.) and that gets reported as "stopped
        // detecting the song".
        backingPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ false)
            .build().also {
                it.playWhenReady = true
                it.repeatMode = Player.REPEAT_MODE_ONE
                it.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("AutoMediaService", "Player error: ${error.errorCode}")
                    }
                })
            }

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.nothing.lyricwidget.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            RemoteControlPlayer(backingPlayer),
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
        ).setSessionActivity(sessionActivity).build()

        if (LyricRepository.currentTrack.isNotBlank()) {
            publishSnapshot(
                title = LyricRepository.currentTrack,
                artist = LyricRepository.currentArtist,
                album = LyricRepository.currentAlbum,
                artworkData = null,
                isPlaying = LyricRepository.isPlaying
            )
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        if (activeService === this) activeService = null
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        backingPlayer.release()
        super.onDestroy()
    }

    private fun publishSnapshot(
        title: String,
        artist: String,
        album: String,
        artworkData: ByteArray?,
        isPlaying: Boolean = lastIsPlaying
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { publishSnapshot(title, artist, album, artworkData, isPlaying) }
            return
        }

        val trackChanged = title != lastTitle || artist != lastArtist
        lastTitle = title
        lastArtist = artist
        if (artworkData != null) lastArtworkData = artworkData
        if (trackChanged) lastLyricLine = ""
        lastIsPlaying = isPlaying
        // Keep the decoy's own playback state mirroring the real app's, so Android Auto's
        // Play/Pause icon and toggle logic (which reads *this* player's state) stay correct.
        backingPlayer.playWhenReady = isPlaying

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
            // Snapshot both into locals: lyricLines can be reassigned from a background thread
            // (LyricRepository.fetchLyricsInBackground), so reading .size and then indexing as
            // two separate property reads risks a torn read against a shorter, newer list.
            val lines = LyricRepository.lyricLines
            val nextIndex = LyricRepository.currentLyricIndex + 1
            val nextLine = if (nextIndex in lines.indices) lines[nextIndex].text else ""

            val displayTitle = lastLyricLine.ifBlank { lastTitle }
            val displaySubtitle = nextLine.ifBlank { lastArtist }
            val displayDescription = buildString {
                append(displayTitle)
                if (nextLine.isNotBlank()) append("\n").append(nextLine)
                append("\n— ").append(lastTitle).append(" · ").append(lastArtist)
            }

            // Android Auto (and the system status bar, which reads from the same session)
            // rebuilds its Now Playing view whenever the item is replaced. We used to call
            // replaceMediaItem() on every 300-500ms poll tick even when nothing on screen would
            // actually change, which spammed AA with redundant updates and could make it stall
            // or skip rendering the newer lyric text. Skip the rebuild entirely when the visible
            // content is identical to what's already published.
            val signature = "$displayTitle|$displaySubtitle|$displayDescription|" +
                "$lastArtist|$lastTitle|${lastArtworkData?.size ?: 0}"
            if (signature == lastRenderedSignature && currentItem != null) return
            lastRenderedSignature = signature

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
                .setUri(silentUri)
                .setMediaMetadata(mediaMetadata.build())
                .build()

            if (currentItem == null) {
                backingPlayer.setMediaItem(item)
                backingPlayer.prepare()
            } else {
                backingPlayer.replaceMediaItem(0, item)
            }
            currentItem = item
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
            try {
                val metadata = controller.metadata ?: return
                val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
                if (title.isBlank()) return
                val artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                val isPlaying = controller.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING

                activeService?.publishSnapshot(
                    title = title,
                    artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                    album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                    artworkData = artwork?.toJpeg(),
                    isPlaying = isPlaying
                )
            } catch (e: IllegalStateException) {
                // Controller's session died between the caller checking it and us reading it.
                Log.w("AutoMediaService", "publish() on stale controller: ${e.message}")
            } catch (e: Exception) {
                Log.e("AutoMediaService", "publish() failed: ${e.message}")
            }
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
