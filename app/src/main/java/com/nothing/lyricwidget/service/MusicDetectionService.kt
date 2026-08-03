package com.nothing.lyricwidget.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Choreographer
import com.nothing.lyricwidget.service.AutoMediaService
import com.nothing.lyricwidget.utils.LyricRepository
import com.nothing.lyricwidget.widget.NothingLyricWidget

class MusicDetectionService : Service() {

    companion object {
        private const val TAG = "MusicDetectionSvc"
        private const val CHANNEL_ID = "lyric_detection"
        private const val NOTIFICATION_ID = 1

        fun isNotificationListenerEnabled(context: Context): Boolean {
            val cn = ComponentName(context, MusicNotificationListenerService::class.java)
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (flat != null && flat.contains(cn.flattenToString())) {
                return true
            }
            return false
        }
    }

    private lateinit var handler: Handler
    private var pollRunnable: Runnable? = null
    private var frameActive = false

    // Drives the home-screen widget's spinning disc at a smooth 60fps while playing.
    private val frameCallback: Choreographer.FrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!frameActive) return
            try {
                val hasWidgets = NothingLyricWidget.pushPlayerFrame(applicationContext)
                if (!hasWidgets) {
                    frameActive = false
                    return
                }
                if (LyricRepository.isPlaying) {
                    Choreographer.getInstance().postFrameCallback(this)
                } else {
                    handler.postDelayed(frameKick, 250)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Widget frame error: ${e.message}")
                frameActive = false
            }
        }
    }

    private val frameKick: Runnable = Runnable {
        if (frameActive) Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        frameActive = false
        pollRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Lyric Detection", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Detects currently playing music"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setContentTitle("Nothing Lyrics")
            .setContentText("Music detection active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    private fun startPolling() {
        pollRunnable = object : Runnable {
            override fun run() {
                detectMusic()
                if (LyricRepository.isPlaying) {
                    val changed = LyricRepository.updateLyricIndex()
                    if (changed) {
                        NothingLyricWidget.updateAllWidgets(applicationContext)
                    }
                }
                if (NothingLyricWidget.hasPlayerWidgets(applicationContext) && !frameActive) {
                    frameActive = true
                    Choreographer.getInstance().postFrameCallback(frameCallback)
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(pollRunnable!!)
    }

    private fun detectMusic() {
        try {
            // Android only exposes other apps' media metadata through a user-approved listener.
            if (isNotificationListenerEnabled(this)) {
                detectViaListener()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
        }
    }

    private fun detectViaListener() {
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager ?: return
            val component = ComponentName(this, MusicNotificationListenerService::class.java)
            val controllers = msm.getActiveSessions(component)

            for (controller in controllers) {
                val metadata = controller.metadata ?: continue
                val state = controller.playbackState
                val title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: ""
                val artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""

                if (title.isNotBlank() && artist.isNotBlank()) {
                    LyricRepository.updateTrack(
                        applicationContext, title, artist,
                        metadata.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM) ?: "",
                        metadata.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION),
                        state?.state == PlaybackState.STATE_PLAYING,
                        state?.position ?: 0L,
                        metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                            ?: metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
                    )
                    AutoMediaService.publish(controller)
                    return
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Listener fallback denied: ${e.message}")
        }
    }
}
