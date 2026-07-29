package com.creativitism.appredirector

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Owns the alarm's klaxon + vibration. Plays [R.raw.alarm_klaxon] on the
 * `USAGE_ALARM` stream so it survives ringer/media mute, and vibrates alongside it.
 * Volume escalates over the countdown on the alarm stream. If the alarm stream is
 * silenced, MediaPlayer fails, or playback is otherwise inaudible, falls back to
 * aggressive vibration (and a ToneGenerator loop when the stream still has volume).
 */
class AlarmAudioController(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var isVibrating = false

    private var originalAlarmVolume: Int? = null
    private val handler = Handler(Looper.getMainLooper())
    private var escalateRunnable: Runnable? = null
    private var toneRunnable: Runnable? = null

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
        val audible = isAudible()
        val klaxonStarted = if (audible) startKlaxon() else false
        if (klaxonStarted) {
            vibrate(aggressive = false)
            startVolumeEscalation(countdownMillis)
        } else {
            // Silent stream or MediaPlayer failure: never stay on mild vibe alone.
            vibrate(aggressive = true)
            if (audible) {
                startToneFallback()
            }
        }
    }

    fun stop() {
        cancelEscalation()
        cancelToneFallback()
        restoreAlarmVolume()
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

    /** @return true if looping klaxon is playing. */
    private fun startKlaxon(): Boolean {
        return try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                isLooping = true
                val afd = context.resources.openRawResourceFd(R.raw.alarm_klaxon)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
                start()
            }
            mediaPlayer = player
            true
        } catch (_: Exception) {
            mediaPlayer?.release()
            mediaPlayer = null
            false
        }
    }

    /**
     * Ramp [AudioManager.STREAM_ALARM] from a low audible level up to max over
     * [countdownMillis] (~10s). Original volume is restored in [stop].
     */
    private fun startVolumeEscalation(countdownMillis: Long) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (max <= 0) return
        originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val startVol = 1.coerceAtMost(max)
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, startVol, 0)
        }
        val steps = 20
        val interval = (countdownMillis / steps).coerceAtLeast(50L)
        var step = 0
        val runnable = object : Runnable {
            override fun run() {
                step++
                val vol = (startVol + ((max - startVol) * step / steps)).coerceIn(0, max)
                runCatching {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, vol, 0)
                }
                if (step < steps) {
                    handler.postDelayed(this, interval)
                }
            }
        }
        escalateRunnable = runnable
        handler.postDelayed(runnable, interval)
    }

    private fun cancelEscalation() {
        escalateRunnable?.let { handler.removeCallbacks(it) }
        escalateRunnable = null
    }

    private fun restoreAlarmVolume() {
        val original = originalAlarmVolume ?: return
        originalAlarmVolume = null
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, original, 0)
        }
    }

    /** ToneGenerator loop when MediaPlayer fails but the alarm stream still has volume. */
    private fun startToneFallback() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
            toneGenerator = tg
            val runnable = object : Runnable {
                override fun run() {
                    runCatching {
                        tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                    }
                    handler.postDelayed(this, 500)
                }
            }
            toneRunnable = runnable
            handler.post(runnable)
        } catch (_: Exception) {
            toneGenerator?.release()
            toneGenerator = null
        }
    }

    private fun cancelToneFallback() {
        toneRunnable?.let { handler.removeCallbacks(it) }
        toneRunnable = null
        toneGenerator?.release()
        toneGenerator = null
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
