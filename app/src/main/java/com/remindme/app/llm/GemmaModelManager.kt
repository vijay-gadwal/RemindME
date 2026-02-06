package com.remindme.app.llm

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

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

class GemmaModelManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "GemmaModelManager"
        private const val AUTO_UNLOAD_DELAY_MS = 120_000L // 2 minutes idle
        private const val MAX_TOKENS = 1024
        private const val TOP_K = 40
        private val SUPPORTED_EXTENSIONS = listOf(".bin", ".task", ".litertlm")

        @Volatile
        private var INSTANCE: GemmaModelManager? = null

        fun getInstance(context: Context): GemmaModelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GemmaModelManager(context.applicationContext).also { INSTANCE = it }
            }
        }
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

    private fun findModelFile(): File? {
        if (!modelDir.exists()) return null
        return modelDir.listFiles()?.firstOrNull { file ->
            SUPPORTED_EXTENSIONS.any { ext -> file.name.endsWith(ext, ignoreCase = true) }
                    && file.length() > 100_000_000 // at least ~100MB to be a real model
        }
    }

    init {
        checkModelStatus()
    }

    private fun checkModelStatus() {
        val file = findModelFile()
        if (file != null) {
            Log.d(TAG, "Found model: ${file.name} (${file.length() / (1024*1024)} MB)")
            _modelInfo.value = ModelInfo(
                state = ModelState.READY,
                modelSizeMb = file.length() / (1024 * 1024)
            )
        } else {
            _modelInfo.value = ModelInfo(state = ModelState.NOT_DOWNLOADED)
        }
    }

    fun isModelDownloaded(): Boolean = findModelFile() != null

    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        if (_isLoaded.value && llmInference != null) {
            resetAutoUnloadTimer()
            return@withContext true
        }

        val file = findModelFile()
        if (file == null) {
            _modelInfo.value = _modelInfo.value.copy(
                state = ModelState.ERROR,
                errorMessage = "Model not found. Import a Gemma 2B model file (.bin) first."
            )
            return@withContext false
        }

        Log.d(TAG, "Loading model: ${file.name} (${file.length() / (1024*1024)} MB)")
        _modelInfo.value = _modelInfo.value.copy(state = ModelState.LOADING)

        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(MAX_TOKENS)
                .setMaxTopK(TOP_K)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            _isLoaded.value = true
            _modelInfo.value = _modelInfo.value.copy(state = ModelState.LOADED)
            Log.d(TAG, "Model loaded successfully")
            resetAutoUnloadTimer()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${file.name}", e)
            val errorMsg = e.message ?: "Unknown error"
            val userMessage = when {
                errorMsg.contains("Error building model", ignoreCase = true)
                        || errorMsg.contains("RET_CHECK", ignoreCase = true)
                        || errorMsg.contains("failed to initialize session", ignoreCase = true) -> {
                    val isGpu = file.name.contains("gpu", ignoreCase = true)
                    if (isGpu) {
                        "GPU model failed to load — your device may not support it. " +
                        "Please import the CPU variant instead: gemma-2b-it-cpu-int4.bin"
                    } else {
                        "Model file may be corrupted or incompatible. " +
                        "Try re-downloading: gemma-2b-it-cpu-int4.bin from Kaggle."
                    }
                }
                errorMsg.contains("out of memory", ignoreCase = true) ||
                        errorMsg.contains("OOM", ignoreCase = true) -> {
                    "Not enough memory to load model. Close other apps and try again."
                }
                else -> "Failed to load model: $errorMsg"
            }
            _modelInfo.value = _modelInfo.value.copy(
                state = ModelState.ERROR,
                errorMessage = userMessage
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
            // Use synchronous generateResponse and report the full result
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
            state = if (findModelFile() != null) ModelState.READY else ModelState.NOT_DOWNLOADED
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

    fun getModelPath(): String = findModelFile()?.absolutePath ?: ""

    fun getModelDirectory(): File {
        if (!modelDir.exists()) modelDir.mkdirs()
        return modelDir
    }

    fun getLoadedModelName(): String = findModelFile()?.name ?: "none"

    suspend fun copyModelFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            _modelInfo.value = _modelInfo.value.copy(
                state = ModelState.DOWNLOADING,
                downloadProgress = 0f
            )

            if (!modelDir.exists()) modelDir.mkdirs()

            // Resolve the original filename from the URI
            val resolvedName = resolveFileName(uri) ?: "model.bin"
            val targetFile = File(modelDir, resolvedName)
            Log.d(TAG, "Importing model as: $resolvedName")

            // Remove any existing model files first
            modelDir.listFiles()?.forEach { existing ->
                if (SUPPORTED_EXTENSIONS.any { ext -> existing.name.endsWith(ext, ignoreCase = true) }) {
                    Log.d(TAG, "Removing old model: ${existing.name}")
                    existing.delete()
                }
            }

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw Exception("Cannot open file")

            // Use file descriptor to get accurate size
            val fd = context.contentResolver.openFileDescriptor(uri, "r")
            val totalBytes = fd?.statSize ?: inputStream.available().toLong()
            fd?.close()
            val total = totalBytes.coerceAtLeast(1L)
            var copiedBytes = 0L

            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    copiedBytes += bytesRead
                    _modelInfo.value = _modelInfo.value.copy(
                        downloadProgress = (copiedBytes.toFloat() / total).coerceIn(0f, 1f)
                    )
                }
            }
            inputStream.close()

            Log.d(TAG, "Model file copied: ${targetFile.length()} bytes -> ${targetFile.name}")

            _modelInfo.value = ModelInfo(
                state = ModelState.READY,
                modelSizeMb = targetFile.length() / (1024 * 1024)
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model file", e)
            // Clean up partial files
            modelDir.listFiles()?.forEach { f ->
                if (SUPPORTED_EXTENSIONS.any { ext -> f.name.endsWith(ext, ignoreCase = true) } && f.length() < 100_000_000) {
                    f.delete()
                }
            }
            _modelInfo.value = _modelInfo.value.copy(
                state = ModelState.ERROR,
                errorMessage = "Import failed: ${e.message}"
            )
            false
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    return it.getString(nameIndex)
                }
            }
        }
        return uri.lastPathSegment
    }

    suspend fun testModel(): String = withContext(Dispatchers.IO) {
        val file = findModelFile()
        Log.d(TAG, "Testing model. File: ${file?.name}, exists: ${file?.exists()}, size: ${file?.length()} bytes")

        if (file == null) {
            return@withContext "FAIL: No model file found in ${modelDir.absolutePath}"
        }

        try {
            val loaded = loadModel()
            if (!loaded) {
                return@withContext "FAIL: ${_modelInfo.value.errorMessage}"
            }

            val response = llmInference?.generateResponse("Say hello in one sentence.")
            if (response.isNullOrBlank()) {
                return@withContext "WARN: Model loaded but returned empty response"
            }

            Log.d(TAG, "Model test response: $response")
            "OK: ${file.name} loaded and responding.\nResponse: ${response.trim().take(200)}"
        } catch (e: Exception) {
            Log.e(TAG, "Model test failed", e)
            "FAIL: ${e.message}"
        }
    }

    fun deleteModel() {
        unloadModel()
        modelDir.listFiles()?.forEach { f ->
            if (SUPPORTED_EXTENSIONS.any { ext -> f.name.endsWith(ext, ignoreCase = true) }) {
                Log.d(TAG, "Deleting model: ${f.name}")
                f.delete()
            }
        }
        _modelInfo.value = ModelInfo(state = ModelState.NOT_DOWNLOADED)
    }

}
