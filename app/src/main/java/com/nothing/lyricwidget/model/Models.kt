package com.nothing.lyricwidget.model

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class LrcResponse(
    val id: Long,
    val name: String,
    val artistName: String,
    val albumName: String?,
    val duration: Double?,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?
)
