package io.github.martin27222.simpleocr.core.rectify

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uses a made-up photo of a page whose distortion is known exactly: squeezed to about half width
 * on the left, lines tilted and curved, and the page smaller on the left than on the right.
 */
class TextRectifierTest {
    private val width = 2000
    private val height = 3000

    // Flat page position (u, v) -> position in the photo (x, y).
    private fun photoX(u: Double): Double {
        val s = u / 2000
        return 150 + 1700 * (s + 0.6 * s * s) / 1.6
    }

    private fun verticalScale(x: Double) = 0.8 + 0.2 * x / width

    private fun lift(x: Double): Double {
        val across = x / width - 0.5
        return 100 + 80 * across * across + 0.04 * x
    }

    private fun photoY(u: Double, v: Double): Double = lift(photoX(u)) + v * verticalScale(photoX(u))

    // Position in the photo -> flat page position.
    private fun pageU(x: Double): Double = 2000 * (-1 + sqrt(1 + 3.84 * (x - 150) / 1700)) / 1.2

    private fun pageV(x: Double, y: Double): Double = (y - lift(x)) / verticalScale(x)

    private fun page(lines: Int = 36, characters: Int = 70): List<List<Glyph>> = List(lines) { line ->
        val v = 300.0 + 60 * line
        List(characters) { index ->
            val u = 300.0 + 20 * index
            Glyph(
                centerX = photoX(u).toFloat(),
                centerY = photoY(u, v).toFloat(),
                height = (28 * verticalScale(photoX(u))).toFloat(),
                left = photoX(u - 8).toFloat(),
                position = index.toFloat(),
            )
        }
    }

    /** For every grid node inside the text: where on the flat page its source point really is. */
    private fun truePositions(map: FlatteningMap): List<List<Pair<Double, Double>?>> =
        List(map.grid.rows) { row ->
            List(map.grid.cols) { column ->
                val x = (map.grid.x[row * map.grid.cols + column] + 1) / 2.0 * (width - 1)
                val y = (map.grid.y[row * map.grid.cols + column] + 1) / 2.0 * (height - 1)
                val u = pageU(x)
                val v = pageV(x, y)
                if (u in 340.0..1640.0 && v in 340.0..2360.0) u to v else null
            }
        }

    @Test
    fun textLinesBecomeStraightRows() {
        val map = TextRectifier.rectify(page(), width, height)!!
        for (row in truePositions(map)) {
            val v = row.filterNotNull().map { it.second }
            if (v.size < 20) continue
            assertTrue("a row wanders ${v.max() - v.min()} px across text lines", v.max() - v.min() < 4.0)
        }
    }

    @Test
    fun charactersGetTheSameWidthEverywhere() {
        val map = TextRectifier.rectify(page(), width, height)!!
        for (row in truePositions(map)) {
            val inside = row.withIndex().filter { it.value != null }
            if (inside.size < 20) continue
            // On the flat page, u must grow evenly from column to column.
            val first = inside.first()
            val last = inside.last()
            val perColumn = (last.value!!.first - first.value!!.first) / (last.index - first.index)
            for (node in inside) {
                val expected = first.value!!.first + perColumn * (node.index - first.index)
                assertTrue(
                    "uneven stretch: ${node.value!!.first - expected} px",
                    abs(node.value!!.first - expected) < 20.0,
                )
            }
        }
    }

    @Test
    fun uprightEdgesStayUpright() {
        val map = TextRectifier.rectify(page(), width, height)!!
        val positions = truePositions(map)
        for (column in 0 until map.grid.cols step 10) {
            val u = positions.mapNotNull { it[column]?.first }
            if (u.size < 20) continue
            assertTrue("column leans by ${u.max() - u.min()} px", u.max() - u.min() < 20.0)
        }
    }

    @Test
    fun squeezedSideIsStretched() {
        val map = TextRectifier.rectify(page(), width, height)!!
        // The text is 1700 px wide in the photo where it is squeezed least, so the flat image
        // must be clearly wider than the photo's text area.
        assertTrue("output is only ${map.outputWidth} px wide", map.outputWidth > width)
    }

    @Test
    fun tooLittleTextGivesNoMap() {
        assertNull(TextRectifier.rectify(page(lines = 3), width, height))
        assertNull(TextRectifier.rectify(page(lines = 10, characters = 4), width, height))
        assertNull(TextRectifier.rectify(emptyList(), width, height))
    }
}
