package io.github.martin27222.simpleocr.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import kotlin.math.max

/** Turns a CameraX capture into an upright bitmap that is small enough for fast OCR. */
object CapturedPhoto {
    /** Speed mode. Same limit as images opened from other apps, so both paths behave alike. */
    const val MAX_SIDE_PX = 3000

    /** Quality mode: keeps a 12 megapixel photo (4080 x 3072) at full size. */
    const val QUALITY_MAX_SIDE_PX = 4096

    fun toUprightBitmap(image: ImageProxy, maxSide: Int = MAX_SIDE_PX): Bitmap {
        val raw = image.toBitmap()
        val matrix = uprightMatrix(
            width = raw.width,
            height = raw.height,
            rotationDegrees = image.imageInfo.rotationDegrees,
            maxSide = maxSide,
        )
        if (matrix.isIdentity) return raw
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
            if (it !== raw) raw.recycle()
        }
    }

    fun uprightMatrix(width: Int, height: Int, rotationDegrees: Int, maxSide: Int = MAX_SIDE_PX): Matrix {
        val scale = downscaleFactor(width, height, maxSide)
        return Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotationDegrees % 360 != 0) postRotate(rotationDegrees.toFloat())
        }
    }

    fun downscaleFactor(width: Int, height: Int, maxSide: Int = MAX_SIDE_PX): Float {
        val longSide = max(width, height)
        return if (longSide > maxSide) maxSide.toFloat() / longSide else 1f
    }
}
