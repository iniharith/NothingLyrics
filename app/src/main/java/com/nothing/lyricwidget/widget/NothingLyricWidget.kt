package com.nothing.lyricwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import com.nothing.lyricwidget.MainActivity
import com.nothing.lyricwidget.R
import com.nothing.lyricwidget.service.MusicDetectionService
import com.nothing.lyricwidget.service.MusicNotificationListenerService
import com.nothing.lyricwidget.utils.LyricRepository
import kotlin.math.roundToInt

open class NothingLyricWidget : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ensureDetectionService(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        ensureDetectionService(context)
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CONTROL) {
            when (intent.getStringExtra(EXTRA_CONTROL)) {
                CONTROL_PLAY -> MusicNotificationListenerService.play()
                CONTROL_PAUSE -> MusicNotificationListenerService.pause()
                CONTROL_NEXT -> MusicNotificationListenerService.skipToNext()
                CONTROL_PREV -> MusicNotificationListenerService.skipToPrevious()
            }
            updateAllWidgets(context)
        }
    }

    companion object {
        const val ACTION_CONTROL = "com.nothing.lyricwidget.action.PLAYER_WIDGET_CONTROL"
        private const val EXTRA_CONTROL = "control"
        private const val CONTROL_PLAY = "play"
        private const val CONTROL_PAUSE = "pause"
        private const val CONTROL_NEXT = "next"
        private const val CONTROL_PREV = "prev"

        // Home UI spins 360 degrees every 8 seconds -> 45 deg/s -> 0.75 deg per 60fps frame.
        private const val DISC_DEG_PER_FRAME = 0.75f
        private const val PROGRESS_MAX = 10_000
        private var discRotation = 0f

        // Per-widget cache of the last fully-built RemoteViews, keyed by content hash so the
        // per-frame pusher can clone it (adding only rotation actions) instead of
        // re-parceling the disc bitmap and all text at 60fps.
        private val playerViewsCache = HashMap<Int, Pair<String, RemoteViews>>()

        fun hasPlayerWidgets(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            return playerWidgetIds(context, manager).isNotEmpty()
        }

        /**
         * Pushes one animation frame to every player widget. Called by the detection service on
         * every Choreographer frame. Returns false when no player widgets exist (loop can stop).
         */
        fun pushPlayerFrame(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            val ids = playerWidgetIds(context, manager)
            if (ids.isEmpty()) return false

            var pushed = false

            if (LyricRepository.isPlaying) {
                discRotation = (discRotation + DISC_DEG_PER_FRAME) % 360f
                pushed = true
            }
            if (!pushed) return true

            val key = contentKey()
            for (appWidgetId in ids) {
                val cached = playerViewsCache[appWidgetId]
                val views = if (cached == null || cached.first != key) {
                    buildPlayerViews(context, manager, appWidgetId)
                } else {
                    cached.second.clone() as RemoteViews
                }
                applyFrameActions(views)
                manager.updateAppWidget(appWidgetId, views)
            }
            return true
        }

        fun updateAllWidgets(context: Context) {
            updateAllForProvider(context, NothingLyricWidget::class.java)
            updateAllForProvider(context, NothingPlayerWidget::class.java)
        }

        private fun updateAllForProvider(context: Context, providerClass: Class<*>) {
            val manager = AppWidgetManager.getInstance(context)
            for (appWidgetId in manager.getAppWidgetIds(ComponentName(context, providerClass))) {
                updateWidget(context, manager, appWidgetId)
            }
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            updatePlayerWidget(context, appWidgetManager, appWidgetId)
        }

        private fun playerWidgetIds(context: Context, manager: AppWidgetManager): IntArray {
            return manager.getAppWidgetIds(ComponentName(context, NothingLyricWidget::class.java)) +
                manager.getAppWidgetIds(ComponentName(context, NothingPlayerWidget::class.java))
        }

        private fun ensureDetectionService(context: Context) {
            try {
                val intent = Intent(context, MusicDetectionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        private fun updatePlayerWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = buildPlayerViews(context, appWidgetManager, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun buildPlayerViews(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ): RemoteViews {
            val layout = playerWidgetLayout(context, appWidgetId)
            val views = RemoteViews(context.packageName, layout)

            val openIntent = Intent(context, MainActivity::class.java)
            val openPendingIntent = PendingIntent.getActivity(
                context, 1, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val isPlaying = LyricRepository.isPlaying
            val index = LyricRepository.currentLyricIndex
            val track = LyricRepository.currentTrack
            val artist = LyricRepository.currentArtist

            // Spinning vinyl disc drawn exactly like the home screen vinyl (see buildDiscBitmap).
            views.setImageViewBitmap(
                R.id.widget_player_disc,
                buildDiscBitmap(LyricRepository.currentAlbumArt, artist, LyricRepository.currentAlbum)
            )
            views.setOnClickPendingIntent(R.id.widget_player_root, openPendingIntent)

            views.setTextViewText(R.id.widget_title, track.ifBlank { "Nothing Lyrics" })
            views.setTextViewText(R.id.widget_lyric_line1, if (track.isBlank()) {
                "Play music to see lyrics"
            } else if (LyricRepository.lyricLines.isEmpty()) {
                "Fetching lyrics..."
            } else {
                LyricRepository.getLyricAt(index).ifBlank { "..." }
            })
            // The strip variant has no artist line.
            if (layout != R.layout.widget_player_strip) {
                views.setTextViewText(R.id.widget_artist, artist.ifBlank { "Open music player first" })
            }

            views.setImageViewResource(
                R.id.widget_player_play_pause,
                if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )
            val playPausePendingIntent = controlPendingIntent(
                context,
                if (isPlaying) CONTROL_PAUSE else CONTROL_PLAY
            )
            views.setOnClickPendingIntent(R.id.widget_player_prev, controlPendingIntent(context, CONTROL_PREV))
            views.setOnClickPendingIntent(R.id.widget_player_disc_button, playPausePendingIntent)
            views.setOnClickPendingIntent(R.id.widget_player_disc, playPausePendingIntent)
            views.setOnClickPendingIntent(R.id.widget_player_play_pause, playPausePendingIntent)
            views.setOnClickPendingIntent(R.id.widget_player_next, controlPendingIntent(context, CONTROL_NEXT))

            applyFrameActions(views)
            playerViewsCache[appWidgetId] = contentKey() to views
            return views
        }

        private fun applyFrameActions(views: RemoteViews) {
            views.setFloat(R.id.widget_player_disc, "setRotation", discRotation)
            val duration = LyricRepository.getDurationMs()
            val position = LyricRepository.getPlaybackPositionMs().coerceAtLeast(0L)
            val progress = if (duration > 0L) {
                ((position.coerceAtMost(duration) * PROGRESS_MAX) / duration).toInt()
            } else {
                0
            }
            views.setProgressBar(R.id.widget_player_progress, PROGRESS_MAX, progress, false)
        }

        /** Picks the layout that fits the launcher-provided cell size (1x4 strip / 2-row compact / big). */
        private fun playerWidgetLayout(context: Context, appWidgetId: Int): Int {
            val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            return when {
                minHeight in 1 until 80 -> R.layout.widget_player_strip
                minHeight in 80 until 150 -> R.layout.widget_player_compact
                else -> R.layout.widget_player
            }
        }

        private fun contentKey(): String {
            return "${LyricRepository.currentTrack}|${LyricRepository.currentArtist}|" +
                "${LyricRepository.currentAlbum}|" +
                "${LyricRepository.currentLyricIndex}|${LyricRepository.isPlaying}|" +
                "${LyricRepository.lyricLines.size}|${LyricRepository.currentAlbumArt?.hashCode()}"
        }

        private fun controlPendingIntent(context: Context, control: String): PendingIntent {
            val intent = Intent(context, NothingPlayerWidget::class.java).apply {
                action = ACTION_CONTROL
                putExtra(EXTRA_CONTROL, control)
            }
            return PendingIntent.getBroadcast(
                context, control.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * Exact visual replica of the home screen vinyl:
         * #1B1C1E body, #38393B ring, #3A3B3E diagonal guide lines, album art at 119/365 of the
         * disc diameter with a #535457 border, and the black center dot with white ring.
         */
        private fun buildDiscBitmap(artwork: Bitmap?, artist: String, album: String): Bitmap {
            val size = 240
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val center = size / 2f

            canvas.drawCircle(center, center, center, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF1B1C1E.toInt()
            })
            canvas.drawCircle(center, center, center - 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = 0xFF38393B.toInt()
            })

            // Diagonal guide lines (same split around the center as the home screen).
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF3A3B3E.toInt()
                strokeWidth = 2f
            }
            val inset = size * 0.10f
            val gapStart = size * 0.39f
            val gapEnd = size * 0.61f
            canvas.drawLine(inset, size - inset, gapStart, size - gapStart, linePaint)
            canvas.drawLine(gapEnd, size - gapEnd, size - inset, inset, linePaint)

            // Match the two -45 degree labels printed on the home-screen disc.
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFD7D7D7.toInt()
                textSize = size * 10f / 365f
                textAlign = Paint.Align.CENTER
            }
            fun drawLabel(text: String, x: Float, y: Float) {
                val saveCount = canvas.save()
                canvas.rotate(-45f, x, y)
                canvas.drawText(
                    text,
                    x,
                    y - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                    labelPaint
                )
                canvas.restoreToCount(saveCount)
            }
            drawLabel(
                artist.ifBlank { "UNKNOWN ARTIST" }.take(18).uppercase(),
                size * 102.5f / 365f,
                size * 264f / 365f
            )
            drawLabel(
                album.ifBlank { "UNKNOWN ALBUM" }.take(16).uppercase(),
                size * 262.5f / 365f,
                size * 101f / 365f
            )

            // Album art: 119/365 of the disc diameter, same ratio as the home screen.
            val artSize = (size * 119f / 365f).roundToInt()
            val artLeft = (size - artSize) / 2f
            val artTop = (size - artSize) / 2f

            if (artwork != null) {
                val cropped = centerCrop(artwork, artSize, artSize)
                val clip = canvas.save()
                canvas.clipPath(Path().apply { addCircle(center, center, artSize / 2f, Path.Direction.CW) })
                canvas.drawBitmap(cropped, artLeft, artTop, null)
                canvas.restoreToCount(clip)
            } else {
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt()
                    textSize = artSize * 0.15f
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }
                canvas.drawText("NL", center, center - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
            }
            canvas.drawCircle(center, center, artSize / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = 0xFF535457.toInt()
            })

            // Center dot: black with a thin white ring, like the home screen.
            val dotRadius = size * 24f / 365f / 2f
            canvas.drawCircle(center, center, dotRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF000000.toInt()
            })
            canvas.drawCircle(center, center, dotRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = 0xFFFFFFFF.toInt()
            })

            return bitmap
        }

        private fun centerCrop(source: Bitmap, width: Int, height: Int): Bitmap {
            var src = source
            if (src.config == Bitmap.Config.HARDWARE) {
                src = src.copy(Bitmap.Config.ARGB_8888, false)
            }
            val scale = maxOf(width.toFloat() / src.width, height.toFloat() / src.height)
            val sw = (width / scale).roundToInt().coerceAtMost(src.width)
            val sh = (height / scale).roundToInt().coerceAtMost(src.height)
            val sx = (src.width - sw) / 2
            val sy = (src.height - sh) / 2
            val cropped = Bitmap.createBitmap(src, sx, sy, sw, sh)
            return Bitmap.createScaledBitmap(cropped, width, height, true)
        }
    }
}
