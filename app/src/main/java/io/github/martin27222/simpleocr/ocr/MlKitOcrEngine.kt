// Derived from EasyOCR (https://github.com/MosesEllermann/EasyOCR), Apache License 2.0.
// Modified for SimpleOCR; see NOTICE and the git history for the changes.

package io.github.martin27222.simpleocr.ocr

import android.graphics.Bitmap
import io.github.martin27222.simpleocr.geometry.RectF2
import io.github.martin27222.simpleocr.core.merge.ReadWord
import io.github.martin27222.simpleocr.core.rectify.Glyph
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MlKitOcrEngine : OcrEngine {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognize(
        bitmap: Bitmap,
        language: OcrLanguage,
    ): OcrResult = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val text = recognizer.process(image).await()
        val blocks = text.textBlocks.mapNotNull { block ->
            val box = block.boundingBox ?: return@mapNotNull null
            RecognizedTextBlock(
                text = block.text.trim(),
                boundingBox = RectF2(
                    left = box.left.toFloat(),
                    top = box.top.toFloat(),
                    right = box.right.toFloat(),
                    bottom = box.bottom.toFloat(),
                ),
                lines = block.lines.mapNotNull { line ->
                    val lineBox = line.boundingBox ?: return@mapNotNull null
                    RecognizedTextLine(
                        text = line.text.trim(),
                        boundingBox = RectF2(
                            left = lineBox.left.toFloat(),
                            top = lineBox.top.toFloat(),
                            right = lineBox.right.toFloat(),
                            bottom = lineBox.bottom.toFloat(),
                        ),
                        confidence = line.confidence,
                        words = line.elements.map { ReadWord(it.text, it.confidence) },
                        glyphs = glyphs(line),
                    )
                }.filter { it.text.isNotBlank() },
            )
        }.filter { it.text.isNotBlank() }

        OcrResultMapper.fromBlocks(blocks).arranged()
    }

    /**
     * Places one point per character along each recognized word. ML Kit reports a tilted box per
     * word; the characters are spread evenly between its left and right edge.
     */
    private fun glyphs(line: Text.Line): List<Glyph> {
        val glyphs = ArrayList<Glyph>()
        var position = 0
        for (element in line.elements) {
            val length = element.text.length
            val corners = element.cornerPoints
            if (length > 0 && corners != null && corners.size == 4) {
                // Corners run clockwise from the top left.
                val leftX = (corners[0].x + corners[3].x) / 2f
                val leftY = (corners[0].y + corners[3].y) / 2f
                val rightX = (corners[1].x + corners[2].x) / 2f
                val rightY = (corners[1].y + corners[2].y) / 2f
                val height = (
                    hypot((corners[3].x - corners[0].x).toFloat(), (corners[3].y - corners[0].y).toFloat()) +
                        hypot((corners[2].x - corners[1].x).toFloat(), (corners[2].y - corners[1].y).toFloat())
                    ) / 2f
                val pitch = (rightX - leftX) / length
                for (index in 0 until length) {
                    val along = (index + 0.5f) / length
                    val centerX = leftX + (rightX - leftX) * along
                    glyphs += Glyph(
                        centerX = centerX,
                        centerY = leftY + (rightY - leftY) * along,
                        height = height,
                        left = centerX - pitch / 2f,
                        position = (position + index).toFloat(),
                    )
                }
            }
            position += length + 1
        }
        return glyphs
    }
}
