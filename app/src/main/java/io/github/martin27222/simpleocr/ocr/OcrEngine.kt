// Derived from EasyOCR (https://github.com/MosesEllermann/EasyOCR), Apache License 2.0.
// Modified for SimpleOCR; see NOTICE and the git history for the changes.

package io.github.martin27222.simpleocr.ocr

import android.graphics.Bitmap

interface OcrEngine {
    suspend fun recognize(
        bitmap: Bitmap,
        language: OcrLanguage = OcrLanguage.EnglishGerman,
    ): OcrResult
}
