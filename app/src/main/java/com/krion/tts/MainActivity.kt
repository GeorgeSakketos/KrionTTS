package com.krion.tts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.krion.tts.domain.AudioExporter
import com.krion.tts.domain.ModelRepository
import com.krion.tts.domain.OfflineTtsManager
import com.krion.tts.ui.KrionScreen
import com.krion.tts.ui.KrionViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = ModelRepository(applicationContext)
        val ttsManager = OfflineTtsManager(applicationContext)
        val exporter = AudioExporter(applicationContext)

        setContent {
            val viewModel: KrionViewModel = viewModel(
                factory = KrionViewModel.Factory(repository, ttsManager, exporter)
            )
            KrionScreen(viewModel = viewModel)
        }
    }
}
