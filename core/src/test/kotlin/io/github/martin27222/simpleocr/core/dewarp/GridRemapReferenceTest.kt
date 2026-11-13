package io.github.martin27222.simpleocr.core.dewarp

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks [GridRemap] against PyTorch, the reference implementation UVDoc was written for.
 *
 * The files in test/resources/dewarp hold a small photo of a curved page, the grid the UVDoc
 * network predicted for it, and the flattened image PyTorch computed from both with
 * interpolate(align_corners=True) + grid_sample(align_corners=True, padding_mode="border").
 */
class GridRemapReferenceTest {
    private val width = 96
    private val height = 128

    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/dewarp/$name")) { "Missing test file $name" }.use { it.readBytes() }

    private fun rgbToArgb(bytes: ByteArray): IntArray = IntArray(bytes.size / 3) { index ->
        val r = bytes[3 * index].toInt() and 0xFF
        val g = bytes[3 * index + 1].toInt() and 0xFF
        val b = bytes[3 * index + 2].toInt() and 0xFF
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    @Test
    fun matchesThePyTorchReference() {
        val photo = rgbToArgb(resource("golden_photo_96x128.rgb"))
        val expected = rgbToArgb(resource("golden_flat_96x128.rgb"))
        val floats = ByteBuffer.wrap(resource("golden_grid_2x45x31.f32le")).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val nodes = 45 * 31
        val x = FloatArray(nodes).also { floats.get(it) }
        val y = FloatArray(nodes).also { floats.get(it) }

        val actual = GridRemap.remap(photo, width, height, DewarpGrid(cols = 31, rows = 45, x = x, y = y), width, height)

        assertEquals(expected.size, actual.size)
        var largestDifference = 0
        for (index in expected.indices) {
            for (shift in intArrayOf(16, 8, 0)) {
                val difference = abs(((expected[index] ushr shift) and 0xFF) - ((actual[index] ushr shift) and 0xFF))
                if (difference > largestDifference) largestDifference = difference
            }
        }
        // Float rounding may differ by one gray level, nothing more.
        assertTrue("Largest channel difference was $largestDifference", largestDifference <= 1)
    }
}
