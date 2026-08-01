package com.nothing.lyricwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nothing.lyricwidget.service.MusicDetectionService

/**
 * Second widget provider (main-screen style player widget).
 *
 * Shares all rendering logic with [NothingLyricWidget] — this subclass exists so the
 * framework hands it its own widget IDs via a separate <receiver> / appwidget-provider
 * entry, letting the shared updater distinguish the two sizes by provider class.
 */
class NothingPlayerWidget : NothingLyricWidget() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ensureDetectionService(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        ensureDetectionService(context)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    // The detection service hosts the 60fps Choreographer loop that spins the widget disc,
    // so make sure it is running whenever the player widget exists.
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
}
