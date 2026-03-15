package com.krion.tts.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import com.krion.tts.R
import com.krion.tts.domain.DownloadState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KrionScreen(viewModel: KrionViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isDarkTheme = when (uiState.themeOption) {
        ThemeOption.SYSTEM -> isSystemInDarkTheme()
        ThemeOption.LIGHT -> false
        ThemeOption.DARK -> true
    }
    val appColorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()

    LaunchedEffect(uiState.autoRestartRequested) {
        if (uiState.autoRestartRequested) {
            restartApp(context)
        }
    }

    BackHandler(enabled = uiState.currentPage != KrionPage.MAIN) {
        viewModel.closeSubPage()
    }

    MaterialTheme(colorScheme = appColorScheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        when (uiState.currentPage) {
                            KrionPage.MODELS -> Text("Models")
                            KrionPage.SETTINGS -> Text("Settings")
                            KrionPage.MAIN -> Image(
                                painter = painterResource(id = R.drawable.header_logo),
                                contentDescription = "KrionTTS logo",
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    },
                    navigationIcon = {
                        if (uiState.currentPage != KrionPage.MAIN) {
                            IconButton(onClick = viewModel::closeSubPage) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (uiState.currentPage == KrionPage.MAIN) {
                            IconButton(onClick = viewModel::openModelsPage) {
                                Icon(Icons.Rounded.Download, contentDescription = "Download models")
                            }
                        }

                        IconButton(onClick = viewModel::openSettingsPage) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Theme settings")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (uiState.currentPage) {
                    KrionPage.MAIN -> MainPage(uiState = uiState, viewModel = viewModel)
                    KrionPage.MODELS -> ModelsPage(uiState = uiState, viewModel = viewModel)
                    KrionPage.SETTINGS -> SettingsPage(uiState = uiState, viewModel = viewModel)
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.isBusy) {
                        CircularProgressIndicator()
                    }
                    Text(
                        text = "Made with Love, by Zenith",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

            }
        }
    }
}

private fun restartApp(context: android.content.Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

    if (intent != null) {
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}

@Composable
private fun MainPage(uiState: KrionUiState, viewModel: KrionViewModel) {
    val currentSpeakerId = uiState.speakerIdInput.toIntOrNull()?.coerceIn(0, uiState.maxSpeakerId) ?: 0
    val selectedModel = uiState.models.firstOrNull { it.isSelected }

    val wavSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportToUri(it, isMp3 = false) }
    }

    val mp3SaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/mpeg")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportToUri(it, isMp3 = true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Offline Text to Speech",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            Text(
                text = selectedModel?.let { "Selected model: ${it.model.displayName}" } ?: "No model selected",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            OutlinedTextField(
                value = uiState.inputText,
                onValueChange = viewModel::onTextChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Input text") },
                minLines = 4
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = viewModel::decrementSpeakerId,
                    enabled = !uiState.isBusy && currentSpeakerId > 0
                ) {
                    Text("-")
                }

                // Track whether the field was ever focused so the initial
                // onFocusChanged(isFocused=false) at first composition does not
                // trigger a clamp before the TTS speaker count is known.
                var speakerFieldWasFocused by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = uiState.speakerIdInput,
                    onValueChange = viewModel::onSpeakerIdChanged,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                speakerFieldWasFocused = true
                            } else if (speakerFieldWasFocused) {
                                viewModel.clampSpeakerIdInput()
                            }
                        },
                    label = { Text("Speaker ID") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Button(
                    onClick = viewModel::incrementSpeakerId,
                    enabled = !uiState.isBusy && currentSpeakerId < uiState.maxSpeakerId
                ) {
                    Text("+")
                }
            }
        }

        item {
            Text(
                text = "Available speaker range for selected model: 0-${uiState.maxSpeakerId}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        when {
                            uiState.isPaused -> viewModel.resume()
                            uiState.isPlaying -> viewModel.pause()
                            else -> viewModel.play()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isBusy
                ) {
                    Icon(
                        when {
                            uiState.isPaused -> Icons.Rounded.PlayArrow
                            uiState.isPlaying -> Icons.Rounded.Pause
                            else -> Icons.Rounded.PlayArrow
                        },
                        contentDescription = null
                    )
                }
                Button(
                    onClick = viewModel::stop,
                    modifier = Modifier.weight(1f),
                    enabled = uiState.isPlaying || uiState.isPaused
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val timestamp = System.currentTimeMillis()
                        val languageCode = selectedModel?.model?.languageCode ?: "audio"
                        wavSaveLauncher.launch("krion_${languageCode}_$timestamp.wav")
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isBusy
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Text(" WAV")
                }
                Button(
                    onClick = {
                        val timestamp = System.currentTimeMillis()
                        val languageCode = selectedModel?.model?.languageCode ?: "audio"
                        mp3SaveLauncher.launch("krion_${languageCode}_$timestamp.mp3")
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isBusy
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Text(" MP3")
                }
            }
        }

        item {
            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ModelsPage(uiState: KrionUiState, viewModel: KrionViewModel) {
    val downloadedModels = uiState.models.filter { it.state == DownloadState.INSTALLED }
    val filteredAvailableModels = uiState.models.filter { item ->
        (item.state != DownloadState.INSTALLED) &&
        (uiState.selectedLanguageFilterId == "all" || languageFamilyId(item.model.languageCode) == uiState.selectedLanguageFilterId)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Language Filter",
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.languageFilters) { filter ->
                    FilterChip(
                        selected = uiState.selectedLanguageFilterId == filter.id,
                        onClick = { viewModel.selectLanguageFilter(filter.id) },
                        label = { Text(filter.label) }
                    )
                }
            }
        }

        item {
            Text(
                text = "Downloaded Models",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (downloadedModels.isEmpty()) {
            item {
                Text(
                    text = "No downloaded models yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(downloadedModels, key = { it.model.id }) { item ->
                ModelCard(
                    item = item,
                    onDownload = { viewModel.downloadModel(item.model.id) },
                    onSelect = { viewModel.selectModel(item.model.id) },
                    onDelete = { viewModel.deleteModel(item.model.id) }
                )
            }
        }

        item {
            Text(
                text = "Available Models",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        items(filteredAvailableModels, key = { it.model.id }) { item ->
            ModelCard(
                item = item,
                onDownload = { viewModel.downloadModel(item.model.id) },
                onSelect = { viewModel.selectModel(item.model.id) },
                onDelete = { viewModel.deleteModel(item.model.id) }
            )
        }
    }
}

@Composable
private fun SettingsPage(uiState: KrionUiState, viewModel: KrionViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Choose how KrionTTS looks across the app.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Theme mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeOption.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeOption.entries.size
                                ),
                                onClick = { viewModel.setThemeOption(option) },
                                selected = uiState.themeOption == option,
                                label = {
                                    Text(
                                        text = when (option) {
                                            ThemeOption.SYSTEM -> "System"
                                            ThemeOption.LIGHT -> "Light"
                                            ThemeOption.DARK -> "Dark"
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            {
                Text(
                    text = "Active: ${themeOptionLabel(uiState.themeOption)}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun themeOptionLabel(themeOption: ThemeOption): String {
    return when (themeOption) {
        ThemeOption.SYSTEM -> "System default"
        ThemeOption.LIGHT -> "Light"
        ThemeOption.DARK -> "Dark"
    }
}

private fun languageFamilyId(languageCode: String): String {
    val locale = Locale.forLanguageTag(languageCode)
    return locale.language.ifBlank { languageCode.lowercase() }
}

@Composable
private fun ModelCard(
    item: ModelItemUi,
    onDownload: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${item.model.displayName} • ${item.model.modelName}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = item.model.description,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "License: ${item.model.licenseUrl}",
                style = MaterialTheme.typography.bodySmall
            )

            if (item.state == DownloadState.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { item.downloadProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Downloading: ${item.downloadProgress}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onDownload,
                    enabled = item.state != DownloadState.DOWNLOADING,
                    label = { Text(if (item.state == DownloadState.INSTALLED) "Reinstall" else "Download") },
                    leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) }
                )
                AssistChip(
                    onClick = onSelect,
                    enabled = item.state == DownloadState.INSTALLED,
                    label = { Text(if (item.isSelected) "Selected" else "Select") }
                )
                if (item.state == DownloadState.INSTALLED) {
                    AssistChip(
                        onClick = onDelete,
                        label = { Text("Delete") }
                    )
                }
            }
        }
    }
}
