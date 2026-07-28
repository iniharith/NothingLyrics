package com.nothing.lyricwidget.service

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nothing.lyricwidget.R
import com.nothing.lyricwidget.utils.LyricRepository

class LyricsAAActivity : AppCompatActivity() {
    private lateinit var songInfo: TextView
    private lateinit var currentLine: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val updateTask = object : Runnable {
        override fun run() {
            if (!running) return
            try { updateUi() } catch (_: Exception) { }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_lyrics_aa)
            songInfo = findViewById(R.id.songInfo)
            currentLine = findViewById(R.id.currentLine)
        } catch (_: Exception) { }
        running = true
        handler.post(updateTask)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(updateTask)
        super.onDestroy()
    }

    private fun updateUi() {
        val title = LyricRepository.currentTrack
        val artist = LyricRepository.currentArtist
        val lyrics = LyricRepository.lyricLines
        val index = LyricRepository.currentLyricIndex

        if (title.isBlank()) {
            songInfo.text = "Waiting for music…"
            currentLine.text = ""
            return
        }

        songInfo.text = "$title · $artist"

        if (lyrics.isEmpty()) {
            currentLine.text = ""
            return
        }

        val displayIndex = if (index < 0) 0 else index
        currentLine.text = lyrics[displayIndex].text
    }
}
