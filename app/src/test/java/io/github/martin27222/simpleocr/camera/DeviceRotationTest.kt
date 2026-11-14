package io.github.martin27222.simpleocr.camera

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceRotationTest {
    @Test
    fun phoneHeldSidewaysTurnsThePhoto() {
        assertEquals(Surface.ROTATION_270, DeviceRotation.fromOrientation(90, Surface.ROTATION_0))
        assertEquals(Surface.ROTATION_90, DeviceRotation.fromOrientation(270, Surface.ROTATION_0))
        assertEquals(Surface.ROTATION_0, DeviceRotation.fromOrientation(350, Surface.ROTATION_90))
    }

    @Test
    fun nearTheBorderTheWayItWasHeldIsKept() {
        // 50 degrees is past the border at 45, but not yet close enough to sideways.
        assertEquals(Surface.ROTATION_0, DeviceRotation.fromOrientation(50, Surface.ROTATION_0))
        assertEquals(Surface.ROTATION_270, DeviceRotation.fromOrientation(40, Surface.ROTATION_270))
    }

    @Test
    fun symbolsTurnAgainstThePhone() {
        assertEquals(-90f, DeviceRotation.symbolAngle(Surface.ROTATION_270), 0f)
        assertEquals(90f, DeviceRotation.symbolAngle(Surface.ROTATION_90), 0f)
        assertEquals(0f, DeviceRotation.symbolAngle(Surface.ROTATION_0), 0f)
    }
}
