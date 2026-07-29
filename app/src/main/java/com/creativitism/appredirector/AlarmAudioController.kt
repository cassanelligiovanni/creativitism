package com.creativitism.appredirector

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Owns the alarm's klaxon + vibration. Plays [R.raw.alarm_klaxon] on the
 * `USAGE_ALARM` stream so it survives ringer/media mute, and vibrates alongside it.
 * If the alarm stream is silenced (volume 0), falls back to an aggressive vibration
 * pattern since the user may have no other way to notice the alarm.
 */
class AlarmAudioController(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var isVibrating = false

    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val vibrator: Vibrator
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    /** True when the alarm stream has any volume, i.e. the klaxon will actually be heard. */
    fun isAudible(): Boolean = audioManager.getStreamVolume(AudioManager.STREAM_ALARM) > 0

    fun start(countdownMillis: Long) {
        stop()
        if (isAudible()) {
            startKlaxon()
            vibrate(aggressive = false)
        } else {
            // Silent alarm stream: the klaxon would be pointless, so lean harder on vibration.
            vibrate(aggressive = true)
        }
    }

    fun stop() {
        mediaPlayer?.let { player ->
            runCatching { if (player.isPlaying) player.stop() }
            player.release()
        }
        mediaPlayer = null
        if (isVibrating) {
            vibrator.cancel()
            isVibrating = false
        }
    }

    private fun startKlaxon() {
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            isLooping = true
            try {
                val afd = context.resources.openRawResourceFd(R.raw.alarm_klaxon)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
                start()
            } catch (e: Exception) {
                release()
                mediaPlayer = null
            }
        }
    }

    private fun vibrate(aggressive: Boolean) {
        isVibrating = true
        val timings = if (aggressive) {
            longArrayOf(0, 400, 100, 400, 100)
        } else {
            longArrayOf(0, 300, 300)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = if (aggressive) 255 else 160
            val amplitudes = IntArray(timings.size) { index -> if (index % 2 == 0) 0 else amplitude }
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, 0)
        }
    }
}
