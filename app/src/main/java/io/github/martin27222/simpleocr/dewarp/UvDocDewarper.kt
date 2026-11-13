package io.github.martin27222.simpleocr.dewarp

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import io.github.martin27222.simpleocr.core.dewarp.DewarpGrid
import io.github.martin27222.simpleocr.core.dewarp.GridRemap
import java.nio.FloatBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Dewarps with the UVDoc network (MIT License, https://github.com/tanguymagne/UVDoc).
 *
 * The network looks at a small copy of the photo and predicts a coarse grid that says where each
 * part of the flat page sits in the photo. [GridRemap] then rebuilds the page at full resolution.
 * Everything runs on the device through ONNX Runtime; see third_party/uvdoc for the model's origin.
 */
class UvDocDewarper(
    private val context: Context,
) : Dewarper {
    private val mutex = Mutex()
    private var session: OrtSession? = null

    override suspend fun dewarp(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val grid = mutex.withLock { predictGrid(bitmap) }
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val flat = GridRemap.remap(pixels, width, height, grid, width, height)
        Bitmap.createBitmap(flat, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun predictGrid(bitmap: Bitmap): DewarpGrid {
        val environment = OrtEnvironment.getEnvironment()
        val activeSession = session ?: createSession(environment).also { session = it }
        OnnxTensor.createTensor(
            environment,
            networkInput(bitmap),
            longArrayOf(1, 3, INPUT_HEIGHT.toLong(), INPUT_WIDTH.toLong()),
        ).use { input ->
            activeSession.run(mapOf(INPUT_NAME to input)).use { outputs ->
                val values = (outputs[0] as OnnxTensor).floatBuffer
                val nodes = GRID_COLS * GRID_ROWS
                val x = FloatArray(nodes)
                val y = FloatArray(nodes)
                values.get(x)
                values.get(y)
                return DewarpGrid(GRID_COLS, GRID_ROWS, x, y)
            }
        }
    }

    private fun createSession(environment: OrtEnvironment): OrtSession {
        val model = context.assets.open(MODEL_ASSET).use { it.readBytes() }
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(INFERENCE_THREADS)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        return environment.createSession(model, options)
    }

    /** Scales the photo to the network size and lays it out as planar RGB with values 0..1. */
    private fun networkInput(bitmap: Bitmap): FloatBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_WIDTH, INPUT_HEIGHT, true)
        val pixelCount = INPUT_WIDTH * INPUT_HEIGHT
        val pixels = IntArray(pixelCount)
        scaled.getPixels(pixels, 0, INPUT_WIDTH, 0, 0, INPUT_WIDTH, INPUT_HEIGHT)
        if (scaled !== bitmap) scaled.recycle()
        val buffer = FloatBuffer.allocate(3 * pixelCount)
        for (index in 0 until pixelCount) {
            val pixel = pixels[index]
            buffer.put(index, ((pixel shr 16) and 0xFF) / 255f)
            buffer.put(pixelCount + index, ((pixel shr 8) and 0xFF) / 255f)
            buffer.put(2 * pixelCount + index, (pixel and 0xFF) / 255f)
        }
        return buffer
    }

    private companion object {
        const val MODEL_ASSET = "uvdoc.onnx"
        const val INPUT_NAME = "image"
        const val INPUT_WIDTH = 488
        const val INPUT_HEIGHT = 712
        const val GRID_COLS = 31
        const val GRID_ROWS = 45
        const val INFERENCE_THREADS = 4
    }
}
