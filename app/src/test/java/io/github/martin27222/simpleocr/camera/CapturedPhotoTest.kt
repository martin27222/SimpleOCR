package io.github.martin27222.simpleocr.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CapturedPhotoTest {
    @Test
    fun smallPhotosKeepTheirSize() {
        assertEquals(1f, CapturedPhoto.downscaleFactor(width = 3000, height = 2250), 0f)
        assertEquals(1f, CapturedPhoto.downscaleFactor(width = 640, height = 480), 0f)
    }

    @Test
    fun largePhotosShrinkSoTheLongSideHitsTheLimit() {
        assertEquals(0.75f, CapturedPhoto.downscaleFactor(width = 4000, height = 3000), 1e-6f)
        assertEquals(0.5f, CapturedPhoto.downscaleFactor(width = 4500, height = 6000), 1e-6f)
    }
}
