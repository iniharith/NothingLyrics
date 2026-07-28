package com.nothing.lyricwidget.service

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.nothing.lyricwidget.R

class LyricMirrorPlayer(context: android.content.Context) {
    private val silentUri = Uri.parse("android.resource://${context.packageName}/${R.raw.silent}")

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().also {
        it.playWhenReady = true
    }

    fun setItem(mediaItem: MediaItem) {
        val item = mediaItem.buildUpon().setUri(silentUri).build()
        exoPlayer.setMediaItem(item)
        exoPlayer.prepare()
    }

    fun replaceItem(mediaItem: MediaItem) {
        val item = mediaItem.buildUpon().setUri(silentUri).build()
        exoPlayer.replaceMediaItem(0, item)
    }

    fun release() {
        exoPlayer.release()
    }
}
