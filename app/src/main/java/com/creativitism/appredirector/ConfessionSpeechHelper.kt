package com.creativitism.appredirector

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Owns [SpeechRecognizer] create/destroy on the main thread.
 *
 * Prefers on-device recognition only when SDK ≥ 31 and
 * [SpeechRecognizer.isOnDeviceRecognitionAvailable]; otherwise uses the default
 * recognizer (platform speech service may process audio transiently). Mic denial,
 * missing recognizer, or hard recognition errors surface via [Callback] so the UI
 * can fall back to typed confession only.
 *
 * Emulator gate: many AVDs have no speech services — verify the typed path only;
 * speak-path checks are optional on a physical device.
 */
class ConfessionSpeechHelper(
    private val context: Context,
    private val callback: Callback,
) {

    interface Callback {
        fun onSpeechResult(text: String)
        /** [forceTypedFallback] true → hide mic for the rest of this session. */
        fun onSpeechError(forceTypedFallback: Boolean)
        fun onListeningChanged(listening: Boolean)
    }

    private var recognizer: SpeechRecognizer? = null
    private var listening: Boolean = false
    private var typedFallbackOnly: Boolean = false

    val isSpeechUsable: Boolean
        get() = !typedFallbackOnly && recognitionPossible()

    fun startListening() {
        ensureMainThread()
        if (typedFallbackOnly) {
            callback.onSpeechError(forceTypedFallback = true)
            return
        }
        val active = recognizer ?: createRecognizer().also { recognizer = it }
        if (active == null) {
            typedFallbackOnly = true
            callback.onSpeechError(forceTypedFallback = true)
            return
        }
        if (listening) {
            active.cancel()
        }
        listening = true
        callback.onListeningChanged(true)
        active.startListening(recognitionIntent())
    }

    fun stopListening() {
        ensureMainThread()
        if (!listening) return
        listening = false
        recognizer?.stopListening()
        callback.onListeningChanged(false)
    }

    fun destroy() {
        ensureMainThread()
        listening = false
        recognizer?.setRecognitionListener(null)
        recognizer?.destroy()
        recognizer = null
    }

    private fun createRecognizer(): SpeechRecognizer? {
        ensureMainThread()
        if (!recognitionPossible()) return null
        return try {
            val created = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            created.apply { setRecognitionListener(listener) }
        } catch (_: Exception) {
            null
        }
    }

    private fun recognitionPossible(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            return true
        }
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    private fun recognitionIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onEndOfSpeech() {
            listening = false
            callback.onListeningChanged(false)
        }

        override fun onError(error: Int) {
            listening = false
            callback.onListeningChanged(false)
            val hardFail = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                error == SpeechRecognizer.ERROR_CLIENT ||
                error == SpeechRecognizer.ERROR_SERVER
            if (hardFail) {
                typedFallbackOnly = true
                callback.onSpeechError(forceTypedFallback = true)
            } else {
                // Soft miss / timeout / busy — keep mic; UI can retry or type.
                callback.onSpeechError(forceTypedFallback = false)
            }
        }

        override fun onResults(results: Bundle?) {
            listening = false
            callback.onListeningChanged(false)
            val texts = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            val best = texts.firstOrNull { it.isNotBlank() }
            if (best != null) {
                callback.onSpeechResult(best)
            } else {
                callback.onSpeechError(forceTypedFallback = false)
            }
        }
    }

    private fun ensureMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "ConfessionSpeechHelper must be used on the main thread"
        }
    }
}
