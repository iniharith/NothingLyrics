package com.nothing.lyricwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nothing.lyricwidget.MainActivity
import com.nothing.lyricwidget.R
import com.nothing.lyricwidget.utils.LyricRepository

class NothingLyricWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_small)

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)

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

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, NothingLyricWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }
}
