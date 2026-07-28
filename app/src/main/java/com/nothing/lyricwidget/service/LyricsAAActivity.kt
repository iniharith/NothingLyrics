package com.nothing.lyricwidget.service

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.nothing.lyricwidget.R
import com.nothing.lyricwidget.utils.LyricRepository

class LyricsAAActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val mainHandler = Handler(Looper.getMainLooper())
    private var updateActive = false

    private val lyricsTemplate: String by lazy {
        """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body {
                background: #0a0a0a;
                color: #888;
                font-family: -apple-system, 'Segoe UI', Roboto, sans-serif;
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                min-height: 100vh;
                padding: 24px;
                text-align: center;
                overflow: hidden;
            }
            #header {
                color: #1aa34a;
                font-size: 13px;
                letter-spacing: 0.5px;
                text-transform: uppercase;
                margin-bottom: 32px;
                opacity: 0.7;
            }
            #lyrics-container {
                width: 100%;
                max-width: 700px;
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                min-height: 60vh;
            }
            .line {
                width: 100%;
                padding: 6px 0;
                font-size: 17px;
                line-height: 1.4;
                color: #555;
                transition: all 0.3s ease;
            }
            .line.past {
                color: #444;
                font-size: 15px;
                opacity: 0.5;
            }
            .line.future {
                color: #555;
                font-size: 15px;
                opacity: 0.6;
            }
            .line.current {
                color: #ffffff;
                font-size: 22px;
                font-weight: 700;
                padding: 10px 0;
                text-shadow: 0 0 20px rgba(26, 163, 74, 0.3);
            }
            .line.current::before {
                content: '';
                display: block;
                width: 40px;
                height: 3px;
                background: #1aa34a;
                margin: 0 auto 12px auto;
                border-radius: 2px;
            }
            .line.current::after {
                content: '';
                display: block;
                width: 40px;
                height: 3px;
                background: #1aa34a;
                margin: 12px auto 0 auto;
                border-radius: 2px;
                opacity: 0.5;
            }
            #empty-state {
                color: #333;
                font-size: 16px;
                opacity: 0.4;
                display: none;
            }
            #empty-state.visible { display: block; }
            @media (min-width: 600px) {
                .line.current { font-size: 28px; }
                .line { font-size: 19px; }
            }
        </style>
        <script>
        function updateLyrics(title, artist, lines, currentIndex) {
            document.getElementById('songTitle').textContent = title;
            document.getElementById('songArtist').textContent = artist;
            document.getElementById('header').style.display = title ? 'block' : 'none';
            var container = document.getElementById('lyrics-container');
            var emptyState = document.getElementById('empty-state');
            if (!lines || lines.length === 0) { container.innerHTML = ''; emptyState.className = 'visible'; return; }
            emptyState.className = '';
            if (currentIndex < 0 || currentIndex >= lines.length) { container.innerHTML = ''; return; }
            var html = '';
            var start = Math.max(0, currentIndex - 3);
            var end = Math.min(lines.length, currentIndex + 4);
            for (var i = start; i < end; i++) {
                var cls = i < currentIndex ? 'line past' : (i === currentIndex ? 'line current' : 'line future');
                html += '<div class="' + cls + '">' + lines[i].i + '</div>';
            }
            container.innerHTML = html;
        }
        </script>
        </head>
        <body>
            <div id="header"><span id="songTitle">Waiting for music…</span> · <span id="songArtist"></span></div>
            <div id="lyrics-container"></div>
            <div id="empty-state" class="visible">No lyrics found for this track</div>
        </body>
        </html>
        """.trimIndent()
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!updateActive) return
            updateDisplay()
            mainHandler.postDelayed(this, 300)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lyrics_aa)

        webView = findViewById(R.id.lyricsWebView)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                updateDisplay()
                startUpdates()
            }
        }
        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            setSupportZoom(false)
        }
        webView.loadDataWithBaseURL(null, lyricsTemplate, "text/html", "UTF-8", null)
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized && webView.progress == 100) {
            startUpdates()
        }
    }

    override fun onPause() {
        stopUpdates()
        super.onPause()
    }

    override fun onDestroy() {
        stopUpdates()
        super.onDestroy()
    }

    private fun startUpdates() {
        updateActive = true
        mainHandler.post(updateRunnable)
    }

    private fun stopUpdates() {
        updateActive = false
        mainHandler.removeCallbacks(updateRunnable)
    }

    private fun updateDisplay() {
        val title = LyricRepository.currentTrack
        val artist = LyricRepository.currentArtist
        val lyrics = LyricRepository.lyricLines
        val currentIndex = LyricRepository.currentLyricIndex

        if (title.isBlank()) {
            webView.evaluateJavascript(
                """updateLyrics('Waiting for music…', '', [], -1);""", null
            )
            return
        }

        val escapedTitle = escapeJs(title)
        val escapedArtist = escapeJs(artist)

        if (lyrics.isEmpty()) {
            webView.evaluateJavascript(
                """updateLyrics('$escapedTitle', '$escapedArtist', [], -1);""", null
            )
            return
        }

        val linesJs = lyrics.joinToString(",") { line ->
            val text = escapeJs(line.text)
            """{t:${line.timeMs},i:"$text"}"""
        }

        webView.evaluateJavascript(
            """updateLyrics('$escapedTitle', '$escapedArtist', [$linesJs], $currentIndex);""", null
        )
    }

    private fun escapeJs(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    companion object {
        init {
            android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
