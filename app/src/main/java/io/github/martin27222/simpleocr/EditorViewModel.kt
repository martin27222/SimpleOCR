// Derived from EasyOCR (https://github.com/MosesEllermann/EasyOCR), Apache License 2.0.
// Modified for SimpleOCR; see NOTICE and the git history for the changes.

package io.github.martin27222.simpleocr

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.martin27222.simpleocr.geometry.RectF2
import io.github.martin27222.simpleocr.image.BitmapEditor
import io.github.martin27222.simpleocr.image.ImageLoader
import io.github.martin27222.simpleocr.image.SaveImageUseCase
import io.github.martin27222.simpleocr.intents.ImageEditIntentHandler
import io.github.martin27222.simpleocr.ocr.MlKitOcrEngine
import io.github.martin27222.simpleocr.ocr.OcrLanguage
import io.github.martin27222.simpleocr.ocr.OcrRepository
import io.github.martin27222.simpleocr.ui.CropPreset
import io.github.martin27222.simpleocr.ui.CropState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditorViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val intentHandler = ImageEditIntentHandler()
    private val imageLoader = ImageLoader(application.contentResolver)
    private val ocrRepository = OcrRepository(MlKitOcrEngine())
    private val saveImageUseCase = SaveImageUseCase(application)

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState

    fun openIntent(intent: Intent?) {
        val input = intentHandler.extract(intent) ?: return
        intentHandler.tryPersistReadPermission(getApplication<Application>().contentResolver, input)
        openUri(input.uri, openedExternally = true)
    }

    /** Shows a photo taken with the in-app camera. The bitmap only lives in memory. */
    fun openCapturedPhoto(bitmap: Bitmap) {
        ocrRepository.clear()
        _uiState.update {
            it.copy(
                sourceUri = null,
                bitmap = bitmap,
                ocrResult = null,
                isLoadingImage = false,
                isRunningOcr = false,
                openedExternally = false,
                lastSavedUri = null,
                errorMessage = null,
            )
        }
        runOcr(force = true)
    }

    /** Drops the current image and returns to the camera. */
    fun closeImage() {
        ocrRepository.clear()
        _uiState.update {
            EditorUiState(language = it.language, overlaysEnabled = it.overlaysEnabled)
        }
    }

    fun openUri(uri: Uri, openedExternally: Boolean = false) {
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
                    runOcr(force = true)
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

    fun setLanguage(language: OcrLanguage) {
        if (_uiState.value.language == language) return
        _uiState.update { it.copy(language = language, ocrResult = null) }
        runOcr(force = true)
    }

    fun toggleOverlays() {
        _uiState.update { it.copy(overlaysEnabled = !it.overlaysEnabled) }
    }

    fun rerunOcr() {
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

    private fun rotate(degrees: Float) {
        val current = _uiState.value.bitmap ?: return
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
