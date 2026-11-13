package io.github.martin27222.simpleocr.core.dewarp

/**
 * A backward map for flattening a photo of a curved page.
 *
 * Picture a regular grid of [cols] x [rows] nodes laid over the flattened output image, with the
 * corner nodes exactly on the image corners. For each node, [x] and [y] say where that spot is in
 * the source photo, in normalized coordinates: -1 is the left/top edge of the photo, +1 the
 * right/bottom edge. Nodes are stored row by row.
 *
 * This is the layout UVDoc produces and the "align_corners = true" convention of PyTorch's
 * grid_sample, so the output of the network can be used without conversion.
 */
class DewarpGrid(
    val cols: Int,
    val rows: Int,
    val x: FloatArray,
    val y: FloatArray,
) {
    init {
        require(cols >= 2 && rows >= 2) { "A grid needs at least 2 x 2 nodes" }
        require(x.size == cols * rows && y.size == cols * rows) { "Grid arrays must hold cols * rows values" }
    }

    companion object {
        /** A grid that maps every pixel onto itself. */
        fun identity(cols: Int, rows: Int): DewarpGrid {
            val x = FloatArray(cols * rows)
            val y = FloatArray(cols * rows)
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    x[row * cols + col] = -1f + 2f * col / (cols - 1)
                    y[row * cols + col] = -1f + 2f * row / (rows - 1)
                }
            }
            return DewarpGrid(cols, rows, x, y)
        }
    }
}
