package com.krion.tts.ui

import com.krion.tts.domain.DownloadState
import com.krion.tts.domain.LanguageModel

enum class KrionPage {
    MAIN,
    MODELS
}

data class ModelItemUi(
    val model: LanguageModel,
    val state: DownloadState,
    val isSelected: Boolean,
    val downloadProgress: Int = 0
)

data class KrionUiState(
    val currentPage: KrionPage = KrionPage.MAIN,
    val inputText: String = "Welcome to KrionTTS. This is an offline text to speech demo.",
    val speakerIdInput: String = "0",
    val maxSpeakerId: Int = 0,
    val languageFilters: List<String> = listOf("All"),
    val selectedLanguageFilter: String = "All",
    val models: List<ModelItemUi> = emptyList(),
    val selectedModelId: String? = null,
    val isBusy: Boolean = false,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val statusMessage: String = "Select a language model and generate audio."
)
