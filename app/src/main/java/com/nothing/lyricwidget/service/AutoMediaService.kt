package com.nothing.lyricwidget.service

import android.net.Uri
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
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

    override fun onCreate() {
        super.onCreate()
        val channelId = "media_playback_channel"
        val channel = NotificationChannel(channelId, "Media Playback", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val silentUri = Uri.parse("android.resource://${packageName}/${R.raw.silent}")
        player = ExoPlayer.Builder(this).build()
        player.setMediaItem(
            MediaItem.Builder()
                .setMediaId("nothinglyrics_session")
                .setUri(silentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("NothingLyrics")
                        .setArtist("Lyrics provider")
                        .build()
                )
                .build()
        )
        player.prepare()
        player.playWhenReady = true
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, Notification.Builder(this, "media_playback_channel")
            .setContentTitle("NothingLyrics")
            .setContentText("Lyrics provider")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build())
        return super.onStartCommand(intent, flags, startId)
    }
}
