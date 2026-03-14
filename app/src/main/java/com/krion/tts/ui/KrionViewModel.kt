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
import java.util.Locale

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

    fun selectLanguageFilter(filterId: String) {
        _uiState.update { it.copy(selectedLanguageFilterId = filterId) }
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
        val current = _uiState.value.speakerIdInput.toIntOrNull() ?: 0
        val next = (current - 1).coerceAtLeast(0)
        _uiState.update { state -> state.copy(speakerIdInput = next.toString()) }
        persistCurrentSpeakerId(next)
    }

    fun incrementSpeakerId() {
        val current = _uiState.value.speakerIdInput.toIntOrNull() ?: 0
        val next = (current + 1).coerceAtMost(_uiState.value.maxSpeakerId)
        _uiState.update { state -> state.copy(speakerIdInput = next.toString()) }
        persistCurrentSpeakerId(next)
    }

    fun downloadModel(modelId: String) {
        val model = ModelCatalog.models.firstOrNull { it.id == modelId } ?: return

        viewModelScope.launch {
            updateModelState(modelId, DownloadState.DOWNLOADING, progress = 0)
            _uiState.update {
                it.copy(
                    isBusy = true,
                    isPlaying = false,
                    isPaused = false,
                    statusMessage = "Downloading ${model.modelName}..."
                )
            }

            runCatching {
                modelRepository.downloadModel(model) { progress ->
                    updateModelProgress(modelId, progress)
                }
                modelRepository.saveLastSpeakerId(model.id, 0)
                modelRepository.setSelectedModelId(model.id)
            }.onSuccess {
                refreshModels()
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        autoRestartRequested = true,
                        statusMessage = "${model.displayName} model installed. Restarting app..."
                    )
                }
            }.onFailure { error ->
                updateModelState(modelId, DownloadState.FAILED, progress = 0)
                _uiState.update { state ->
                    state.copy(
                        isBusy = false,
                        autoRestartRequested = false,
                        statusMessage = "Download failed: ${error.message}"
                    )
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
                val maxSpeaker = (ttsManager.speakerCount(paths) - 1).coerceAtLeast(0)
                val speakerId = resolveSpeakerIdForModel(selected.id, maxSpeaker)
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
                val maxSpeaker = (ttsManager.speakerCount(paths) - 1).coerceAtLeast(0)
                val speakerId = resolveSpeakerIdForModel(selected.id, maxSpeaker)
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
                val maxSpeaker = (ttsManager.speakerCount(paths) - 1).coerceAtLeast(0)
                val speakerId = resolveSpeakerIdForModel(selected.id, maxSpeaker)
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
        // If maxSpeakerId is still 0 the TTS speaker count hasn't loaded yet
        // (or this is a genuine single-speaker model). Either way, a coerceIn(0,0)
        // would wrongly overwrite a restored non-zero speaker ID, so skip it.
        if (max == 0) return parsed
        val clamped = parsed.coerceIn(0, max)
        if (clamped.toString() != _uiState.value.speakerIdInput) {
            _uiState.update { it.copy(speakerIdInput = clamped.toString()) }
        }
        persistCurrentSpeakerId(clamped)
        return clamped
    }

    private fun resolveSpeakerIdForModel(modelId: String, maxSpeakerId: Int): Int {
        val parsed = _uiState.value.speakerIdInput.toIntOrNull() ?: 0
        val clamped = parsed.coerceIn(0, maxSpeakerId)
        _uiState.update {
            it.copy(maxSpeakerId = maxSpeakerId, speakerIdInput = clamped.toString())
        }
        modelRepository.saveLastSpeakerId(modelId, clamped)
        return clamped
    }

    private fun persistCurrentSpeakerId(speakerId: Int? = null) {
        val modelId = modelRepository.selectedModelId() ?: return
        val id = speakerId ?: (_uiState.value.speakerIdInput.toIntOrNull() ?: 0)
        modelRepository.saveLastSpeakerId(modelId, id)
    }

    private fun refreshSpeakerInfo(model: LanguageModel) {
        viewModelScope.launch {
            runCatching {
                val paths = modelRepository.getInstalledModelPaths(model) ?: return@runCatching null
                ttsManager.availableSpeakerCount(paths)
            }.onSuccess { countOrNull ->
                if (countOrNull != null) {
                    val maxSpeaker = (countOrNull - 1).coerceAtLeast(0)
                    if (maxSpeaker > 0) {
                        // Reliable multi-speaker count: restore persisted ID clamped to valid range.
                        val saved = modelRepository.loadLastSpeakerId(model.id)
                        val restored = if (isCoquiModel(model.id)) 0 else saved.coerceIn(0, maxSpeaker)
                        _uiState.update { state ->
                            state.copy(maxSpeakerId = maxSpeaker, speakerIdInput = restored.toString())
                        }
                    } else {
                        // Single-speaker or TTS reported an unexpected 0/1 count.
                        // Only update maxSpeakerId; preserve whatever speakerIdInput
                        // was already set (e.g. eagerly restored from prefs).
                        _uiState.update { state ->
                            state.copy(maxSpeakerId = maxSpeaker)
                        }
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

        val installedFamilyIds = items
            .filter { it.state == DownloadState.INSTALLED }
            .map { languageFamilyId(it.model.languageCode) }
            .toSet()

        val filters = listOf(LanguageFilterUi(id = "all", label = "All")) +
            items
                .map { it.model }
                .groupBy { languageFamilyId(it.languageCode) }
                .map { (familyId, modelsForFamily) ->
                    val familyLabel = languageFamilyLabel(modelsForFamily.first().languageCode)
                    LanguageFilterUi(id = familyId, label = familyLabel)
                }
                .sortedWith(
                    compareByDescending<LanguageFilterUi> { it.id in installedFamilyIds }
                        .thenBy { it.label }
                )

        val filterIds = filters.map { it.id }.toSet()
        val selectedFilterId = _uiState.value.selectedLanguageFilterId
            .takeIf { it in filterIds }
            ?: "all"

        // Eagerly restore the persisted speaker ID so it is visible immediately on
        // startup, before the async TTS initialization in refreshSpeakerInfo completes.
        val restoredSpeakerId = selected?.let {
            if (isCoquiModel(it)) 0 else modelRepository.loadLastSpeakerId(it)
        } ?: 0

        _uiState.update {
            it.copy(
                models = items,
                selectedModelId = selected,
                languageFilters = filters,
                selectedLanguageFilterId = selectedFilterId,
                speakerIdInput = restoredSpeakerId.toString()
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

    private fun languageFamilyId(languageCode: String): String {
        val locale = Locale.forLanguageTag(languageCode)
        return locale.language.ifBlank { languageCode.lowercase() }
    }

    private fun languageFamilyLabel(languageCode: String): String {
        val locale = Locale.forLanguageTag(languageCode)
        val language = locale.getDisplayLanguage(Locale.ENGLISH)
        return language.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
    }

    private fun isCoquiModel(modelId: String): Boolean {
        return modelId.contains("coqui", ignoreCase = true)
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
