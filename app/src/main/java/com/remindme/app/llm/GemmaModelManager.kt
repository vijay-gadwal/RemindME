package com.remindme.app.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class ModelState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    LOADING,
    LOADED,
    GENERATING,
    ERROR,
    UNLOADING
}

data class ModelInfo(
    val state: ModelState = ModelState.NOT_DOWNLOADED,
    val errorMessage: String? = null,
    val modelSizeMb: Long = 0,
    val downloadProgress: Float = 0f
)

class GemmaModelManager(private val context: Context) {

    companion object {
        const val MODEL_FILENAME = "gemma-2b-it-gpu-int4.bin"
        private const val AUTO_UNLOAD_DELAY_MS = 120_000L // 2 minutes idle
        private const val MAX_TOKENS = 1024
        private const val TOP_K = 40
        private const val TEMPERATURE = 0.8f
    }

    private var llmInference: LlmInference? = null
    private var autoUnloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _modelInfo = MutableStateFlow(ModelInfo())
    val modelInfo: StateFlow<ModelInfo> = _modelInfo.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val modelDir: File
        get() = File(context.filesDir, "models")

    private val modelFile: File
        get() = File(modelDir, MODEL_FILENAME)

    init {
        checkModelStatus()
    }

    private fun checkModelStatus() {
        if (modelFile.exists()) {
            _modelInfo.value = ModelInfo(
                state = ModelState.READY,
                modelSizeMb = modelFile.length() / (1024 * 1024)
            )
        } else {
            _modelInfo.value = ModelInfo(state = ModelState.NOT_DOWNLOADED)
        }
    }

    fun isModelDownloaded(): Boolean = modelFile.exists()

    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        if (_isLoaded.value && llmInference != null) {
            resetAutoUnloadTimer()
            return@withContext true
        }

        if (!modelFile.exists()) {
            _modelInfo.value = _modelInfo.value.copy(
                state = ModelState.ERROR,
                errorMessage = "Model not found. Please download the Gemma 2B model first."
            )
            return@withContext false
        }

        _modelInfo.value = _modelInfo.value.copy(state = ModelState.LOADING)

        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(MAX_TOKENS)
                .setTopK(TOP_K)
                .setTemperature(TEMPERATURE)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            _isLoaded.value = true
            _modelInfo.value = _modelInfo.value.copy(state = ModelState.LOADED)
            resetAutoUnloadTimer()
            true
        } catch (e: Exception) {
            _modelInfo.value = _modelInfo.value.copy(
                state = ModelState.ERROR,
                errorMessage = "Failed to load model: ${e.message}"
            )
            _isLoaded.value = false
            false
        }
    }

    suspend fun generateResponse(prompt: String): String? = withContext(Dispatchers.IO) {
        // Lazy load the model if not loaded
        if (!_isLoaded.value) {
            val loaded = loadModel()
            if (!loaded) return@withContext null
        }

        val inference = llmInference ?: return@withContext null

        _modelInfo.value = _modelInfo.value.copy(state = ModelState.GENERATING)

        try {
            val response = inference.generateResponse(prompt)
            _modelInfo.value = _modelInfo.value.copy(state = ModelState.LOADED)
            resetAutoUnloadTimer()
            response?.trim()
        } catch (e: Exception) {
            _modelInfo.value = _modelInfo.value.copy(
                state = ModelState.ERROR,
                errorMessage = "Generation failed: ${e.message}"
            )
            null
        }
    }

    suspend fun generateResponseStreaming(
        prompt: String,
        onPartialResult: (String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        if (!_isLoaded.value) {
            val loaded = loadModel()
            if (!loaded) return@withContext null
        }

        val inference = llmInference ?: return@withContext null

        _modelInfo.value = _modelInfo.value.copy(state = ModelState.GENERATING)

        try {
            // MediaPipe generateResponseAsync only takes inputText, no callback.
            // Use synchronous generateResponse and report the full result.
            val response = inference.generateResponse(prompt)
            val result = response?.trim() ?: ""
            onPartialResult(result)
            _modelInfo.value = _modelInfo.value.copy(state = ModelState.LOADED)
            resetAutoUnloadTimer()
            result.ifEmpty { null }
        } catch (e: Exception) {
            _modelInfo.value = _modelInfo.value.copy(
                state = ModelState.ERROR,
                errorMessage = "Streaming generation failed: ${e.message}"
            )
            null
        }
    }

    fun unloadModel() {
        autoUnloadJob?.cancel()
        _modelInfo.value = _modelInfo.value.copy(state = ModelState.UNLOADING)
        try {
            llmInference?.close()
        } catch (_: Exception) { }
        llmInference = null
        _isLoaded.value = false
        _modelInfo.value = _modelInfo.value.copy(
            state = if (modelFile.exists()) ModelState.READY else ModelState.NOT_DOWNLOADED
        )
    }

    private fun resetAutoUnloadTimer() {
        autoUnloadJob?.cancel()
        autoUnloadJob = scope.launch {
            delay(AUTO_UNLOAD_DELAY_MS)
            unloadModel()
        }
    }

    fun destroy() {
        autoUnloadJob?.cancel()
        scope.cancel()
        unloadModel()
    }

    fun getModelPath(): String = modelFile.absolutePath

    fun getModelDirectory(): File {
        if (!modelDir.exists()) modelDir.mkdirs()
        return modelDir
    }
}
