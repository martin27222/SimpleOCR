// Derived from EasyOCR (https://github.com/MosesEllermann/EasyOCR), Apache License 2.0.
// Modified for SimpleOCR; see NOTICE and the git history for the changes.

package io.github.martin27222.simpleocr

import android.graphics.Bitmap
import android.net.Uri
import io.github.martin27222.simpleocr.ocr.OcrLanguage
import io.github.martin27222.simpleocr.ocr.OcrResult

/** What the shutter button does. */
enum class CaptureMode {
    /** One photo, flattened if that helps. */
    Photo,

    /** Several overlapping photos of one object, joined into one text. */
    MultiPhoto,
}

data class EditorUiState(
    val sourceUri: Uri? = null,
    val bitmap: Bitmap? = null,
    val ocrResult: OcrResult? = null,
    val language: OcrLanguage = OcrLanguage.EnglishGerman,
    val overlaysEnabled: Boolean = true,
    val openedExternally: Boolean = false,
    val isLoadingImage: Boolean = false,
    val isRunningOcr: Boolean = false,
    /** The flattened version of the photo is still being computed. */
    val isDewarping: Boolean = false,
    /** Both the original photo and a flattened version exist and can be swapped. */
    val canToggleDewarp: Boolean = false,
    val showingDewarped: Boolean = false,
    val captureMode: CaptureMode = CaptureMode.Photo,
    /** Photos taken so far in the mode for several photos. */
    val multiPhotoCount: Int = 0,
    val isMergingPhotos: Boolean = false,
    /** The joined text of several photos as paragraphs of lines; non-null while it is shown. */
    val mergedParagraphs: List<List<String>>? = null,
    /** Text was put on the clipboard automatically and the user has not been told yet. */
    val textCopied: Boolean = false,
    val lastSavedUri: Uri? = null,
    val errorMessage: String? = null,
) {
    val hasImage: Boolean = bitmap != null
    val fullText: String = ocrResult?.fullText.orEmpty()
}
