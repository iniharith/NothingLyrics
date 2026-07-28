package com.nothing.lyricwidget.service

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.nothing.lyricwidget.R
import com.nothing.lyricwidget.utils.LyricRepository

class AutoMediaService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private var currentTrackId: String? = null

    override fun onCreate() {
        super.onCreate()
        val silentUri = Uri.parse("android.resource://${packageName}/${R.raw.silent}")
        player = ExoPlayer.Builder(this).build().also { it.playWhenReady = true }
        player.setMediaItem(placeholderItem)
        player.prepare()
        mediaSession = MediaSession.Builder(this, player).build()

        LyricRepository.onTrackChanged = { track, artist, album ->
            val trackId = "$track|$artist"
            currentTrackId = trackId
            val item = MediaItem.Builder()
                .setMediaId(trackId)
                .setUri(silentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track)
                        .setArtist(artist)
                        .setAlbumTitle(album)
                        .setDescription(LyricRepository.getLyricAt(LyricRepository.currentLyricIndex))
                        .build()
                )
                .build()
            player.setMediaItem(item)
            player.prepare()
        }

        LyricRepository.onLyricChanged = { lyric ->
            val current = player.currentMediaItem
            if (current != null) {
                val meta = current.mediaMetadata.buildUpon().setDescription(lyric).build()
                val item = current.buildUpon().setMediaMetadata(meta).build()
                player.replaceMediaItem(0, item)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        LyricRepository.onTrackChanged = null
        LyricRepository.onLyricChanged = null
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    companion object {
        private val placeholderItem = MediaItem.Builder()
            .setMediaId("nothinglyrics_placeholder")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("NothingLyrics")
                    .setArtist("Waiting for music…")
                    .build()
            )
            .build()
    }
}
