package io.github.martin27222.simpleocr.core.merge

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageMergeTest {
    private val page = listOf(
        "Heat the butter in a large pan and add the chopped onion and the garlic.",
        "Fry for a few minutes until soft, then stir until golden and set aside.",
        "Take your blind-baked pastry case and scatter the onion over the base.",
        "Whisk the eggs with the cream, season well and pour over the filling.",
        "Bake for thirty minutes until just set in the middle and lightly golden.",
        "Leave to cool for ten minutes before cutting into slices and serving.",
    )

    @Test
    fun twoPhotosSideBySide() {
        val photos = listOf(
            photo(from = 0.00f, to = 0.62f, originX = 0f, originY = 0f, scale = 1.0f, turn = 0f),
            photo(from = 0.38f, to = 1.00f, originX = 270f, originY = -14f, scale = 1.12f, turn = 0.02f),
        )
        assertEquals(page, text(PageMerge.merge(photos)))
    }

    @Test
    fun threePhotosAroundABottle() {
        val photos = listOf(
            photo(from = 0.00f, to = 0.45f, originX = 0f, originY = 0f, scale = 1.0f, turn = 0f),
            photo(from = 0.30f, to = 0.75f, originX = 200f, originY = 9f, scale = 0.93f, turn = -0.015f),
            photo(from = 0.60f, to = 1.00f, originX = 410f, originY = -6f, scale = 1.05f, turn = 0.01f),
        )
        assertEquals(page, text(PageMerge.merge(photos)))
    }

    @Test
    fun repeatedWordsAtTheJoinAreKept() {
        // "until" appears twice in line 2; the join must neither drop nor double it.
        val photos = listOf(
            photo(from = 0.00f, to = 0.50f, originX = 0f, originY = 0f, scale = 1.0f, turn = 0f),
            photo(from = 0.30f, to = 1.00f, originX = 210f, originY = 4f, scale = 1.0f, turn = 0f),
        )
        val lines = text(PageMerge.merge(photos))
        assertEquals(page[1], lines[1])
    }

    @Test
    fun bentPhotos() {
        // Lines of a curved page bend up towards the photo edges by up to a line height.
        val photos = listOf(
            photo(from = 0.00f, to = 0.60f, originX = 0f, originY = 0f, scale = 1.0f, turn = 0f, bend = 25f),
            photo(from = 0.40f, to = 1.00f, originX = 280f, originY = 6f, scale = 1.06f, turn = 0.01f, bend = 25f),
        )
        assertEquals(page, text(PageMerge.merge(photos)))
    }

    @Test
    fun photoWithoutSharedWordsIsNotPlaced() {
        val photos = listOf(
            photo(from = 0.00f, to = 0.30f, originX = 0f, originY = 0f, scale = 1.0f, turn = 0f),
            photo(from = 0.70f, to = 1.00f, originX = 500f, originY = 0f, scale = 1.0f, turn = 0f),
        )
        assertNull(PageMerge.merge(photos))
    }

    @Test
    fun paragraphsLeaveOutSpecks() {
        fun word(text: String, left: Float, top: Float) =
            PlacedWord(text, 0.9f, left, top, left + text.length * CHAR_WIDTH, top + LINE_HEIGHT)
        val lines = listOf(
            listOf(word("First", 0f, 0f), word("line", 60f, 0f)),
            listOf(word("second", 0f, 30f), word("line.", 70f, 30f)),
            listOf(word("¢", 400f, 300f)),
        )
        assertEquals(listOf(listOf("First line", "second line.")), PageMerge.paragraphs(lines))
    }

    // --- A printed page and photos of parts of it ----------------------------------------------

    private class Printed(val text: String, val left: Float, val top: Float)

    private val printed: List<List<Printed>> = page.mapIndexed { row, line ->
        var x = 0f
        line.split(" ").map { word ->
            Printed(word, x, row * LINE_STEP).also { x += (word.length + 1) * CHAR_WIDTH }
        }
    }

    private val pageWidth = printed.maxOf { line -> line.last().left + line.last().text.length * CHAR_WIDTH }

    /**
     * What a reader sees in a photo of the strip [from]..[to] of the page width: letters whose
     * middle lies outside the strip are cut off. The photo is shifted to [originX], [originY],
     * enlarged by [scale] and turned by [turn] radians; lines bend up by [bend] pixels towards
     * the photo's edges.
     */
    private fun photo(
        from: Float,
        to: Float,
        originX: Float,
        originY: Float,
        scale: Float,
        turn: Float,
        bend: Float = 0f,
    ): List<List<PlacedWord>> {
        val start = from * pageWidth
        val end = to * pageWidth
        val middle = (start + end) / 2f
        return printed.map { line ->
            line.mapNotNull { word ->
                val visible = word.text.indices.filter { index ->
                    val middle = word.left + (index + 0.5f) * CHAR_WIDTH
                    middle in start..end
                }
                if (visible.isEmpty()) return@mapNotNull null
                val left = word.left + visible.first() * CHAR_WIDTH
                val right = word.left + (visible.last() + 1) * CHAR_WIDTH
                val cut = visible.size < word.text.length
                // Into the photo's own coordinates.
                val x = (left + right) / 2f - originX
                val offCenter = ((left + right) / 2f - middle) / (end - start)
                val y = word.top + LINE_HEIGHT / 2f - originY - bend * 4f * offCenter * offCenter
                val centerX = scale * (cos(turn) * x - sin(turn) * y)
                val centerY = scale * (sin(turn) * x + cos(turn) * y)
                val halfWidth = scale * (right - left) / 2f
                val halfHeight = scale * LINE_HEIGHT / 2f
                // A cut word of one or two letters is often read as a stray mark.
                val seen = word.text.substring(visible.first(), visible.last() + 1)
                PlacedWord(
                    text = if (cut && seen.length <= 2) "¢" else seen,
                    confidence = if (cut) 0.6f else 0.9f,
                    left = centerX - halfWidth,
                    top = centerY - halfHeight,
                    right = centerX + halfWidth,
                    bottom = centerY + halfHeight,
                )
            }
        }
    }

    private fun text(lines: List<List<PlacedWord>>?): List<String> =
        lines!!.sortedBy { line -> line.map { it.centerY }.average() }
            .map { line -> line.sortedBy { it.left }.joinToString(" ") { it.text } }

    private companion object {
        const val CHAR_WIDTH = 10f
        const val LINE_HEIGHT = 20f
        const val LINE_STEP = 30f
    }
}
