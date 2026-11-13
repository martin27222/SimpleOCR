// Derived from EasyOCR (https://github.com/MosesEllermann/EasyOCR), Apache License 2.0.
// Modified for SimpleOCR; see NOTICE and the git history for the changes.

package io.github.martin27222.simpleocr

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.martin27222.simpleocr.core.dewarp.DewarpChoice
import io.github.martin27222.simpleocr.dewarp.Dewarper
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
import io.github.martin27222.simpleocr.ui.CropPreset
import io.github.martin27222.simpleocr.ui.CropState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditorViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val intentHandler = ImageEditIntentHandler()
    private val imageLoader = ImageLoader(application.contentResolver)
    private val ocrEngine: OcrEngine = MlKitOcrEngine()
    private val ocrRepository = OcrRepository(ocrEngine)
    private val dewarper: Dewarper = UvDocDewarper(application)
    private val saveImageUseCase = SaveImageUseCase(application)

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState

    /** One reading of the current photo: the image and the text found in it. */
    private data class Variant(val bitmap: Bitmap, val ocr: OcrResult)

    private var originalVariant: Variant? = null
    private var dewarpedVariant: Variant? = null
    private var photoJob: Job? = null

    // Becomes a user setting in a later step.
    private val autoDewarp = true

    fun openIntent(intent: Intent?) {
        val input = intentHandler.extract(intent) ?: return
        intentHandler.tryPersistReadPermission(getApplication<Application>().contentResolver, input)
        openUri(input.uri, openedExternally = true)
    }

    /** Shows a photo taken with the in-app camera. The bitmap only lives in memory. */
    fun openCapturedPhoto(bitmap: Bitmap) {
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
        readPhoto(bitmap)
    }

    /** Drops the current image and returns to the camera. */
    fun closeImage() {
        forgetVariants()
        ocrRepository.clear()
        _uiState.update {
            EditorUiState(language = it.language, overlaysEnabled = it.overlaysEnabled)
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
                    if (openedExternally) runOcr(force = true) else readPhoto(bitmap)
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
        _uiState.update { it.copy(errorMessage = null, lastSavedUri = null) }
    }

    /**
     * Reads a photo in two steps so text shows up as fast as possible:
     *  1. recognize the photo as taken and show that at once;
     *  2. flatten the photo, recognize it again and switch to it if it reads clearly better.
     */
    private fun readPhoto(bitmap: Bitmap) {
        val language = _uiState.value.language
        photoJob = viewModelScope.launch {
            _uiState.update { it.copy(isRunningOcr = true, errorMessage = null) }
            val originalOcr = runCatching { ocrEngine.recognize(bitmap, language) }
                .getOrElse { throwable ->
                    _uiState.update {
                        it.copy(isRunningOcr = false, errorMessage = throwable.message ?: "OCR failed")
                    }
                    return@launch
                }
            originalVariant = Variant(bitmap, originalOcr)
            _uiState.update {
                it.copy(ocrResult = originalOcr, isRunningOcr = false, isDewarping = autoDewarp)
            }
            if (!autoDewarp) return@launch

            // A failed flattening is not an error for the user: the original reading stays.
            val dewarped = runCatching {
                val flat = dewarper.dewarp(bitmap)
                Variant(flat, ocrEngine.recognize(flat, language))
            }.getOrNull()
            if (dewarped == null) {
                _uiState.update { it.copy(isDewarping = false) }
                return@launch
            }
            dewarpedVariant = dewarped
            val showDewarped = DewarpChoice.preferDewarped(
                originalScore = originalOcr.qualityScore,
                dewarpedScore = dewarped.ocr.qualityScore,
            )
            val shown = if (showDewarped) dewarped else Variant(bitmap, originalOcr)
            _uiState.update {
                it.copy(
                    bitmap = shown.bitmap,
                    ocrResult = shown.ocr,
                    isDewarping = false,
                    canToggleDewarp = true,
                    showingDewarped = showDewarped,
                )
            }
        }
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
}
