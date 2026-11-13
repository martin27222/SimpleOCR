package io.github.martin27222.simpleocr.core.dewarp

import java.util.stream.IntStream

/**
 * Builds the flattened image described by a [DewarpGrid].
 *
 * For every output pixel:
 *  1. find its position inside the grid and blend the four surrounding nodes (bilinear), which
 *     gives the matching position in the source photo;
 *  2. read the source photo there, again blending the four surrounding pixels.
 *
 * Positions outside the photo use the nearest edge pixel, so no black wedges appear.
 * Pixels are packed ARGB ints, as in android.graphics.Bitmap.getPixels.
 */
object GridRemap {
    fun remap(
        source: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        grid: DewarpGrid,
        outputWidth: Int,
        outputHeight: Int,
    ): IntArray {
        require(source.size == sourceWidth * sourceHeight) { "Source size does not match its dimensions" }
        require(outputWidth >= 2 && outputHeight >= 2) { "Output must be at least 2 x 2 pixels" }
        val output = IntArray(outputWidth * outputHeight)
        // Rows are independent, so they can be computed on all cores.
        IntStream.range(0, outputHeight).parallel().forEach { row ->
            remapRow(source, sourceWidth, sourceHeight, grid, outputWidth, outputHeight, row, output)
        }
        return output
    }

    private fun remapRow(
        source: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        grid: DewarpGrid,
        outputWidth: Int,
        outputHeight: Int,
        row: Int,
        output: IntArray,
    ) {
        val cols = grid.cols
        val gridY = row.toFloat() * (grid.rows - 1) / (outputHeight - 1)
        val gridRow = gridY.toInt().coerceAtMost(grid.rows - 2)
        val rowBlend = gridY - gridRow
        val upper = gridRow * cols
        val lower = upper + cols
        val gridStepX = (cols - 1).toFloat() / (outputWidth - 1)
        val maxX = (sourceWidth - 1).toFloat()
        val maxY = (sourceHeight - 1).toFloat()

        for (col in 0 until outputWidth) {
            val gridX = col * gridStepX
            val gridCol = gridX.toInt().coerceAtMost(cols - 2)
            val colBlend = gridX - gridCol

            val topX = lerp(grid.x[upper + gridCol], grid.x[upper + gridCol + 1], colBlend)
            val bottomX = lerp(grid.x[lower + gridCol], grid.x[lower + gridCol + 1], colBlend)
            val topY = lerp(grid.y[upper + gridCol], grid.y[upper + gridCol + 1], colBlend)
            val bottomY = lerp(grid.y[lower + gridCol], grid.y[lower + gridCol + 1], colBlend)

            // Normalized -1..1 to pixel coordinates, clamped to the photo.
            val sourceX = ((lerp(topX, bottomX, rowBlend) + 1f) * 0.5f * maxX).coerceIn(0f, maxX)
            val sourceY = ((lerp(topY, bottomY, rowBlend) + 1f) * 0.5f * maxY).coerceIn(0f, maxY)

            output[row * outputWidth + col] = sample(source, sourceWidth, sourceHeight, sourceX, sourceY)
        }
    }

    private fun sample(source: IntArray, width: Int, height: Int, x: Float, y: Float): Int {
        val x0 = x.toInt().coerceAtMost(width - 1)
        val y0 = y.toInt().coerceAtMost(height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val fx = x - x0
        val fy = y - y0

        val topLeft = source[y0 * width + x0]
        val topRight = source[y0 * width + x1]
        val bottomLeft = source[y1 * width + x0]
        val bottomRight = source[y1 * width + x1]

        var result = 0
        var shift = 24
        while (shift >= 0) {
            val top = lerp(((topLeft ushr shift) and 0xFF).toFloat(), ((topRight ushr shift) and 0xFF).toFloat(), fx)
            val bottom = lerp(((bottomLeft ushr shift) and 0xFF).toFloat(), ((bottomRight ushr shift) and 0xFF).toFloat(), fx)
            val channel = (lerp(top, bottom, fy) + 0.5f).toInt().coerceIn(0, 255)
            result = result or (channel shl shift)
            shift -= 8
        }
        return result
    }

    private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction
}
