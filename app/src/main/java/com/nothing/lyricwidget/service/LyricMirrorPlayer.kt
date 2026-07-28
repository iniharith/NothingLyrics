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

    fun publishTrack(mediaItem: MediaItem) {
        currentItemData = buildItemData(mediaItem, "track_" + System.currentTimeMillis())
        invalidateState()
    }

    fun publishLine(mediaItem: MediaItem) {
        val existing = currentItemData
        currentItemData = if (existing != null) {
            existing.buildUpon().setMediaItem(mediaItem).build()
        } else {
            buildItemData(mediaItem, "track_" + System.currentTimeMillis())
        }
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
        currentItemData = mediaItems.firstOrNull()?.let {
            buildItemData(it, "track_" + System.currentTimeMillis())
        }
        return Futures.immediateVoidFuture()
    }

    private fun buildItemData(mediaItem: MediaItem, uid: String): MediaItemData {
        return MediaItemData.Builder(uid)
            .setMediaItem(mediaItem)
            .setIsSeekable(true)
            .setDurationUs(LyricRepository.getDurationMs() * 1000)
            .build()
    }
}
