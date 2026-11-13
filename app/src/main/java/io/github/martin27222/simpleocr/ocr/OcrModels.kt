// Derived from EasyOCR (https://github.com/MosesEllermann/EasyOCR), Apache License 2.0.
// Modified for SimpleOCR; see NOTICE and the git history for the changes.

package io.github.martin27222.simpleocr.ocr

import io.github.martin27222.simpleocr.core.merge.ReadWord
import io.github.martin27222.simpleocr.core.rectify.Glyph
import io.github.martin27222.simpleocr.geometry.RectF2

data class OcrResult(
    val fullText: String,
    val blocks: List<OcrTextBlock>,
) {
    /**
     * Roughly "how many characters were probably read correctly": every line counts its visible
     * characters, weighted by how sure the recognizer was. More text and surer text both raise it,
     * which makes two readings of the same photo comparable.
     */
    val qualityScore: Float
        get() = blocks.sumOf { block ->
            block.lines.sumOf { line ->
                (line.text.count { !it.isWhitespace() } * line.confidence).toDouble()
            }
        }.toFloat()
}

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
    /** 0..1, how sure the recognizer is about this line. */
    val confidence: Float = 1f,
    /** The words of the line with their own confidence, for comparing several readings. */
    val words: List<ReadWord> = emptyList(),
    /** Where the characters of the line sit in the image, for flattening the page. */
    val glyphs: List<Glyph> = emptyList(),
)

enum class OcrLanguage(val label: String) {
    EnglishGerman("English + German"),
    English("English"),
    German("German"),
}
