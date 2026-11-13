package io.github.martin27222.simpleocr.ocr

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
                boundingBox = RectF2(0f, 0f, 100f, 60f),
                lines = listOf(line("Die Zei-", 0f, 0f), line("tung von heute", 0f, 40f)),
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
}
