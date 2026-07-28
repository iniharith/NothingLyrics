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
    private lateinit var emptyState: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var updating = false

    private val updateTask = object : Runnable {
        override fun run() {
            if (!updating) return
            updateUi()
            handler.postDelayed(this, 400)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lyrics_aa)
        songInfo = findViewById(R.id.songInfo)
        currentLine = findViewById(R.id.currentLine)
        emptyState = findViewById(R.id.emptyState)
        updateUi()
        updating = true
        handler.post(updateTask)
    }

    override fun onDestroy() {
        updating = false
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
            currentLine.visibility = android.view.View.GONE
            emptyState.visibility = android.view.View.GONE
            return
        }

        songInfo.text = "$title · $artist"

        if (lyrics.isEmpty()) {
            currentLine.visibility = android.view.View.GONE
            emptyState.visibility = android.view.View.VISIBLE
            emptyState.text = "No lyrics found"
            return
        }

        if (index < 0 || index >= lyrics.size) {
            currentLine.visibility = android.view.View.GONE
            emptyState.visibility = android.view.View.VISIBLE
            emptyState.text = "Loading lyrics…"
            return
        }

        emptyState.visibility = android.view.View.GONE
        currentLine.visibility = android.view.View.VISIBLE
        currentLine.text = lyrics[index].text
    }
}
