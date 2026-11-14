package io.github.martin27222.simpleocr.ocr

import io.github.martin27222.simpleocr.geometry.RectF2
import io.github.martin27222.simpleocr.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrLayoutTest {
    private fun line(text: String, left: Float, top: Float, width: Float) =
        RecognizedTextLine(text, RectF2(left, top, left + width, top + 30f))

    /** A side column and the main text, handed over as one block the way the recognizer does it. */
    private val result = OcrResultMapper.fromBlocks(
        listOf(
            RecognizedTextBlock(
                text = "",
                boundingBox = RectF2(0f, 100f, 1500f, 330f),
                lines = listOf(
                    line("Heat the butter in a pan", 350f, 100f, 1150f),
                    line("1 onion", 0f, 115f, 200f),
                    line("and add the onion.", 350f, 150f, 1150f),
                    line("Fry for a few minutes", 350f, 200f, 1150f),
                    line("2 eggs", 0f, 215f, 200f),
                    line("until soft.", 350f, 250f, 500f),
                ),
            ).let { it.copy(text = it.lines.joinToString("\n") { line -> line.text }) },
        ),
    ).arranged()

    @Test
    fun sideColumnAndMainTextBecomeSeparateFrames() {
        val frames = result.frames

        assertEquals(2, frames.size)
        assertEquals(
            "Heat the butter in a pan and add the onion. Fry for a few minutes until soft.",
            frames.maxBy { it.size }.textToCopy(AppSettings()),
        )
    }

    @Test
    fun copyAllKeepsTheFramesApart() {
        assertEquals(
            "1 onion\n\n2 eggs\n\nHeat the butter in a pan and add the onion. Fry for a few minutes until soft.",
            result.textToCopy(AppSettings()),
        )
    }
}
