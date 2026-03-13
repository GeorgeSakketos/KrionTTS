package com.krion.tts.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.krion.tts.domain.AudioExporter
import com.krion.tts.domain.DownloadState
import com.krion.tts.domain.LanguageModel
import com.krion.tts.domain.ModelCatalog
import com.krion.tts.domain.ModelRepository
import com.krion.tts.domain.OfflineTtsManager
import com.krion.tts.domain.PlaybackEndReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class KrionViewModel(
    private val modelRepository: ModelRepository,
    private val ttsManager: OfflineTtsManager,
    private val audioExporter: AudioExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(KrionUiState())
    val uiState: StateFlow<KrionUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "KrionViewModel"
    }

    init {
        refreshModels()
    }

    fun openModelsPage() {
        _uiState.update { it.copy(currentPage = KrionPage.MODELS) }
    }

    fun closeModelsPage() {
        _uiState.update { it.copy(currentPage = KrionPage.MAIN) }
    }

    fun selectLanguageFilter(filter: String) {
        _uiState.update { it.copy(selectedLanguageFilter = filter) }
    }

    fun onTextChanged(value: String) {
        _uiState.update { it.copy(inputText = value) }
    }

    fun onSpeakerIdChanged(value: String) {
        val sanitized = value.filter { it.isDigit() }
        _uiState.update { it.copy(speakerIdInput = sanitized) }
    }

    fun clampSpeakerIdInput() {
        resolveSpeakerId()
    }

    fun decrementSpeakerId() {
        _uiState.update { state ->
            val current = state.speakerIdInput.toIntOrNull() ?: 0
            val next = (current - 1).coerceAtLeast(0)
            state.copy(speakerIdInput = next.toString())
        }
    }

    fun incrementSpeakerId() {
        _uiState.update { state ->
            val current = state.speakerIdInput.toIntOrNull() ?: 0
            val next = (current + 1).coerceAtMost(state.maxSpeakerId)
            state.copy(speakerIdInput = next.toString())
        }
    }

    fun downloadModel(modelId: String) {
        val model = ModelCatalog.models.firstOrNull { it.id == modelId } ?: return

        viewModelScope.launch {
            updateModelState(modelId, DownloadState.DOWNLOADING, progress = 0)
            _uiState.update { it.copy(statusMessage = "Downloading ${model.modelName}...") }

            runCatching {
                modelRepository.downloadModel(model) { progress ->
                    updateModelProgress(modelId, progress)
                }
                modelRepository.setSelectedModelId(model.id)
            }.onSuccess {
                refreshModels()
                refreshSpeakerInfo(model)
                _uiState.update {
                    it.copy(statusMessage = "${model.displayName} model installed and selected")
                }
            }.onFailure { error ->
                updateModelState(modelId, DownloadState.FAILED, progress = 0)
                _uiState.update { state ->
                    state.copy(statusMessage = "Download failed: ${error.message}")
                }
            }
        }
    }

    fun selectModel(modelId: String) {
        val model = ModelCatalog.models.firstOrNull { it.id == modelId } ?: return
        if (!modelRepository.isInstalled(model)) {
            _uiState.update { it.copy(statusMessage = "Download this model first") }
            return
        }

        modelRepository.setSelectedModelId(model.id)
        refreshModels()
        refreshSpeakerInfo(model)
        _uiState.update { it.copy(statusMessage = "Selected ${model.displayName}") }
    }

    fun play() {
        val state = _uiState.value
        val selected = state.models.firstOrNull { it.isSelected }?.model
            ?: run {
                _uiState.update { it.copy(statusMessage = "Please select a downloaded model") }
                return
            }

        if (state.inputText.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Enter text to play") }
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "play() called")
            _uiState.update { it.copy(isBusy = true, isPlaying = false, isPaused = false, statusMessage = "Preparing audio...") }
            runCatching {
                val paths = modelRepository.getInstalledModelPaths(selected)
                    ?: throw IllegalStateException("Selected model is not installed")
                ttsManager.initialize(selected, paths)
                val speakerId = resolveSpeakerId()
                Log.d(TAG, "Setting state to playing, calling speak()")
                _uiState.update { it.copy(isBusy = false, isPlaying = true, isPaused = false, statusMessage = "Playing audio...") }
                ttsManager.speak(state.inputText, speakerId)
            }.onSuccess { reason ->
                Log.d(TAG, "speak() completed successfully with reason: $reason")
                when (reason) {
                    PlaybackEndReason.COMPLETED -> {
                        Log.d(TAG, "Updating UI state to playback finished")
                        _uiState.update { current ->
                            current.copy(
                                isBusy = false,
                                isPlaying = false,
                                isPaused = false,
                                statusMessage = "Playback finished"
                            )
                        }
                    }

                    PlaybackEndReason.STOPPED -> {
                        Log.d(TAG, "Updating UI state to stopped")
                        _uiState.update { current ->
                            current.copy(
                                isBusy = false,
                                isPlaying = false,
                                isPaused = false
                            )
                        }
                    }
                }
                Log.d(TAG, "UI state updated after playback")
            }.onFailure { error ->
                Log.e(TAG, "play() failed", error)
                _uiState.update { current ->
                    current.copy(isBusy = false, isPlaying = false, isPaused = false, statusMessage = "Play failed: ${error.message}")
                }
            }
        }
    }

    fun stop() {
        ttsManager.stop()
        _uiState.update { it.copy(isPlaying = false, isPaused = false, statusMessage = "Playback stopped") }
    }

    fun pause() {
        ttsManager.pause()
        _uiState.update { it.copy(isPlaying = false, isPaused = true, statusMessage = "Playback paused") }
    }

    fun resume() {
        ttsManager.resume()
        _uiState.update { it.copy(isPlaying = true, isPaused = false, statusMessage = "Playback resumed") }
    }

    fun deleteModel(modelId: String) {
        val model = ModelCatalog.models.firstOrNull { it.id == modelId } ?: return
        modelRepository.deleteModel(model)
        refreshModels()
        _uiState.update { it.copy(statusMessage = "${model.displayName} deleted") }
    }

    fun saveWav() {
        exportAudio(isMp3 = false)
    }

    fun saveMp3() {
        exportAudio(isMp3 = true)
    }

    fun exportToUri(uri: android.net.Uri, isMp3: Boolean) {
        val state = _uiState.value
        val selected = state.models.firstOrNull { it.isSelected }?.model
            ?: run {
                _uiState.update { it.copy(statusMessage = "Please select a downloaded model") }
                return
            }

        if (state.inputText.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Enter text to export") }
            return
        }

        viewModelScope.launch {
            val ext = if (isMp3) "MP3" else "WAV"
            ttsManager.stop()
            _uiState.update {
                it.copy(
                    isBusy = true,
                    isPlaying = false,
                    isPaused = false,
                    statusMessage = "Exporting $ext..."
                )
            }

            runCatching {
                val paths = modelRepository.getInstalledModelPaths(selected)
                    ?: throw IllegalStateException("Selected model is not installed")
                ttsManager.initialize(selected, paths)
                val speakerId = resolveSpeakerId()
                val wavFile = ttsManager.synthesizeToWav(state.inputText, speakerId)

                audioExporter.exportToUri(wavFile, uri, isMp3)
            }.onSuccess {
                _uiState.update {
                    it.copy(isBusy = false, statusMessage = "$ext exported successfully")
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(isBusy = false, statusMessage = "Export failed: ${error.message}")
                }
            }
        }
    }

    private fun exportAudio(isMp3: Boolean) {
        val state = _uiState.value
        val selected = state.models.firstOrNull { it.isSelected }?.model
            ?: run {
                _uiState.update { it.copy(statusMessage = "Please select a downloaded model") }
                return
            }

        if (state.inputText.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Enter text to export") }
            return
        }

        viewModelScope.launch {
            val ext = if (isMp3) "MP3" else "WAV"
            ttsManager.stop()
            _uiState.update {
                it.copy(
                    isBusy = true,
                    isPlaying = false,
                    isPaused = false,
                    statusMessage = "Exporting $ext..."
                )
            }

            runCatching {
                val paths = modelRepository.getInstalledModelPaths(selected)
                    ?: throw IllegalStateException("Selected model is not installed")
                ttsManager.initialize(selected, paths)
                val speakerId = resolveSpeakerId()
                val wavFile = ttsManager.synthesizeToWav(state.inputText, speakerId)
                val fileName = "krion_${selected.languageCode}_${System.currentTimeMillis()}"

                if (isMp3) {
                    audioExporter.exportMp3(wavFile, fileName)
                } else {
                    audioExporter.exportWav(wavFile, fileName)
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(isBusy = false, statusMessage = "$ext exported to Downloads")
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(isBusy = false, statusMessage = "Export failed: ${error.message}")
                }
            }
        }
    }

    private fun resolveSpeakerId(): Int {
        val parsed = _uiState.value.speakerIdInput.toIntOrNull() ?: 0
        val max = _uiState.value.maxSpeakerId
        val clamped = parsed.coerceIn(0, max)
        if (clamped.toString() != _uiState.value.speakerIdInput) {
            _uiState.update { it.copy(speakerIdInput = clamped.toString()) }
        }
        return clamped
    }

    private fun refreshSpeakerInfo(model: LanguageModel) {
        viewModelScope.launch {
            runCatching {
                val paths = modelRepository.getInstalledModelPaths(model) ?: return@runCatching null
                ttsManager.initialize(model, paths)
                ttsManager.speakerCount()
            }.onSuccess { countOrNull ->
                if (countOrNull != null) {
                    val maxSpeaker = (countOrNull - 1).coerceAtLeast(0)
                    _uiState.update { state ->
                        val current = state.speakerIdInput.toIntOrNull() ?: 0
                        val clamped = current.coerceIn(0, maxSpeaker)
                        state.copy(maxSpeakerId = maxSpeaker, speakerIdInput = clamped.toString())
                    }
                }
            }
        }
    }

    private fun refreshModels() {
        val selected = modelRepository.selectedModelId()
        val items = ModelCatalog.models.map { model ->
            ModelItemUi(
                model = model,
                state = if (modelRepository.isInstalled(model)) DownloadState.INSTALLED else DownloadState.NOT_INSTALLED,
                isSelected = selected == model.id,
                downloadProgress = 0
            )
        }

        val filters = listOf("All") + items.map { it.model.languageCode }.distinct().sorted()
        val selectedFilter = _uiState.value.selectedLanguageFilter.takeIf { it in filters } ?: "All"

        _uiState.update {
            it.copy(
                models = items,
                selectedModelId = selected,
                languageFilters = filters,
                selectedLanguageFilter = selectedFilter
            )
        }

        val selectedModel = items.firstOrNull { it.isSelected }?.model
        if (selectedModel != null && modelRepository.isInstalled(selectedModel)) {
            refreshSpeakerInfo(selectedModel)
        }
    }

    private fun updateModelState(modelId: String, state: DownloadState, progress: Int) {
        _uiState.update { current ->
            current.copy(
                models = current.models.map {
                    if (it.model.id == modelId) it.copy(state = state, downloadProgress = progress) else it
                }
            )
        }
    }

    private fun updateModelProgress(modelId: String, progress: Int) {
        _uiState.update { current ->
            current.copy(
                models = current.models.map {
                    if (it.model.id == modelId) it.copy(state = DownloadState.DOWNLOADING, downloadProgress = progress) else it
                }
            )
        }
    }

    override fun onCleared() {
        ttsManager.shutdown()
        super.onCleared()
    }

    class Factory(
        private val modelRepository: ModelRepository,
        private val ttsManager: OfflineTtsManager,
        private val audioExporter: AudioExporter
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return KrionViewModel(modelRepository, ttsManager, audioExporter) as T
        }
    }
}
