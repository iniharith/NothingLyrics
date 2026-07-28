package com.nothing.lyricwidget.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import com.nothing.lyricwidget.model.LyricLine
import com.nothing.lyricwidget.widget.NothingLyricWidget

object LyricRepository {
    var currentTrack: String = ""
        private set
    var currentArtist: String = ""
        private set
    var currentAlbum: String = ""
        private set
    var currentAlbumArt: Bitmap? = null
        private set
    var isPlaying: Boolean = false
        private set
    var lyricLines: List<LyricLine> = emptyList()
    var onTrackChanged: ((track: String, artist: String, album: String) -> Unit)? = null
    var onLyricChanged: ((lyric: String) -> Unit)? = null
    var currentLyricIndex: Int = -1
        private set

    private var lastKnownPositionMs: Long = 0L
    private var lastUpdateTimeMs: Long = 0L
    private var durationMs: Long = 0L
    private const val CACHE_NAME = "lyric_cache"

    fun updateTrack(
        context: Context,
        track: String,
        artist: String,
        album: String,
        duration: Long,
        playing: Boolean,
        position: Long,
        albumArt: Bitmap? = null
    ) {
        val trackChanged = (track != currentTrack || artist != currentArtist)
        
        currentTrack = track
        currentArtist = artist
        currentAlbum = album
        if (trackChanged || albumArt != null) currentAlbumArt = albumArt
        if (duration > 0L || trackChanged) durationMs = duration
        isPlaying = playing
        lastKnownPositionMs = position
        lastUpdateTimeMs = System.currentTimeMillis()

        if (trackChanged) {
            lyricLines = emptyList()
            currentLyricIndex = -1
            onTrackChanged?.invoke(track, artist, album)

            val cachedLyrics = context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)
                .getString(cacheKey(track, artist), null)
            if (cachedLyrics != null) {
                setLyrics(context, cachedLyrics, cache = false)
            } else {
                fetchLyricsInBackground(context, track, artist, album, duration)
            }
        } else {
            updateLyricIndex()
            NothingLyricWidget.updateAllWidgets(context)
        }
    }

    fun updatePlaybackState(context: Context, playing: Boolean, position: Long) {
        isPlaying = playing
        lastKnownPositionMs = position
        lastUpdateTimeMs = System.currentTimeMillis()
        updateLyricIndex()
        NothingLyricWidget.updateAllWidgets(context)
    }

    fun setLyrics(context: Context, rawLrc: String?, cache: Boolean = true) {
        lyricLines = LrcParser.parse(rawLrc)
        if (durationMs <= 0L && lyricLines.isNotEmpty()) {
            durationMs = lyricLines.last().timeMs + DEFAULT_TRACK_TAIL_MS
        }
        currentLyricIndex = -1
        updateLyricIndex()
        if (cache && !rawLrc.isNullOrBlank() && currentTrack.isNotBlank()) {
            context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(cacheKey(currentTrack, currentArtist), rawLrc)
                .apply()
        }
        NothingLyricWidget.updateAllWidgets(context)
    }

    fun getDurationMs(): Long = durationMs

    fun getPlaybackPositionMs(): Long {
        if (!isPlaying) return lastKnownPositionMs
        val elapsed = System.currentTimeMillis() - lastUpdateTimeMs
        val estimated = lastKnownPositionMs + elapsed
        return if (durationMs > 0) estimated.coerceAtMost(durationMs) else estimated
    }

    fun setPlaybackPositionMs(positionMs: Long) {
        lastKnownPositionMs = positionMs.coerceAtLeast(0)
        lastUpdateTimeMs = System.currentTimeMillis()
    }

    fun updateLyricIndex(): Boolean {
        if (lyricLines.isEmpty()) {
            val changed = currentLyricIndex != -1
            currentLyricIndex = -1
            return changed
        }
        
        val pos = getPlaybackPositionMs()
        var newIndex = -1
        
        for (i in lyricLines.indices) {
            if (pos >= lyricLines[i].timeMs) {
                newIndex = i
            } else {
                break
            }
        }
        
        val changed = (newIndex != currentLyricIndex)
        currentLyricIndex = newIndex
        if (changed) {
            onLyricChanged?.invoke(getLyricAt(currentLyricIndex))
        }
        return changed
    }

    private fun fetchLyricsInBackground(context: Context, track: String, artist: String, album: String, duration: Long) {
        Thread {
            try {
                val response = com.nothing.lyricwidget.api.LrcLibClient.fetchLyrics(track, artist, album, duration / 1000.0)
                val lyricsText = response?.syncedLyrics

                // Do not let a slow lookup overwrite lyrics for a newer track.
                if (currentTrack == track && currentArtist == artist) {
                    response?.duration?.takeIf { it > 0.0 }?.let { durationMs = (it * 1000).toLong() }
                    setLyrics(context, lyricsText)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun getLyricAt(index: Int): String {
        if (index in lyricLines.indices) {
            return lyricLines[index].text
        }
        return ""
    }

    private fun cacheKey(track: String, artist: String): String {
        return "${track.trim().lowercase()}|${artist.trim().lowercase()}"
    }

    private const val DEFAULT_TRACK_TAIL_MS = 10_000L
}
