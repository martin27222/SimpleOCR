// Derived from EasyOCR (https://github.com/MosesEllermann/EasyOCR), Apache License 2.0.
// Modified for SimpleOCR; see NOTICE and the git history for the changes.

package io.github.martin27222.simpleocr.ocr

import io.github.martin27222.simpleocr.geometry.RectF2

data class OcrResult(
    val fullText: String,
    val blocks: List<OcrTextBlock>,
)

data class OcrTextBlock(
    val id: String,
    val text: String,
    val boundingBox: RectF2,
    val lines: List<OcrTextLine> = emptyList(),
)

data class OcrTextLine(
    val id: String,
    val text: String,
    val boundingBox: RectF2,
)

enum class OcrLanguage(val label: String) {
    EnglishGerman("English + German"),
    English("English"),
    German("German"),
}
