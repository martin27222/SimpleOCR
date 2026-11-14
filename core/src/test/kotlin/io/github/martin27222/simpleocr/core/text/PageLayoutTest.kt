package io.github.martin27222.simpleocr.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class PageLayoutTest {
    /** Lines 30 px high, one every 50 px, starting at [top]. */
    private fun column(left: Float, right: Float, top: Float, count: Int): List<LineBox> =
        List(count) { LineBox(left, top + it * 50f, right, top + it * 50f + 30f) }

    @Test
    fun plainPageIsOneFrameWithOneParagraph() {
        val frames = PageLayout.frames(column(100f, 900f, 100f, 6))

        assertEquals(listOf(Frame(listOf(listOf(0, 1, 2, 3, 4, 5)))), frames)
    }

    @Test
    fun emptyBandStartsNewParagraph() {
        val lines = column(100f, 900f, 100f, 4) + column(100f, 900f, 340f, 3)

        val frames = PageLayout.frames(lines)

        assertEquals(listOf(Frame(listOf(listOf(0, 1, 2, 3), listOf(4, 5, 6)))), frames)
    }

    @Test
    fun sideColumnIsNotMixedIntoTheMainText() {
        // A heading across the page, a narrow side column with its own line spacing and the
        // main text: the layout of a typical cookbook page.
        val heading = listOf(LineBox(50f, 20f, 800f, 80f))
        val side = List(5) { LineBox(0f, 210f + it * 80f, 230f, 235f + it * 80f) }
        val main = column(350f, 1500f, 200f, 8)
        val lines = heading + side + main

        val frames = PageLayout.frames(lines)

        assertEquals(3, frames.size)
        assertEquals(listOf(0), frames[0].lines)
        assertEquals((1..5).toList(), frames[1].lines)
        assertEquals((6..13).toList(), frames[2].lines)
    }

    @Test
    fun pageNumberFarBelowIsItsOwnFrame() {
        val lines = column(100f, 900f, 100f, 5) + LineBox(480f, 600f, 520f, 630f)

        val frames = PageLayout.frames(lines)

        assertEquals(listOf(listOf(0, 1, 2, 3, 4), listOf(5)), frames.map { it.lines })
    }

    @Test
    fun shortLastLineAndPageNumberAreNotColumns() {
        // "Serves 4" at the left, the page number further down in the middle.
        val lines = column(100f, 900f, 100f, 5) + LineBox(100f, 400f, 220f, 430f) + LineBox(480f, 600f, 520f, 630f)

        val frames = PageLayout.frames(lines)

        assertEquals(listOf(listOf(0, 1, 2, 3, 4, 5), listOf(6)), frames.map { it.lines })
    }

    @Test
    fun linesSideBySideInOneRowReadLeftToRight() {
        // Two pieces of one printed line, the right one a few pixels higher.
        val lines = listOf(
            LineBox(100f, 100f, 900f, 130f),
            LineBox(520f, 147f, 900f, 177f),
            LineBox(100f, 150f, 500f, 180f),
            LineBox(100f, 200f, 900f, 230f),
        )

        val frames = PageLayout.frames(lines)

        assertEquals(listOf(listOf(0, 2, 1, 3)), frames.map { it.lines })
    }
}
