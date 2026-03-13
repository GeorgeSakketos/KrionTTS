package com.krion.tts.domain

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

enum class PlaybackEndReason {
    COMPLETED,
    STOPPED
}

class OfflineTtsManager(context: Context) {

    private val appContext = context.applicationContext
    private var offlineTts: OfflineTts? = null
    private var currentModelId: String? = null
    private val playbackLock = Any()
    private var mediaPlayer: MediaPlayer? = null
    private var playbackResult: CompletableDeferred<PlaybackEndReason>? = null

    companion object {
        private const val TAG = "OfflineTtsManager"
        private const val PLAYBACK_TIMEOUT_MS = 300000L // 5 minutes max
    }

    suspend fun initialize(model: LanguageModel, installed: InstalledModelPaths) = withContext(Dispatchers.IO) {
        if (offlineTts != null && currentModelId == model.id) {
            return@withContext
        }

        offlineTts?.release()

        val vits = OfflineTtsVitsModelConfig(
            model = installed.modelFile.absolutePath,
            tokens = installed.tokensFile.absolutePath,
            dataDir = installed.dataDir?.absolutePath ?: ""
        )

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = vits,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
        )

        offlineTts = OfflineTts(config = config)
        currentModelId = model.id
    }

    fun speakerCount(installed: InstalledModelPaths? = null): Int {
        val runtimeCount = offlineTts?.numSpeakers() ?: 1
        val normalizedRuntimeCount = if (runtimeCount <= 0) 1 else runtimeCount

        if (normalizedRuntimeCount > 1) {
            return normalizedRuntimeCount
        }

        val metadataCount = speakerCountFromMetadata(installed?.metadataFile)
        return metadataCount?.takeIf { it > 0 } ?: normalizedRuntimeCount
    }

    fun availableSpeakerCount(installed: InstalledModelPaths?): Int {
        return speakerCountFromMetadata(installed?.metadataFile)?.takeIf { it > 0 } ?: 1
    }

    suspend fun speak(text: String, speakerId: Int): PlaybackEndReason = withContext(Dispatchers.IO) {
        Log.d(TAG, "speak() called - generating audio")
        val tts = offlineTts ?: throw IllegalStateException("TTS model is not initialized")
        val output = File(appContext.cacheDir, "krion_play_${System.currentTimeMillis()}.wav")
        val audio = tts.generate(text = text, sid = speakerId, speed = 1.0f)
        val saved = audio.save(output.absolutePath)
        if (!saved) {
            throw IllegalStateException("Failed to generate audio for playback")
        }
        Log.d(TAG, "Audio generated, starting playback")

        try {
            playWavBlocking(output)
        } finally {
            output.delete()
            Log.d(TAG, "speak() completed, temp file deleted")
        }
    }

    suspend fun synthesizeToWav(text: String, speakerId: Int): File = withContext(Dispatchers.IO) {
        val tts = offlineTts ?: throw IllegalStateException("TTS model is not initialized")
        val output = File(appContext.cacheDir, "krion_${System.currentTimeMillis()}.wav")

        val audio = tts.generate(text = text, sid = speakerId, speed = 1.0f)
        val saved = audio.save(output.absolutePath)
        if (!saved) {
            throw IllegalStateException("Failed to generate audio file")
        }

        output
    }

    private suspend fun playWavBlocking(wavFile: File): PlaybackEndReason {
        return withContext(Dispatchers.Main) {
            Log.d(TAG, "playWavBlocking() starting on main thread")
            stop()
            val result = CompletableDeferred<PlaybackEndReason>()
            val player = MediaPlayer()

            synchronized(playbackLock) {
                playbackResult = result
                mediaPlayer = player
            }

            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )

            player.setOnPreparedListener {
                Log.d(TAG, "MediaPlayer prepared, starting playback")
                runCatching { it.start() }.onFailure { error ->
                    Log.e(TAG, "Failed to start playback", error)
                    releasePlayer(player)
                    failPlayback(error)
                }
            }

            player.setOnCompletionListener {
                Log.d(TAG, "MediaPlayer onCompletion fired!")
                releasePlayer(player)
                completePlayback(PlaybackEndReason.COMPLETED)
            }

            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                releasePlayer(player)
                failPlayback(IllegalStateException("Playback error ($what/$extra)"))
                true
            }

            runCatching {
                player.setDataSource(wavFile.absolutePath)
                Log.d(TAG, "Data source set, calling prepareAsync()")
                player.prepareAsync()
            }.onFailure {
                Log.e(TAG, "Failed to prepare player", it)
                releasePlayer(player)
                failPlayback(it)
            }

            val finalResult = try {
                Log.d(TAG, "Waiting for playback result with timeout")
                withTimeoutOrNull(PLAYBACK_TIMEOUT_MS) {
                    result.await()
                } ?: run {
                    Log.e(TAG, "Playback timed out after ${PLAYBACK_TIMEOUT_MS}ms!")
                    stop()
                    PlaybackEndReason.COMPLETED
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Exception while waiting for playback", e)
                stop()
                PlaybackEndReason.STOPPED
            }
            
            Log.d(TAG, "playWavBlocking() returning: $finalResult")
            finalResult
        }
    }

    fun pause() {
        synchronized(playbackLock) {
            mediaPlayer?.pause()
        }
    }

    fun resume() {
        synchronized(playbackLock) {
            mediaPlayer?.start()
        }
    }

    fun stop() {
        val playerToStop = synchronized(playbackLock) {
            val p = mediaPlayer
            mediaPlayer = null
            p
        }

        playerToStop?.let { releasePlayer(it) }
        completePlayback(PlaybackEndReason.STOPPED)
    }

    private fun releasePlayer(player: MediaPlayer) {
        runCatching { player.stop() }
        runCatching { player.release() }
    }

    private fun completePlayback(reason: PlaybackEndReason) {
        Log.d(TAG, "completePlayback() called with reason: $reason")
        synchronized(playbackLock) {
            val completed = playbackResult?.complete(reason) ?: false
            Log.d(TAG, "Playback result completed: $completed")
            playbackResult = null
        }
    }

    private fun failPlayback(error: Throwable) {
        synchronized(playbackLock) {
            playbackResult?.completeExceptionally(error)
            playbackResult = null
        }
    }

    fun shutdown() {
        stop()
        offlineTts?.release()
        offlineTts = null
        currentModelId = null
    }

    private fun speakerCountFromMetadata(metadataFile: File?): Int? {
        if (metadataFile == null || !metadataFile.exists()) {
            return null
        }

        val text = runCatching { metadataFile.readText() }.getOrNull() ?: return null
        val regex = Regex("\"(num_speakers|n_speakers)\"\\s*:\\s*(\\d+)")
        val count = regex.find(text)?.groupValues?.getOrNull(2)?.toIntOrNull()
        return count?.takeIf { it > 0 }
    }
}
