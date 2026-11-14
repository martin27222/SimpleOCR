package io.github.martin27222.simpleocr.ocr

import io.github.martin27222.simpleocr.core.merge.ReadWord
import io.github.martin27222.simpleocr.geometry.RectF2
import io.github.martin27222.simpleocr.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextTest {
    private fun line(text: String, left: Float, top: Float) =
        RecognizedTextLine(text, RectF2(left, top, left + 100f, top + 20f), confidence = 0.9f)

    private val result = OcrResultMapper.fromBlocks(
        listOf(
            RecognizedTextBlock(
                text = "Die Zei-\ntung von heute",
                boundingBox = RectF2(0f, 10f, 100f, 70f),
                lines = listOf(line("Die Zei-", 0f, 10f), line("tung von heute", 0f, 50f)),
            ),
            RecognizedTextBlock(
                text = "Seite 3",
                boundingBox = RectF2(0f, 100f, 100f, 120f),
                lines = listOf(line("Seite 3", 0f, 100f)),
            ),
        ),
    )

    @Test
    fun defaultSettingsGiveFlowingTextWithRejoinedWords() {
        assertEquals("Die Zeitung von heute\n\nSeite 3", result.textToCopy(AppSettings()))
    }

    @Test
    fun lineBreaksAndHyphensCanBeKept() {
        val settings = AppSettings(flowingText = false, joinHyphenatedWords = false)
        assertEquals("Die Zei-\ntung von heute\n\nSeite 3", result.textToCopy(settings))
    }

    @Test
    fun readLinesAreInReadingOrderWithConfidence() {
        val lines = result.readLines()
        assertEquals(listOf("Die Zei-", "tung von heute", "Seite 3"), lines.map { it.text })
        assertEquals(0.9f, lines.first().confidence, 0f)
    }

    @Test
    fun linesCutOffByThePhotoEdgeAreLeftOut() {
        // The photo ends at y = 121, right below the last line (100..120).
        assertEquals(listOf("Die Zei-", "tung von heute"), result.readLines(imageHeight = 121).map { it.text })
    }

    @Test
    fun placedWordsKeepTheirBoxesOrShareTheLineBox() {
        val withBoxes = RecognizedTextLine(
            text = "Hello world",
            boundingBox = RectF2(0f, 0f, 110f, 20f),
            words = listOf(ReadWord("Hello", 0.9f), ReadWord("world", 0.8f)),
            wordBoxes = listOf(RectF2(0f, 0f, 50f, 20f), RectF2(60f, 0f, 110f, 20f)),
        )
        val withoutBoxes = withBoxes.copy(boundingBox = RectF2(0f, 50f, 120f, 70f), wordBoxes = emptyList())
        val placed = OcrResultMapper.fromBlocks(
            listOf(RecognizedTextBlock("Hello world\nHello world", RectF2(0f, 0f, 120f, 70f), listOf(withBoxes, withoutBoxes))),
        ).placedLines()
        assertEquals(60f, placed[0][1].left, 0f)
        // Without boxes the line is shared by letters: 6 of 12 for "Hello" and its space.
        assertEquals(50f, placed[1][0].right, 0.01f)
        assertEquals(60f, placed[1][1].left, 0.01f)
    }
}
