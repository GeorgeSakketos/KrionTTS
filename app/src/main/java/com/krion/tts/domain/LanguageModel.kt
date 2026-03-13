package com.krion.tts.domain

data class LanguageModel(
    val id: String,
    val languageCode: String,
    val displayName: String,
    val modelName: String,
    val description: String,
    val archiveUrl: String,
    val licenseUrl: String
)

enum class DownloadState {
    NOT_INSTALLED,
    DOWNLOADING,
    INSTALLED,
    FAILED
}
