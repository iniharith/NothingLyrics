package com.nothing.lyricwidget.service

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nothing.lyricwidget.utils.LyricRepository

class LyricMirrorPlayer(looper: Looper) : SimpleBasePlayer(looper) {
    private var currentItemData: MediaItemData? = null

    private val placeholderItem: MediaItemData by lazy {
        MediaItemData.Builder("placeholder")
            .setMediaItem(
                MediaItem.Builder()
                    .setMediaId("placeholder")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("NothingLyrics")
                            .setArtist("Waiting for music...")
                            .setIsPlayable(true)
                            .build()
                    )
                    .build()
            )
            .setIsSeekable(false)
            .setDurationUs(0)
            .build()
    }

    override fun getState(): State {
        val pos = LyricRepository.getPlaybackPositionMs()
        val playlist = listOf(currentItemData ?: placeholderItem)
        return State.Builder()
            .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(Player.STATE_READY)
            .setContentPositionMs(pos)
            .setCurrentMediaItemIndex(0)
            .setPlaylist(playlist)
            .build()
    }

    fun publishPosition() {
        invalidateState()
    }

    fun publishTrack(mediaItem: MediaItem) {
        currentItemData = buildItemData(mediaItem, "track")
        invalidateState()
    }

    fun publishLine(mediaItem: MediaItem) {
        val existing = currentItemData
        currentItemData = if (existing != null) {
            existing.buildUpon().setMediaItem(mediaItem).build()
        } else {
            buildItemData(mediaItem, "track")
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
            buildItemData(it, "track")
        }
        return Futures.immediateVoidFuture()
    }

    private fun buildItemData(mediaItem: MediaItem, uid: String): MediaItemData {
        return MediaItemData.Builder(uid)
            .setMediaItem(mediaItem)
            .setIsSeekable(true)
            .setDurationUs((LyricRepository.getDurationMs() * 1000).coerceAtLeast(1))
            .build()
    }
}
