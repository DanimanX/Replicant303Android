package com.danimanx.replicant303.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.danimanx.replicant303.MainActivity
import com.danimanx.replicant303.R

/**
 * Keeps the process, the low-latency audio stream and the USB MIDI transport
 * alive while the screen is off. The engine itself is process-scoped.
 */
class AudioEngineService : Service() {

    private var focusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var focused = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Replicant303::engine")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CLOSE -> {
                EngineHolder.stopEngine()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                EngineHolder.get(this).setPlaying(false)
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        val controller = EngineHolder.get(this)
        Thread({
            if (controller.ensureAudio()) requestAudioFocus(controller)
            EngineHolder.getMidi(this).reconnectSaved()
        }, "engine-init").start()
        return START_STICKY
    }

    override fun onDestroy() {
        abandonAudioFocus()
        wakeLock?.let { runCatching { if (it.isHeld) it.release() } }
        wakeLock = null
        EngineHolder.stopEngine()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_description)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, AudioEngineService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val closeIntent = PendingIntent.getService(
            this,
            12,
            Intent(this, AudioEngineService::class.java).setAction(ACTION_CLOSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val state = EngineHolder.peek()?.state?.value
        val detail = when {
            state == null -> "STARTING THE 303 ENGINE"
            state.audioRunning -> "LOW-LATENCY ENGINE · ${state.audio.apiLabel} ${state.audio.sampleRate / 1000} kHz"
            else -> "SILENT CLOCK MODE · connect an audio output"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_engine)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(detail)
            .setSubText(if (state != null) "${state.presetName} · ${state.tempo.toInt()} BPM" else null)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, "STOP", stopIntent)
            .addAction(0, "CLOSE", closeIntent)
            .build()
    }

    private fun requestAudioFocus(controller: EngineController) {
        if (focused) return
        val audio = getSystemService(AudioManager::class.java) ?: return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ) {
                    controller.setPlaying(false)
                }
            }
            .build()
        val result = audio.requestAudioFocus(request)
        focused = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        focusRequest = request
    }

    private fun abandonAudioFocus() {
        if (!focused) return
        val audio = getSystemService(AudioManager::class.java)
        focusRequest?.let { audio?.abandonAudioFocusRequest(it) }
        focused = false
        focusRequest = null
    }

    companion object {
        const val CHANNEL_ID = "replicant_303_engine"
        const val NOTIFICATION_ID = 303
        const val ACTION_STOP = "com.danimanx.replicant303.STOP"
        const val ACTION_CLOSE = "com.danimanx.replicant303.CLOSE"
    }
}
