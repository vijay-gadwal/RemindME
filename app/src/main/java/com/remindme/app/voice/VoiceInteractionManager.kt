package com.remindme.app.voice

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceInteractionState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

class VoiceInteractionManager(context: Context) {

    val speechRecognizer = SpeechRecognizerManager(context)
    val tts = TextToSpeechManager(context)

    private val _interactionState = MutableStateFlow(VoiceInteractionState.IDLE)
    val interactionState: StateFlow<VoiceInteractionState> = _interactionState.asStateFlow()

    private val _autoReadResponses = MutableStateFlow(true)
    val autoReadResponses: StateFlow<Boolean> = _autoReadResponses.asStateFlow()

    fun initialize() {
        speechRecognizer.initialize()
        tts.initialize()
    }

    fun setAutoReadResponses(enabled: Boolean) {
        _autoReadResponses.value = enabled
    }

    fun startListening(onResult: (String) -> Unit) {
        // Stop TTS if currently speaking
        if (tts.isSpeaking.value) {
            tts.stop()
        }

        _interactionState.value = VoiceInteractionState.LISTENING

        speechRecognizer.startListening { result ->
            _interactionState.value = VoiceInteractionState.PROCESSING
            onResult(result.text)
        }
    }

    fun stopListening() {
        speechRecognizer.stopListening()
        _interactionState.value = VoiceInteractionState.IDLE
    }

    fun cancelListening() {
        speechRecognizer.cancelListening()
        _interactionState.value = VoiceInteractionState.IDLE
    }

    fun speakResponse(text: String, onComplete: (() -> Unit)? = null) {
        if (!_autoReadResponses.value) {
            onComplete?.invoke()
            return
        }

        _interactionState.value = VoiceInteractionState.SPEAKING
        tts.speak(text) {
            _interactionState.value = VoiceInteractionState.IDLE
            onComplete?.invoke()
        }
    }

    fun stopSpeaking() {
        tts.stop()
        _interactionState.value = VoiceInteractionState.IDLE
    }

    fun destroy() {
        speechRecognizer.destroy()
        tts.shutdown()
        _interactionState.value = VoiceInteractionState.IDLE
    }
}
