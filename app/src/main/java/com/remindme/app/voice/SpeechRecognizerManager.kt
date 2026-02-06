package com.remindme.app.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ListeningState {
    IDLE,
    LISTENING,
    PROCESSING,
    ERROR
}

data class SpeechResult(
    val text: String,
    val confidence: Float = 0f,
    val alternatives: List<String> = emptyList()
)

class SpeechRecognizerManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    private val _listeningState = MutableStateFlow(ListeningState.IDLE)
    val listeningState: StateFlow<ListeningState> = _listeningState.asStateFlow()

    private val _speechResult = MutableStateFlow<SpeechResult?>(null)
    val speechResult: StateFlow<SpeechResult?> = _speechResult.asStateFlow()

    private val _partialResult = MutableStateFlow("")
    val partialResult: StateFlow<String> = _partialResult.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var onResultCallback: ((SpeechResult) -> Unit)? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun initialize() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createListener())
        }
    }

    fun startListening(onResult: ((SpeechResult) -> Unit)? = null) {
        onResultCallback = onResult
        _speechResult.value = null
        _partialResult.value = ""
        _errorMessage.value = null

        // Check RECORD_AUDIO permission first
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _errorMessage.value = "Microphone permission required. Please grant it in Settings."
            _listeningState.value = ListeningState.ERROR
            return
        }

        if (!isAvailable) {
            _errorMessage.value = "Speech recognition not available on this device"
            _listeningState.value = ListeningState.ERROR
            return
        }

        initialize()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }

        try {
            speechRecognizer?.startListening(intent)
            _listeningState.value = ListeningState.LISTENING
        } catch (e: Exception) {
            _errorMessage.value = "Failed to start speech recognition: ${e.message}"
            _listeningState.value = ListeningState.ERROR
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) { }
        _listeningState.value = ListeningState.IDLE
    }

    fun cancelListening() {
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) { }
        _listeningState.value = ListeningState.IDLE
        _partialResult.value = ""
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) { }
        speechRecognizer = null
        _listeningState.value = ListeningState.IDLE
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _listeningState.value = ListeningState.LISTENING
            }

            override fun onBeginningOfSpeech() {
                _listeningState.value = ListeningState.LISTENING
            }

            override fun onRmsChanged(rmsdB: Float) {
                _rmsLevel.value = rmsdB.coerceIn(0f, 12f) / 12f
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _listeningState.value = ListeningState.PROCESSING
            }

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error. Please try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error (try offline mode)"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Tap to try again."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy, please wait"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap to try again."
                    13 -> "Language unavailable. Please check speech language settings."
                    else -> "Recognition error ($error)"
                }
                _errorMessage.value = message
                _listeningState.value = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> ListeningState.IDLE
                    SpeechRecognizer.ERROR_CLIENT -> {
                        // Recreate recognizer on client error
                        speechRecognizer?.destroy()
                        speechRecognizer = null
                        ListeningState.IDLE
                    }
                    else -> ListeningState.ERROR
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidenceScores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                if (!matches.isNullOrEmpty()) {
                    val primary = matches[0]
                    val confidence = confidenceScores?.getOrNull(0) ?: 0f
                    val alternatives = if (matches.size > 1) matches.drop(1) else emptyList()

                    val result = SpeechResult(
                        text = primary,
                        confidence = confidence,
                        alternatives = alternatives
                    )
                    _speechResult.value = result
                    onResultCallback?.invoke(result)
                }

                _listeningState.value = ListeningState.IDLE
                _partialResult.value = ""
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    _partialResult.value = matches[0]
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }
}
