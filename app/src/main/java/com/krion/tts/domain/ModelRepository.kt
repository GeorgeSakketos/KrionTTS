package com.krion.tts.domain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File

class ModelRepository(private val context: Context) {

    private val client = OkHttpClient()
    private val prefs = context.getSharedPreferences("krion_tts", Context.MODE_PRIVATE)
    private val modelsDir = File(context.filesDir, "models")

    init {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
    }

    fun selectedModelId(): String? = prefs.getString(KEY_SELECTED_MODEL, null)

    fun setSelectedModelId(modelId: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL, modelId).apply()
    }

    fun isInstalled(model: LanguageModel): Boolean {
        return getInstalledModelPaths(model) != null
    }

    fun getInstalledModelPaths(model: LanguageModel): InstalledModelPaths? {
        val dir = File(modelsDir, model.id)
        val modelFile = File(dir, "model.onnx").takeIf { it.exists() }
            ?: findModelFile(dir)
        val tokensFile = File(dir, "tokens.txt").takeIf { it.exists() }
            ?: findTokensFile(dir)
        val dataDir = File(dir, "espeak-ng-data").takeIf { it.exists() }
            ?: findDataDir(dir)

        if (modelFile == null || tokensFile == null) {
            return null
        }

        val metadataFile = File(dir, "model.onnx.json").takeIf { it.exists() }
            ?: findMetadataFile(dir, modelFile)

        return InstalledModelPaths(
            modelFile = modelFile,
            tokensFile = tokensFile,
            dataDir = dataDir,
            metadataFile = metadataFile
        )
    }

    suspend fun downloadModel(
        model: LanguageModel,
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        onProgress(0)
        removeOtherModelsForLanguage(model.languageCode, exceptId = model.id)

        val targetDir = File(modelsDir, model.id)
        targetDir.deleteRecursively()
        targetDir.mkdirs()

        val archiveFile = File(context.cacheDir, "${model.id}.tar.bz2")
        downloadFile(model.archiveUrl, archiveFile, onProgress)
        extractModelArchive(archiveFile, targetDir)
        normalizeModelLayout(targetDir)

        if (!isInstalled(model)) {
            throw IllegalStateException("Model package is missing required files")
        }

        onProgress(100)
    }

    fun deleteModel(model: LanguageModel) {
        val modelDir = File(modelsDir, model.id)
        if (modelDir.exists()) {
            modelDir.deleteRecursively()
        }
        prefs.edit().remove(speakerKey(model.id)).apply()
        // Clear selected model if it was the deleted one
        if (selectedModelId() == model.id) {
            prefs.edit().remove(KEY_SELECTED_MODEL).apply()
        }
    }

    private fun removeOtherModelsForLanguage(languageCode: String, exceptId: String) {
        modelsDir.listFiles()
            ?.filter { it.isDirectory && it.name != exceptId }
            ?.forEach { directory ->
                val model = ModelCatalog.models.firstOrNull { it.id == directory.name }
                if (model?.languageCode == languageCode) {
                    if (selectedModelId() == directory.name) {
                        prefs.edit().remove(KEY_SELECTED_MODEL).apply()
                    }
                    prefs.edit().remove(speakerKey(directory.name)).apply()
                    directory.deleteRecursively()
                }
            }
    }

    private fun downloadFile(url: String, output: File, onProgress: (Int) -> Unit) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed: ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("Empty download body")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            var lastProgress = -1

            body.byteStream().use { input ->
                output.outputStream().use { fileOutput ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break

                        fileOutput.write(buffer, 0, read)
                        downloadedBytes += read

                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (progress != lastProgress) {
                                onProgress(progress)
                                lastProgress = progress
                            }
                        }
                    }
                }
            }
        }
    }

    private fun extractModelArchive(archive: File, targetDir: File) {
        val unpackTemp = File(context.cacheDir, "${targetDir.name}_unpack")
        unpackTemp.deleteRecursively()
        unpackTemp.mkdirs()

        TarArchiveInputStream(
            BZip2CompressorInputStream(
                BufferedInputStream(archive.inputStream())
            )
        ).use { tarInput ->
            var entry = tarInput.nextTarEntry
            while (entry != null) {
                val outFile = File(unpackTemp, entry.name)

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { output ->
                        tarInput.copyTo(output)
                    }
                }

                entry = tarInput.nextTarEntry
            }
        }

        val root = unpackTemp.listFiles()?.firstOrNull { it.isDirectory } ?: unpackTemp
        root.listFiles()?.forEach { child ->
            child.copyRecursively(File(targetDir, child.name), overwrite = true)
        }

        unpackTemp.deleteRecursively()
    }

    private fun normalizeModelLayout(targetDir: File) {
        val discoveredModel = findModelFile(targetDir) ?: return
        val discoveredTokens = findTokensFile(targetDir) ?: return

        val canonicalModel = File(targetDir, "model.onnx")
        if (!canonicalModel.exists()) {
            discoveredModel.copyTo(canonicalModel, overwrite = true)
        }

        val canonicalTokens = File(targetDir, "tokens.txt")
        if (!canonicalTokens.exists()) {
            discoveredTokens.copyTo(canonicalTokens, overwrite = true)
        }

        val discoveredMetadata = findMetadataFile(targetDir, discoveredModel)
        val canonicalMetadata = File(targetDir, "model.onnx.json")
        if (!canonicalMetadata.exists() && discoveredMetadata != null) {
            discoveredMetadata.copyTo(canonicalMetadata, overwrite = true)
        }

        val discoveredDataDir = findDataDir(targetDir)
        val canonicalDataDir = File(targetDir, "espeak-ng-data")
        if (!canonicalDataDir.exists() && discoveredDataDir != null && discoveredDataDir != canonicalDataDir) {
            discoveredDataDir.copyRecursively(canonicalDataDir, overwrite = true)
        }
    }

    private fun findModelFile(root: File): File? {
        return root.walkTopDown().firstOrNull { file ->
            file.isFile && file.extension == "onnx" && !file.name.endsWith(".onnx.json")
        }
    }

    private fun findTokensFile(root: File): File? {
        return root.walkTopDown().firstOrNull { file ->
            file.isFile && file.name == "tokens.txt"
        }
    }

    private fun findDataDir(root: File): File? {
        return root.walkTopDown().firstOrNull { file ->
            file.isDirectory && file.name == "espeak-ng-data"
        }
    }

    private fun findMetadataFile(root: File, modelFile: File): File? {
        val adjacent = File(modelFile.parentFile, "${modelFile.name}.json")
        if (adjacent.exists()) {
            return adjacent
        }

        val adjacentConfig = File(modelFile.parentFile, "config.json")
        if (adjacentConfig.exists()) {
            return adjacentConfig
        }

        return root.walkTopDown().firstOrNull { file ->
            file.isFile && (file.name.endsWith(".onnx.json") || file.name == "config.json")
        }
    }

    fun saveLastSpeakerId(modelId: String, speakerId: Int) {
        prefs.edit().putInt(speakerKey(modelId), speakerId).apply()
    }

    fun loadLastSpeakerId(modelId: String): Int {
        return prefs.getInt(speakerKey(modelId), 0)
    }

    fun shouldShowFirstLaunchModelNotice(): Boolean {
        return !prefs.getBoolean(KEY_FIRST_LAUNCH_MODEL_NOTICE_SHOWN, false)
    }

    fun markFirstLaunchModelNoticeShown() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH_MODEL_NOTICE_SHOWN, true).apply()
    }

    fun shouldShowLegalNotice(): Boolean {
        return !prefs.getBoolean(KEY_LEGAL_NOTICE_ACCEPTED, false)
    }

    fun markLegalNoticeAccepted() {
        prefs.edit().putBoolean(KEY_LEGAL_NOTICE_ACCEPTED, true).apply()
    }

    private fun speakerKey(modelId: String): String = "speaker_$modelId"

    companion object {
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_FIRST_LAUNCH_MODEL_NOTICE_SHOWN = "first_launch_model_notice_shown"
        private const val KEY_LEGAL_NOTICE_ACCEPTED = "legal_notice_accepted"
    }
}

data class InstalledModelPaths(
    val modelFile: File,
    val tokensFile: File,
    val dataDir: File?,
    val metadataFile: File?
)
