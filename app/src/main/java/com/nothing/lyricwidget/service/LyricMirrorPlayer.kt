package com.nothing.lyricwidget.service

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nothing.lyricwidget.utils.LyricRepository

class LyricMirrorPlayer(looper: Looper) : SimpleBasePlayer(looper) {
    private var currentItemData: MediaItemData? = null

    override fun getState(): State {
        val pos = LyricRepository.getPlaybackPositionMs()
        return State.Builder()
            .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(Player.STATE_READY)
            .setContentPositionMs(pos)
            .setCurrentMediaItemIndex(0)
            .setPlaylist(listOfNotNull(currentItemData))
            .build()
    }

    fun publishPosition() {
        invalidateState()
    }

    fun publishItem(mediaItem: MediaItem) {
        currentItemData = buildItemData(mediaItem)
        invalidateState()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        LyricRepository.setPlaybackPositionMs(positionMs)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        currentItemData = mediaItems.firstOrNull()?.let { buildItemData(it) }
        return Futures.immediateVoidFuture()
    }

    private fun buildItemData(mediaItem: MediaItem): MediaItemData {
        return MediaItemData.Builder("lyric_item")
            .setMediaItem(mediaItem)
            .setIsSeekable(true)
            .setDurationUs(LyricRepository.getDurationMs() * 1000)
            .build()
    }
}
