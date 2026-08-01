package com.nothing.lyricwidget.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.nothing.lyricwidget.utils.LyricRepository

class MusicNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "MusicNLService"
        @Volatile private var currentController: MediaController? = null

        fun play() = currentController?.transportControls?.play()
        fun pause() = currentController?.transportControls?.pause()
        fun skipToNext() = currentController?.transportControls?.skipToNext()
        fun skipToPrevious() = currentController?.transportControls?.skipToPrevious()
    }

    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var controllerCallback: MediaController.Callback? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (LyricRepository.isPlaying) {
                val changed = LyricRepository.updateLyricIndex()
                if (changed) {
                    com.nothing.lyricwidget.widget.NothingLyricWidget.updateAllWidgets(applicationContext)
                }
            }
            handler.postDelayed(this, 300)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener connected")
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        refreshController()
        handler.post(pollRunnable)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Listener disconnected")
        handler.removeCallbacks(pollRunnable)
        unregisterCallback()
        currentController = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        refreshController()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        refreshController()
    }

    private fun refreshController() {
        try {
            val msm = mediaSessionManager ?: return
            val component = ComponentName(this, MusicNotificationListenerService::class.java)
            val controllers = msm.getActiveSessions(component)

            if (controllers.isEmpty()) {
                unregisterCallback()
                activeController = null
                currentController = null
                return
            }

            var best: MediaController? = null
            for (c in controllers) {
                val state = try { c.playbackState } catch (_: Exception) { null }
                if (state?.state == PlaybackState.STATE_PLAYING) {
                    best = c
                    break
                }
            }
            if (best == null) best = controllers.firstOrNull()

            if (best == activeController) {
                best?.let { safeUpdate(it) }
                return
            }

            unregisterCallback()
            activeController = best
            currentController = best

            if (best != null) {
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        safeUpdate(best)
                    }
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        safeUpdate(best)
                    }
                    override fun onSessionDestroyed() {
                        Log.d(TAG, "Session destroyed for ${best.packageName}, refreshing")
                        if (activeController == best) {
                            activeController = null
                            currentController = null
                        }
                        refreshController()
                    }
                }
                best.registerCallback(cb)
                controllerCallback = cb
                safeUpdate(best)
                Log.d(TAG, "Registered to: ${best.packageName}")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
        }
    }

    private fun unregisterCallback() {
        try {
            activeController?.unregisterCallback(controllerCallback ?: return)
        } catch (_: Exception) {}
        controllerCallback = null
    }

    /**
     * MediaController.Callback methods (onPlaybackStateChanged/onMetadataChanged) fire from the
     * framework whenever the session updates. On Android Auto, sessions die/get recreated far
     * more often (audio focus ducking for nav prompts, BT handoff, app switching), so calling
     * into a controller whose session has just died throws IllegalStateException. Uncaught, that
     * crashes the whole app. Always go through this wrapper instead of calling
     * updateFromController() directly.
     */
    private fun safeUpdate(controller: MediaController) {
        try {
            updateFromController(controller)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Stale controller (session died), refreshing: ${e.message}")
            if (activeController == controller) {
                unregisterCallback()
                activeController = null
                currentController = null
            }
            refreshController()
        } catch (e: Exception) {
            Log.e(TAG, "safeUpdate error: ${e.message}")
        }
    }

    private fun updateFromController(controller: MediaController) {
        val metadata = controller.metadata ?: return
        val state = controller.playbackState

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val position = state?.position ?: 0L

        currentController = controller

        if (title.isNotBlank()) {
            LyricRepository.updateTrack(applicationContext, title, artist, album, duration, isPlaying, position, albumArt)
            AutoMediaService.publish(controller)
        }
    }
}
