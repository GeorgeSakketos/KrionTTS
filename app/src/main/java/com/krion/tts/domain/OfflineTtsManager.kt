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
    private val engineLock = Any()
    private var offlineTts: OfflineTts? = null
    private var currentModelId: String? = null
    private val playbackLock = Any()
    private var mediaPlayer: MediaPlayer? = null
    private var playbackResult: CompletableDeferred<PlaybackEndReason>? = null

    companion object {
        private const val TAG = "OfflineTtsManager"
        private const val PLAYBACK_TIMEOUT_MS = 300000L // 5 minutes max
        private const val MIN_WAV_BYTES = 1024L
        private const val WAV_HEADER_BYTES = 44
        private const val WAV_AUDIBLE_PEAK_THRESHOLD = 120
        private const val WAV_ANALYSIS_MAX_BYTES = 512 * 1024
    }

    suspend fun initialize(model: LanguageModel, installed: InstalledModelPaths) = withContext(Dispatchers.IO) {
        synchronized(engineLock) {
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
    }

    fun speakerCount(installed: InstalledModelPaths? = null): Int {
        val runtimeCount = synchronized(engineLock) { offlineTts?.numSpeakers() ?: 1 }
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
        val output = File(appContext.cacheDir, "krion_play_${System.currentTimeMillis()}.wav")
        val saved = synchronized(engineLock) {
            val tts = offlineTts ?: throw IllegalStateException("TTS model is not initialized")
            generateToFile(tts, text, speakerId, output)
        }
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
        val output = File(appContext.cacheDir, "krion_${System.currentTimeMillis()}.wav")
        val saved = synchronized(engineLock) {
            val tts = offlineTts ?: throw IllegalStateException("TTS model is not initialized")
            generateToFile(tts, text, speakerId, output)
        }
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
                    failPlaybackForPlayer(player, error)
                }
            }

            player.setOnCompletionListener {
                Log.d(TAG, "MediaPlayer onCompletion fired!")
                releasePlayer(player)
                completePlaybackForPlayer(player, PlaybackEndReason.COMPLETED)
            }

            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                releasePlayer(player)
                failPlaybackForPlayer(player, IllegalStateException("Playback error ($what/$extra)"))
                true
            }

            runCatching {
                player.setDataSource(wavFile.absolutePath)
                Log.d(TAG, "Data source set, calling prepareAsync()")
                player.prepareAsync()
            }.onFailure {
                Log.e(TAG, "Failed to prepare player", it)
                releasePlayer(player)
                failPlaybackForPlayer(player, it)
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

    private fun completePlaybackForPlayer(player: MediaPlayer, reason: PlaybackEndReason) {
        synchronized(playbackLock) {
            if (mediaPlayer !== player) {
                Log.d(TAG, "Ignoring completion from stale MediaPlayer instance")
                return
            }
            mediaPlayer = null
            val completed = playbackResult?.complete(reason) ?: false
            Log.d(TAG, "Playback result completed (current player): $completed")
            playbackResult = null
        }
    }

    private fun failPlayback(error: Throwable) {
        synchronized(playbackLock) {
            playbackResult?.completeExceptionally(error)
            playbackResult = null
        }
    }

    private fun failPlaybackForPlayer(player: MediaPlayer, error: Throwable) {
        synchronized(playbackLock) {
            if (mediaPlayer !== player) {
                Log.d(TAG, "Ignoring failure from stale MediaPlayer instance")
                return
            }
            mediaPlayer = null
            playbackResult?.completeExceptionally(error)
            playbackResult = null
        }
    }

    fun shutdown() {
        stop()
        synchronized(engineLock) {
            offlineTts?.release()
            offlineTts = null
            currentModelId = null
        }
    }

    private fun speakerCountFromMetadata(metadataFile: File?): Int? {
        if (metadataFile == null || !metadataFile.exists()) {
            return null
        }

        val text = runCatching { metadataFile.readText() }.getOrNull() ?: return null
        val regex = Regex("\"(num_speakers|n_speakers|speaker_count|speakers)\"\\s*:\\s*(\\d+)")
        val counts = regex.findAll(text)
            .mapNotNull { it.groupValues.getOrNull(2)?.toIntOrNull() }
            .filter { it > 0 }
            .toList()
        return counts.maxOrNull()
    }

    private fun generateToFile(tts: OfflineTts, text: String, speakerId: Int, output: File): Boolean {
        val expectedMinBytes = (WAV_HEADER_BYTES + text.length * 200L).coerceAtLeast(MIN_WAV_BYTES)

        fun attempt(sid: Int): Boolean {
            val saved = tts.generate(text = text, sid = sid, speed = 1.0f).save(output.absolutePath)
            if (!saved) return false

            val size = output.length()
            if (size <= MIN_WAV_BYTES) {
                Log.w(TAG, "Generated wav too small with speakerId=$sid, size=$size")
                return false
            }

            val audible = isLikelyAudibleWav(output)
            if (!audible) {
                Log.w(TAG, "Generated wav appears silent with speakerId=$sid, size=$size")
                return false
            }

            if (size < expectedMinBytes) {
                Log.w(
                    TAG,
                    "Generated wav is shorter than expected but audible with speakerId=$sid, size=$size, expectedMin=$expectedMinBytes"
                )
            } else {
                Log.d(TAG, "Generated wav check speakerId=$sid size=$size audible=$audible")
            }
            return true
        }

        if (attempt(speakerId)) {
            return true
        }

        Log.w(TAG, "Retrying generation with same speakerId=$speakerId (warm-up retry)")
        if (attempt(speakerId)) {
            return true
        }

        if (speakerId != 0) {
            Log.w(TAG, "Retrying generation with fallback speakerId=0")
            if (attempt(0)) {
                return true
            }
        }

        if (speakerId != 1) {
            Log.w(TAG, "Retrying generation with fallback speakerId=1")
            if (attempt(1)) {
                return true
            }
        }

        return false
    }

    private fun isLikelyAudibleWav(file: File): Boolean {
        if (!file.exists() || file.length() <= WAV_HEADER_BYTES) {
            return false
        }

        val maxRead = minOf(WAV_ANALYSIS_MAX_BYTES.toLong(), file.length() - WAV_HEADER_BYTES).toInt()
        if (maxRead <= 1) {
            return false
        }

        val buffer = ByteArray(maxRead)
        val read = file.inputStream().use { input ->
            val skipped = input.skip(WAV_HEADER_BYTES.toLong())
            if (skipped < WAV_HEADER_BYTES) return false
            input.read(buffer)
        }
        if (read <= 1) {
            return false
        }

        var peak = 0
        var index = 0
        while (index + 1 < read) {
            val lo = buffer[index].toInt() and 0xFF
            val hi = buffer[index + 1].toInt()
            val sample = (hi shl 8) or lo
            val abs = kotlin.math.abs(sample.toShort().toInt())
            if (abs > peak) {
                peak = abs
                if (peak >= WAV_AUDIBLE_PEAK_THRESHOLD) {
                    return true
                }
            }
            index += 2
        }

        Log.w(TAG, "Generated wav appears near-silent (peak=$peak)")
        return false
    }
}
