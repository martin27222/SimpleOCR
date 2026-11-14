// Derived from EasyOCR (https://github.com/MosesEllermann/EasyOCR), Apache License 2.0.
// Modified for SimpleOCR; see NOTICE and the git history for the changes.

package io.github.martin27222.simpleocr

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.martin27222.simpleocr.core.dewarp.DewarpChoice
import io.github.martin27222.simpleocr.core.merge.ReadLine
import io.github.martin27222.simpleocr.core.merge.TextMerge
import io.github.martin27222.simpleocr.core.text.TextFormatter
import io.github.martin27222.simpleocr.dewarp.Dewarper
import io.github.martin27222.simpleocr.dewarp.TextGuidedFlattener
import io.github.martin27222.simpleocr.dewarp.UvDocDewarper
import io.github.martin27222.simpleocr.image.BitmapEditor
import io.github.martin27222.simpleocr.image.ImageLoader
import io.github.martin27222.simpleocr.image.SaveImageUseCase
import io.github.martin27222.simpleocr.intents.ImageEditIntentHandler
import io.github.martin27222.simpleocr.ocr.MlKitOcrEngine
import io.github.martin27222.simpleocr.ocr.OcrEngine
import io.github.martin27222.simpleocr.ocr.OcrLanguage
import io.github.martin27222.simpleocr.ocr.OcrRepository
import io.github.martin27222.simpleocr.ocr.OcrResult
import io.github.martin27222.simpleocr.ocr.readLines
import io.github.martin27222.simpleocr.ocr.textToCopy
import io.github.martin27222.simpleocr.ocr.votedWith
import io.github.martin27222.simpleocr.settings.AppSettings
import io.github.martin27222.simpleocr.settings.SettingsStore
import io.github.martin27222.simpleocr.ui.CropPreset
import io.github.martin27222.simpleocr.ui.CropState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val intentHandler = ImageEditIntentHandler()
    private val imageLoader = ImageLoader(application.contentResolver)
    private val ocrEngine: OcrEngine = MlKitOcrEngine()
    private val ocrRepository = OcrRepository(ocrEngine)
    private val dewarper: Dewarper = UvDocDewarper(application)
    private val saveImageUseCase = SaveImageUseCase(application)
    private val settingsStore = SettingsStore(application)

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings

    /** One reading of the current photo: the image and the text found in it. */
    private data class Variant(val bitmap: Bitmap, val ocr: OcrResult)

    private var originalVariant: Variant? = null
    private var dewarpedVariant: Variant? = null
    private var photoJob: Job? = null

    /** The photos of one object taken in the mode for several photos, in the order taken. */
    private class MultiPhotoSession {
        val readings = ArrayList<List<ReadLine>?>()
        val jobs = ArrayList<Job>()
    }

    private var multiPhotoSession = MultiPhotoSession()

    /**
     * Lets only one photo be read at a time. Reading holds several full-size images in memory;
     * photos taken in quick succession would otherwise add up.
     */
    private val readingTurn = Mutex()

    fun updateSettings(settings: AppSettings) {
        _settings.value = settings
        settingsStore.save(settings)
    }

    fun openIntent(intent: Intent?) {
        val input = intentHandler.extract(intent) ?: return
        intentHandler.tryPersistReadPermission(getApplication<Application>().contentResolver, input)
        openUri(input.uri, openedExternally = true)
    }

    /** Shows a photo taken with the in-app camera. The bitmap only lives in memory. */
    fun openCapturedPhoto(bitmap: Bitmap, passes: Int) {
        forgetVariants()
        ocrRepository.clear()
        _uiState.update {
            it.copy(
                sourceUri = null,
                bitmap = bitmap,
                ocrResult = null,
                isLoadingImage = false,
                openedExternally = false,
                lastSavedUri = null,
                errorMessage = null,
            )
        }
        autoSave(bitmap)
        readPhoto(bitmap, passes)
    }

    fun setCaptureMode(mode: CaptureMode) {
        if (_uiState.value.captureMode == mode) return
        resetMultiPhoto()
        _uiState.update { it.copy(captureMode = mode) }
    }

    /** Reads one more photo of the object. The photo itself is not kept. */
    fun addMultiPhoto(bitmap: Bitmap, passes: Int) {
        val session = multiPhotoSession
        val index = session.readings.size
        session.readings.add(null)
        _uiState.update { it.copy(multiPhotoCount = session.readings.size) }
        autoSave(bitmap)
        val language = _uiState.value.language
        val flatten = _settings.value.autoDewarp && passes >= 2
        session.jobs += viewModelScope.launch {
            catching { readingTurn.withLock { readForMerging(bitmap, language, flatten) } }
                .onSuccess { session.readings[index] = it }
                .onFailure { throwable ->
                    _uiState.update { it.copy(errorMessage = throwable.message ?: "OCR failed") }
                }
        }
    }

    /**
     * Reads one photo of a series: as taken and, if that reads better, flattened with the help of
     * the first reading. Both readings correct each other word by word.
     */
    private suspend fun readForMerging(bitmap: Bitmap, language: OcrLanguage, flatten: Boolean): List<ReadLine> {
        val asTaken = ocrEngine.recognize(bitmap, language)
        val flat = if (flatten) {
            catching {
                TextGuidedFlattener.flatten(bitmap, asTaken)?.let { Variant(it, ocrEngine.recognize(it, language)) }
            }.getOrNull()
        } else {
            null
        }
        val useFlat = flat != null && DewarpChoice.preferDewarped(asTaken.qualityScore, flat.ocr.qualityScore)
        return if (useFlat) {
            flat.ocr.votedWith(listOf(asTaken)).readLines(flat.bitmap.height)
        } else {
            asTaken.votedWith(listOfNotNull(flat?.ocr)).readLines(bitmap.height)
        }
    }

    /** Joins the text of all photos taken so far and shows it. */
    fun finishMultiPhoto() {
        val session = multiPhotoSession
        if (session.readings.isEmpty() || _uiState.value.isMergingPhotos) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMergingPhotos = true) }
            session.jobs.joinAll()
            val merged = withContext(Dispatchers.Default) {
                TextMerge.merge(session.readings.filterNotNull().filter { it.isNotEmpty() })
            }.map(ReadLine::text)
            // The photos were discarded while this was running.
            if (session !== multiPhotoSession) return@launch
            _uiState.update { it.copy(isMergingPhotos = false, mergedLines = merged) }
            val settings = _settings.value
            if (settings.autoCopy) copyToClipboard(mergedText(merged, settings))
        }
    }

    /** Discards the photos taken so far and their text. */
    fun resetMultiPhoto() {
        multiPhotoSession.jobs.forEach(Job::cancel)
        multiPhotoSession = MultiPhotoSession()
        _uiState.update {
            it.copy(multiPhotoCount = 0, isMergingPhotos = false, mergedLines = null)
        }
    }

    /** Drops the current image and returns to the camera. */
    fun closeImage() {
        forgetVariants()
        ocrRepository.clear()
        _uiState.update {
            EditorUiState(
                language = it.language,
                overlaysEnabled = it.overlaysEnabled,
                captureMode = it.captureMode,
            )
        }
    }

    /**
     * Opens an image file. Images sent by other apps are usually screenshots and are read as they
     * are; images picked from inside the app are treated like camera photos and may be flattened.
     */
    fun openUri(uri: Uri, openedExternally: Boolean = false) {
        forgetVariants()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    sourceUri = uri,
                    openedExternally = openedExternally,
                    isLoadingImage = true,
                    isRunningOcr = false,
                    ocrResult = null,
                    errorMessage = null,
                )
            }
            runCatching { imageLoader.decode(uri) }
                .onSuccess { bitmap ->
                    ocrRepository.clear()
                    _uiState.update {
                        it.copy(
                            bitmap = bitmap,
                            isLoadingImage = false,
                            lastSavedUri = null,
                        )
                    }
                    if (openedExternally) runOcr(force = true) else readPhoto(bitmap, _settings.value.run { level(quality).passes })
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoadingImage = false,
                            errorMessage = throwable.message ?: "Could not open image",
                        )
                    }
                }
        }
    }

    /** Swaps between the original photo and its flattened version. */
    fun toggleDewarp() {
        val original = originalVariant ?: return
        val dewarped = dewarpedVariant ?: return
        val showDewarped = !_uiState.value.showingDewarped
        val variant = if (showDewarped) dewarped else original
        ocrRepository.clear()
        _uiState.update {
            it.copy(
                bitmap = variant.bitmap,
                ocrResult = variant.ocr,
                showingDewarped = showDewarped,
            )
        }
    }

    fun setLanguage(language: OcrLanguage) {
        if (_uiState.value.language == language) return
        forgetVariants()
        _uiState.update { it.copy(language = language, ocrResult = null) }
        runOcr(force = true)
    }

    fun toggleOverlays() {
        _uiState.update { it.copy(overlaysEnabled = !it.overlaysEnabled) }
    }

    fun rerunOcr() {
        forgetVariants()
        runOcr(force = true)
    }

    fun rotateLeft() {
        rotate(-90f)
    }

    fun rotateRight() {
        rotate(90f)
    }

    fun cropTo(cropState: CropState) {
        val current = _uiState.value.bitmap ?: return
        forgetVariants()
        viewModelScope.launch {
            runCatching {
                if (cropState.preset == CropPreset.Document) {
                    BitmapEditor.cropPerspective(current, cropState.documentCorners)
                } else {
                    BitmapEditor.crop(current, cropState.rect)
                }
            }
                .onSuccess { bitmap ->
                    ocrRepository.clear()
                    _uiState.update { it.copy(bitmap = bitmap, ocrResult = null) }
                    runOcr(force = true)
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(errorMessage = throwable.message ?: "Could not crop image")
                    }
                }
        }
    }

    fun saveCopy() {
        val current = _uiState.value.bitmap ?: return
        viewModelScope.launch {
            runCatching { saveImageUseCase.saveCopy(current) }
                .onSuccess { uri ->
                    _uiState.update { it.copy(lastSavedUri = uri, errorMessage = null) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(errorMessage = throwable.message ?: "Could not save copy")
                    }
                }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(errorMessage = null, lastSavedUri = null, textCopied = false) }
    }

    /**
     * Reads a photo in steps so text shows up as fast as possible:
     *  1. recognize the photo as taken and show that at once;
     *  2. flatten the photo, recognize it again and switch to the flattened version if it reads
     *     clearly better than the photo as taken;
     *  3. correct single words by comparing all readings.
     *
     * [passes] sets how much of this happens: 1 stops after step 1, 2 flattens with the help of
     * the text, 3 also tries the UVDoc network and takes the better of both.
     */
    private fun readPhoto(bitmap: Bitmap, passes: Int) {
        val language = _uiState.value.language
        val autoDewarp = _settings.value.autoDewarp && passes >= 2
        photoJob = viewModelScope.launch {
            readingTurn.withLock { readPhoto(bitmap, language, autoDewarp, withNetwork = passes >= 3) }
        }
    }

    private suspend fun readPhoto(bitmap: Bitmap, language: OcrLanguage, autoDewarp: Boolean, withNetwork: Boolean) {
        _uiState.update { it.copy(isRunningOcr = true, errorMessage = null) }
        val originalOcr = catching { ocrEngine.recognize(bitmap, language) }
            .getOrElse { throwable ->
                _uiState.update {
                    it.copy(isRunningOcr = false, errorMessage = throwable.message ?: "OCR failed")
                }
                return
            }
        val original = Variant(bitmap, originalOcr)
        originalVariant = original
        _uiState.update {
            it.copy(ocrResult = originalOcr, isRunningOcr = false, isDewarping = autoDewarp)
        }

        // Up to two ways of flattening compete: one guided by the text just read, one by the
        // UVDoc network. A failed flattening is not an error for the user: the other readings stay.
        val flattened = ArrayList<Variant>()
        if (autoDewarp) {
            catching {
                TextGuidedFlattener.flatten(bitmap, originalOcr)
                    ?.let { Variant(it, ocrEngine.recognize(it, language)) }
            }.getOrNull()?.let(flattened::add)
            if (withNetwork) {
                catching {
                    val flat = dewarper.dewarp(bitmap)
                    Variant(flat, ocrEngine.recognize(flat, language))
                }.getOrNull()?.let(flattened::add)
            }
        }
        val bestFlat = flattened.maxByOrNull { it.ocr.qualityScore }
        val showDewarped = bestFlat != null && DewarpChoice.preferDewarped(
            originalScore = originalOcr.qualityScore,
            dewarpedScore = bestFlat.ocr.qualityScore,
        )
        // Each version is then corrected word by word with what the other versions read.
        val readings = listOf(originalOcr) + flattened.map { it.ocr }
        val votedOriginal = original.copy(ocr = originalOcr.votedWith(readings - originalOcr))
        val votedFlat = bestFlat?.let { best -> best.copy(ocr = best.ocr.votedWith(readings - best.ocr)) }
        originalVariant = votedOriginal
        dewarpedVariant = votedFlat
        val shown = if (showDewarped && votedFlat != null) votedFlat else votedOriginal
        _uiState.update {
            it.copy(
                bitmap = shown.bitmap,
                ocrResult = shown.ocr,
                isDewarping = false,
                canToggleDewarp = votedFlat != null,
                showingDewarped = showDewarped,
            )
        }
        val settings = _settings.value
        if (settings.autoCopy) copyToClipboard(shown.ocr.textToCopy(settings))
    }

    private fun autoSave(bitmap: Bitmap) {
        if (!_settings.value.autoSavePhotos) return
        viewModelScope.launch {
            catching { saveImageUseCase.saveCopy(bitmap) }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(errorMessage = throwable.message ?: "Could not save photo")
                    }
                }
        }
    }

    private fun copyToClipboard(text: String) {
        if (text.isBlank()) return
        getApplication<Application>()
            .getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("SimpleOCR", text))
        _uiState.update { it.copy(textCopied = true) }
    }

    /** Like runCatching, but lets a cancelled coroutine stop instead of reporting an error. */
    private inline fun <T> catching(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }

    /** Stops a running photo read and drops both readings, e.g. before the image is edited. */
    private fun forgetVariants() {
        photoJob?.cancel()
        photoJob = null
        originalVariant = null
        dewarpedVariant = null
        _uiState.update {
            it.copy(isDewarping = false, canToggleDewarp = false, showingDewarped = false)
        }
    }

    private fun rotate(degrees: Float) {
        val current = _uiState.value.bitmap ?: return
        forgetVariants()
        viewModelScope.launch {
            val rotated = BitmapEditor.rotate(current, degrees)
            ocrRepository.clear()
            _uiState.update { it.copy(bitmap = rotated, ocrResult = null) }
            runOcr(force = true)
        }
    }

    private fun runOcr(force: Boolean) {
        val current: Bitmap = _uiState.value.bitmap ?: return
        val language = _uiState.value.language
        viewModelScope.launch {
            _uiState.update { it.copy(isRunningOcr = true, errorMessage = null) }
            runCatching { ocrRepository.recognize(current, language, force) }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(ocrResult = result, isRunningOcr = false)
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isRunningOcr = false,
                            errorMessage = throwable.message ?: "OCR failed",
                        )
                    }
                }
        }
    }

    companion object {
        /** The joined lines of several photos as one paragraph of text. */
        fun mergedText(lines: List<String>, settings: AppSettings): String = TextFormatter.format(
            paragraphs = listOf(lines),
            flowingText = settings.flowingText,
            joinHyphenatedWords = settings.joinHyphenatedWords,
        )
    }
}
