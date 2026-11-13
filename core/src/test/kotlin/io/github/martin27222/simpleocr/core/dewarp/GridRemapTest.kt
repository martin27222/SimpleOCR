package io.github.martin27222.simpleocr.core.dewarp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GridRemapTest {
    private fun gray(value: Int): Int = (0xFF shl 24) or (value shl 16) or (value shl 8) or value

    /** A 5 x 4 image whose gray value encodes the pixel position: 10 * x + 50 * y. */
    private val width = 5
    private val height = 4
    private val image = IntArray(width * height) { index -> gray(10 * (index % width) + 50 * (index / width)) }

    @Test
    fun identityGridReturnsTheSameImage() {
        val result = GridRemap.remap(image, width, height, DewarpGrid.identity(3, 3), width, height)
        assertArrayEquals(image, result)
    }

    @Test
    fun identityGridResizesLikeBilinearScaling() {
        // 9 x 7 output: every second pixel lands exactly on a source pixel, the rest halfway between.
        val result = GridRemap.remap(image, width, height, DewarpGrid.identity(2, 2), 9, 7)
        assertEquals(gray(0), result[0])
        assertEquals(gray(5), result[1])
        assertEquals(gray(10), result[2])
        assertEquals(gray(25), result[9])
        assertEquals(gray(10 * 4 + 50 * 3), result[9 * 7 - 1])
    }

    @Test
    fun mirroredGridFlipsTheImage() {
        val grid = DewarpGrid.identity(4, 3)
        val mirrored = DewarpGrid(grid.cols, grid.rows, FloatArray(grid.x.size) { -grid.x[it] }, grid.y)
        val result = GridRemap.remap(image, width, height, mirrored, width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                assertEquals(image[y * width + (width - 1 - x)], result[y * width + x])
            }
        }
    }

    @Test
    fun gridCanCropToTheCentralPart() {
        // Corners at +-0.5 select the middle half: x from 1 to 3, y from 0.75 to 2.25.
        val grid = DewarpGrid(
            cols = 2,
            rows = 2,
            x = floatArrayOf(-0.5f, 0.5f, -0.5f, 0.5f),
            y = floatArrayOf(-0.5f, -0.5f, 0.5f, 0.5f),
        )
        val result = GridRemap.remap(image, width, height, grid, 3, 3)
        assertEquals(gray(48), result[0]) // x = 1, y = 0.75 -> 10 + 37.5, rounded
        assertEquals(gray(95), result[4]) // x = 2, y = 1.5 -> 20 + 75
        assertEquals(gray(143), result[8]) // x = 3, y = 2.25 -> 30 + 112.5, rounded
    }

    @Test
    fun positionsOutsideThePhotoUseTheEdgePixel() {
        val grid = DewarpGrid(
            cols = 2,
            rows = 2,
            x = floatArrayOf(-3f, -2f, -3f, -2f),
            y = floatArrayOf(-1f, -1f, 1f, 1f),
        )
        val result = GridRemap.remap(image, width, height, grid, 2, height)
        for (y in 0 until height) {
            assertEquals(image[y * width], result[y * 2])
            assertEquals(image[y * width], result[y * 2 + 1])
        }
    }
}
