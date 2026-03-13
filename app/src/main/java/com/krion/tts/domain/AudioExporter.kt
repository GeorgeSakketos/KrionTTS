package com.krion.tts.domain

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.util.Log
import android.os.Environment
import android.provider.MediaStore
import com.naman14.androidlame.AndroidLame
import com.naman14.androidlame.LameBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class AudioExporter(private val context: Context) {

    suspend fun exportWav(sourceWav: File, fileName: String): Uri = withContext(Dispatchers.IO) {
        writeToDownloads(
            sourceFile = sourceWav,
            displayName = ensureExtension(fileName, ".wav"),
            mimeType = "audio/wav"
        )
    }

    suspend fun exportMp3(sourceWav: File, fileName: String): Uri = withContext(Dispatchers.IO) {
        val mp3Temp = File(context.cacheDir, "${sourceWav.nameWithoutExtension}.mp3")
        mp3Temp.delete()

        convertWavToMp3(sourceWav, mp3Temp)

        if (!mp3Temp.exists() || mp3Temp.length() == 0L) {
            throw IllegalStateException("MP3 file was not created or is empty")
        }

        writeToDownloads(
            sourceFile = mp3Temp,
            displayName = ensureExtension(fileName, ".mp3"),
            mimeType = "audio/mpeg"
        )
    }

    suspend fun exportToUri(sourceWav: File, destinationUri: Uri, isMp3: Boolean): Uri = withContext(Dispatchers.IO) {
        val sourceFile = if (isMp3) {
            val mp3Temp = File(context.cacheDir, "${sourceWav.nameWithoutExtension}.mp3")
            mp3Temp.delete()

            convertWavToMp3(sourceWav, mp3Temp)

            if (!mp3Temp.exists() || mp3Temp.length() == 0L) {
                throw IllegalStateException("MP3 file was not created or is empty")
            }

            mp3Temp
        } else {
            sourceWav
        }

        context.contentResolver.openOutputStream(destinationUri)?.use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to open destination stream")

        destinationUri
    }

    private fun convertWavToMp3(sourceWav: File, destinationMp3: File) {
        Log.d("AudioExporter", "MP3 encoding using TAndroidLame: $sourceWav -> $destinationMp3")
        
        try {
            val wavBytes = sourceWav.readBytes()
            val wavInfo = parseWav(wavBytes)
            
            Log.d("AudioExporter", "WAV info: channels=${wavInfo.channels}, sampleRate=${wavInfo.sampleRate}, dataSize=${wavInfo.dataSize}")
            
            // Extract PCM data
            val pcmBytes = wavBytes.copyOfRange(wavInfo.dataStart, wavInfo.dataStart + wavInfo.dataSize)
            val pcmShorts = ShortArray(pcmBytes.size / 2)
            var pcmIndex = 0
            var shortIndex = 0
            while (pcmIndex + 1 < pcmBytes.size) {
                val low = pcmBytes[pcmIndex].toInt() and 0xFF
                val high = pcmBytes[pcmIndex + 1].toInt()
                pcmShorts[shortIndex] = ((high shl 8) or low).toShort()
                pcmIndex += 2
                shortIndex += 1
            }
            
            Log.d("AudioExporter", "Extracted ${pcmShorts.size} PCM samples")
            
            // Build LAME encoder
            val androidLame = LameBuilder()
                .setInSampleRate(wavInfo.sampleRate)
                .setOutChannels(wavInfo.channels)
                .setOutBitrate(128)
                .setOutSampleRate(wavInfo.sampleRate)
                .setQuality(5) // 0=best, 9=fastest
                .build()
            
            val mp3Buf = ByteArray((7200 + pcmShorts.size * 2 * 1.25).toInt())
            val bytesEncoded = androidLame.encode(pcmShorts, pcmShorts, pcmShorts.size, mp3Buf)
            
            Log.d("AudioExporter", "Encoded $bytesEncoded bytes")
            
            val flushBuf = ByteArray(7200)
            val flushedBytes = androidLame.flush(flushBuf)
            
            Log.d("AudioExporter", "Flushed $flushedBytes bytes")
            
            androidLame.close()
            
            // Write MP3 data
            val mp3Output = ByteArrayOutputStream()
            mp3Output.write(mp3Buf, 0, bytesEncoded)
            if (flushedBytes > 0) {
                mp3Output.write(flushBuf, 0, flushedBytes)
            }
            
            destinationMp3.writeBytes(mp3Output.toByteArray())
            Log.d("AudioExporter", "MP3 encoding complete. Output size: ${destinationMp3.length()} bytes")
            
        } catch (e: Exception) {
            Log.e("AudioExporter", "MP3 conversion failed: ${e.message}", e)
            throw e
        }
    }
    
    private fun parseWav(bytes: ByteArray): WavInfo {
        if (bytes.size < 44) {
            throw IllegalStateException("Invalid WAV file: file too small")
        }

        if (readAscii(bytes, 0, 4) != "RIFF" || readAscii(bytes, 8, 4) != "WAVE") {
            throw IllegalStateException("Invalid WAV file: missing RIFF/WAVE header")
        }

        var offset = 12
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var dataStart = -1
        var dataSize = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = readAscii(bytes, offset, 4)
            val chunkSize = readLeInt(bytes, offset + 4)
            val chunkDataStart = offset + 8
            val chunkDataEnd = chunkDataStart + chunkSize

            if (chunkDataEnd > bytes.size) {
                throw IllegalStateException("Invalid WAV file: truncated $chunkId chunk")
            }

            when (chunkId) {
                "fmt " -> {
                    val audioFormat = readLeShort(bytes, chunkDataStart).toInt() and 0xFFFF
                    channels = readLeShort(bytes, chunkDataStart + 2).toInt() and 0xFFFF
                    sampleRate = readLeInt(bytes, chunkDataStart + 4)
                    bitsPerSample = readLeShort(bytes, chunkDataStart + 14).toInt() and 0xFFFF

                    if (audioFormat != 1) {
                        throw IllegalStateException("Unsupported WAV format: only PCM is supported")
                    }
                }

                "data" -> {
                    dataStart = chunkDataStart
                    dataSize = chunkSize
                }
            }

            offset = chunkDataEnd + (chunkSize and 1)
        }

        if (channels <= 0 || sampleRate <= 0 || bitsPerSample != 16 || dataStart < 0 || dataSize <= 0) {
            throw IllegalStateException("Unsupported WAV file for MP3 conversion")
        }

        return WavInfo(channels, sampleRate, dataStart, dataSize)
    }

    private fun readAscii(bytes: ByteArray, start: Int, length: Int): String {
        return bytes.copyOfRange(start, start + length).decodeToString()
    }

    private fun readLeInt(bytes: ByteArray, start: Int): Int {
        return (bytes[start].toInt() and 0xFF) or
            ((bytes[start + 1].toInt() and 0xFF) shl 8) or
            ((bytes[start + 2].toInt() and 0xFF) shl 16) or
            ((bytes[start + 3].toInt() and 0xFF) shl 24)
    }

    private fun readLeShort(bytes: ByteArray, start: Int): Short {
        return (((bytes[start + 1].toInt()) shl 8) or (bytes[start].toInt() and 0xFF)).toShort()
    }

    private data class WavInfo(
        val channels: Int,
        val sampleRate: Int,
        val dataStart: Int,
        val dataSize: Int
    )

    private fun ensureExtension(name: String, extension: String): String {
        return if (name.endsWith(extension, ignoreCase = true)) name else "$name$extension"
    }

    private fun writeToDownloads(sourceFile: File, displayName: String, mimeType: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val itemUri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Unable to create destination file")

        resolver.openOutputStream(itemUri).use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output ?: throw IllegalStateException("Unable to open destination stream"))
            }
        }

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(itemUri, values, null, null)

        return itemUri
    }
}
