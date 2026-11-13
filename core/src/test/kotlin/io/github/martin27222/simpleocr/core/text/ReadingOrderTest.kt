package io.github.martin27222.simpleocr.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingOrderTest {
    private fun line(text: String, left: Float, top: Float, height: Float = 20f) =
        PlacedLine(text, 1f, left, top, top + height)

    @Test
    fun sortsRowsFromTopToBottom() {
        val sorted = ReadingOrder.sort(listOf(line("c", 0f, 80f), line("a", 0f, 0f), line("b", 0f, 40f)))
        assertEquals(listOf("a", "b", "c"), sorted.map { it.text })
    }

    @Test
    fun sortsLinesOfOneRowFromLeftToRight() {
        // "right" sits 3 px higher than "left" but is still the same row.
        val sorted = ReadingOrder.sort(
            listOf(line("right", 300f, 0f), line("left", 0f, 3f), line("below", 0f, 40f)),
        )
        assertEquals(listOf("left", "right", "below"), sorted.map { it.text })
    }

    @Test
    fun keepsAnEmptyListEmpty() {
        assertEquals(emptyList<PlacedLine>(), ReadingOrder.sort(emptyList()))
    }
}
