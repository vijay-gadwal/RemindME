package com.remindme.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

enum class TtsState {
    INITIALIZING,
    READY,
    SPEAKING,
    ERROR,
    NOT_AVAILABLE
}

class TextToSpeechManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var utteranceId = 0

    private val _state = MutableStateFlow(TtsState.INITIALIZING)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var onSpeakingComplete: (() -> Unit)? = null

    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    _state.value = TtsState.ERROR
                } else {
                    _state.value = TtsState.READY
                    configureTts()
                }
            } else {
                _state.value = TtsState.NOT_AVAILABLE
            }
        }
    }

    private fun configureTts() {
        tts?.apply {
            setSpeechRate(1.0f)
            setPitch(1.0f)

            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    _state.value = TtsState.SPEAKING
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    _state.value = TtsState.READY
                    onSpeakingComplete?.invoke()
                    onSpeakingComplete = null
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    _state.value = TtsState.ERROR
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    _state.value = TtsState.ERROR
                }
            })
        }
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (_state.value != TtsState.READY && _state.value != TtsState.SPEAKING) return

        onSpeakingComplete = onComplete

        // Strip emojis for cleaner TTS output
        val cleanText = stripEmojis(text)
        if (cleanText.isBlank()) {
            onComplete?.invoke()
            return
        }

        val id = "utterance_${++utteranceId}"
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun speakQueued(text: String) {
        if (_state.value != TtsState.READY && _state.value != TtsState.SPEAKING) return

        val cleanText = stripEmojis(text)
        if (cleanText.isBlank()) return

        val id = "utterance_${++utteranceId}"
        tts?.speak(cleanText, TextToSpeech.QUEUE_ADD, null, id)
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        if (_state.value == TtsState.SPEAKING) {
            _state.value = TtsState.READY
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        _state.value = TtsState.INITIALIZING
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    private fun stripEmojis(text: String): String {
        // Remove common emoji patterns and special unicode characters
        return text.replace(Regex("[\\p{So}\\p{Cn}]"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }
}
