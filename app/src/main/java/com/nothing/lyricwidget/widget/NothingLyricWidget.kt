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
import android.os.Bundle
import android.widget.RemoteViews
import com.nothing.lyricwidget.MainActivity
import com.nothing.lyricwidget.R
import com.nothing.lyricwidget.service.MusicNotificationListenerService
import com.nothing.lyricwidget.utils.LyricRepository
import kotlin.math.roundToInt

open class NothingLyricWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, this::class.java)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        updateWidget(context, appWidgetManager, appWidgetId, this::class.java)
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
        private const val DISC_ROTATION_STEP = 9f

        @Volatile
        private var discRotation = 0f

        fun updateAllWidgets(context: Context) {
            updateAllForProvider(context, NothingLyricWidget::class.java)
            updateAllForProvider(context, NothingPlayerWidget::class.java)
        }

        private fun updateAllForProvider(context: Context, providerClass: Class<*>) {
            val manager = AppWidgetManager.getInstance(context)
            for (appWidgetId in manager.getAppWidgetIds(ComponentName(context, providerClass))) {
                updateWidget(context, manager, appWidgetId, providerClass)
            }
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            providerClass: Class<*>
        ) {
            if (providerClass == NothingPlayerWidget::class.java) {
                updatePlayerWidget(context, appWidgetManager, appWidgetId)
            } else {
                updateSmallWidget(context, appWidgetManager, appWidgetId)
            }
        }

        private fun updateSmallWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_small)

            val openIntent = Intent(context, MainActivity::class.java)
            val openPendingIntent = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, openPendingIntent)

            val index = LyricRepository.currentLyricIndex

            if (LyricRepository.currentTrack.isBlank()) {
                views.setTextViewText(R.id.widget_title, "Nothing Lyrics")
                views.setTextViewText(R.id.widget_artist, "Open music player first")
                views.setTextViewText(R.id.widget_lyric_line1, "Waiting for music...")
                views.setTextViewText(R.id.widget_lyric_line2, "")
            } else {
                views.setTextViewText(R.id.widget_title, LyricRepository.currentTrack)
                views.setTextViewText(R.id.widget_artist, LyricRepository.currentArtist)

                if (LyricRepository.lyricLines.isEmpty()) {
                    views.setTextViewText(R.id.widget_lyric_line1, "Fetching lyrics...")
                    views.setTextViewText(R.id.widget_lyric_line2, "Connecting to LRCLIB")
                } else {
                    val currentLine = LyricRepository.getLyricAt(index)
                    val displayCurrent = if (currentLine.isBlank()) "..." else currentLine
                    views.setTextViewText(R.id.widget_lyric_line1, displayCurrent)
                    views.setTextViewText(R.id.widget_lyric_line2, LyricRepository.getLyricAt(index + 1))
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun updatePlayerWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_player)

            val openIntent = Intent(context, MainActivity::class.java)
            val openPendingIntent = PendingIntent.getActivity(
                context, 1, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val isPlaying = LyricRepository.isPlaying
            val index = LyricRepository.currentLyricIndex
            val track = LyricRepository.currentTrack
            val artist = LyricRepository.currentArtist

            // Spinning vinyl disc: album art (or "NL" fallback) clipped into a dark disc with
            // ring border, rotated a fixed step per refresh so it slowly spins while playing.
            views.setImageViewBitmap(R.id.widget_player_disc, buildDiscBitmap(LyricRepository.currentAlbumArt))
            views.setFloat(R.id.widget_player_disc, "setRotation", discRotation)
            if (isPlaying) {
                discRotation = (discRotation + DISC_ROTATION_STEP) % 360f
            }
            views.setOnClickPendingIntent(R.id.widget_player_disc, openPendingIntent)

            views.setTextViewText(R.id.widget_title, track.ifBlank { "Nothing Lyrics" })
            views.setTextViewText(R.id.widget_artist, artist.ifBlank { "Open music player first" })
            views.setTextViewText(
                R.id.widget_lyric_line1,
                if (track.isBlank()) {
                    "Play music to see lyrics"
                } else if (LyricRepository.lyricLines.isEmpty()) {
                    "Fetching lyrics..."
                } else {
                    LyricRepository.getLyricAt(index).ifBlank { "..." }
                }
            )

            views.setImageViewResource(
                R.id.widget_player_play_pause,
                if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )
            views.setOnClickPendingIntent(R.id.widget_player_prev, controlPendingIntent(context, CONTROL_PREV))
            views.setOnClickPendingIntent(
                R.id.widget_player_play_pause,
                controlPendingIntent(context, if (isPlaying) CONTROL_PAUSE else CONTROL_PLAY)
            )
            views.setOnClickPendingIntent(R.id.widget_player_next, controlPendingIntent(context, CONTROL_NEXT))

            appWidgetManager.updateAppWidget(appWidgetId, views)
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

        private fun buildDiscBitmap(artwork: Bitmap?): Bitmap {
            val size = 240
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val center = size / 2f

            // Disc body + ring border (same palette as the app's vinyl screen).
            canvas.drawCircle(center, center, center, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF1B1C1E.toInt()
            })
            canvas.drawCircle(center, center, center - 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = 0xFF38393B.toInt()
            })

            val artSize = (size * 0.62f).roundToInt()
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
                    textSize = artSize * 0.34f
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }
                canvas.drawText("NL", center, center - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
            }

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
