package io.github.martin27222.simpleocr.dewarp

import android.graphics.Bitmap
import io.github.martin27222.simpleocr.core.dewarp.GridRemap
import io.github.martin27222.simpleocr.core.rectify.TextRectifier
import io.github.martin27222.simpleocr.ocr.OcrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Flattens a photo with the help of its first reading: the positions of the recognized characters
 * show how the page is bent and tilted. See [TextRectifier] for the method. No model is involved.
 */
object TextGuidedFlattener {
    /** Returns the flattened photo, or null if the reading holds too little text to go by. */
    suspend fun flatten(bitmap: Bitmap, reading: OcrResult): Bitmap? = withContext(Dispatchers.Default) {
        val lines = reading.blocks.flatMap { block -> block.lines.map { it.glyphs } }
        val width = bitmap.width
        val height = bitmap.height
        val map = TextRectifier.rectify(lines, width, height) ?: return@withContext null
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val flat = GridRemap.remap(pixels, width, height, map.grid, map.outputWidth, map.outputHeight)
        Bitmap.createBitmap(flat, map.outputWidth, map.outputHeight, Bitmap.Config.ARGB_8888)
    }
}
