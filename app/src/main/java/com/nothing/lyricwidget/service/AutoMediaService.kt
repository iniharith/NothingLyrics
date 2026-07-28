package com.nothing.lyricwidget.service

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.nothing.lyricwidget.utils.LyricRepository

class AutoMediaService : MediaSessionService() {

    private lateinit var mirrorPlayer: LyricMirrorPlayer
    private lateinit var mediaSession: MediaSession
    private var currentTrackId: String? = null

    override fun onCreate() {
        super.onCreate()
        mirrorPlayer = LyricMirrorPlayer(this)
        mediaSession = MediaSession.Builder(this, mirrorPlayer.exoPlayer).build()

        LyricRepository.onTrackChanged = { track, artist, album ->
            val trackId = "$track|$artist"
            currentTrackId = trackId
            val metadata = MediaMetadata.Builder()
                .setTitle(track)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setDescription(LyricRepository.getLyricAt(LyricRepository.currentLyricIndex))
                .build()
            val item = MediaItem.Builder()
                .setMediaId(trackId)
                .setMediaMetadata(metadata)
                .build()
            mirrorPlayer.setItem(item)
        }

        LyricRepository.onLyricChanged = { lyric ->
            val current = mediaSession.player.currentMediaItem
            if (current != null) {
                val meta = current.mediaMetadata.buildUpon()
                    .setDescription(lyric)
                    .build()
                val item = current.buildUpon().setMediaMetadata(meta).build()
                mirrorPlayer.replaceItem(item)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        LyricRepository.onTrackChanged = null
        LyricRepository.onLyricChanged = null
        mediaSession.release()
        mirrorPlayer.release()
        super.onDestroy()
    }
}
