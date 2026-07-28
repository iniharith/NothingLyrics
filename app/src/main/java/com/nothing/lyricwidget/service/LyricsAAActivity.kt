package com.nothing.lyricwidget.service

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import com.nothing.lyricwidget.utils.LyricRepository

class LyricsAAActivity : Activity() {
    private lateinit var lineView: TextView
    private lateinit var infoView: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val poll = object : Runnable {
        override fun run() {
            try {
                val title = LyricRepository.currentTrack
                val artist = LyricRepository.currentArtist
                val lyrics = LyricRepository.lyricLines
                val index = LyricRepository.currentLyricIndex

                if (title.isBlank()) {
                    infoView.text = "Waiting for music…"
                    lineView.text = ""
                } else {
                    infoView.text = "$title · $artist"
                    val i = if (index < 0 && lyrics.isNotEmpty()) 0 else index
                    lineView.text = if (i in lyrics.indices) lyrics[i].text else ""
                }
            } catch (_: Exception) { }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.BOTTOM
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.BLACK)
        }
        lineView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
        }
        infoView = TextView(this).apply {
            setTextColor(Color.argb(100, 255, 255, 255))
            textSize = 11f
            text = "Waiting for music…"
        }
        root.addView(lineView)
        root.addView(infoView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = 16 })
        setContentView(root)
        handler.post(poll)
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        super.onDestroy()
    }
}
