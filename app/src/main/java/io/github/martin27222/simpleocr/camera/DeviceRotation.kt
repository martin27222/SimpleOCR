package io.github.martin27222.simpleocr.camera

import android.view.Surface

/**
 * Turns the tilt reported by the motion sensor into the way the phone is held. The screen itself
 * stays upright while the camera shows; photos and the camera's symbols follow the phone, so it
 * works the same whether the system's auto-rotate is on or off.
 */
object DeviceRotation {
    /**
     * The rotation in the sense of [Surface.ROTATION_0] and friends for a sensor [orientation]
     * in degrees (0 upright, 90 left side up, 180 upside down, 270 right side up). Near the
     * border between two ways of holding the phone, [current] is kept, so the symbols do not
     * flip back and forth.
     */
    fun fromOrientation(orientation: Int, current: Int): Int {
        val degrees = ((orientation % 360) + 360) % 360
        val candidate = when (degrees) {
            in 45 until 135 -> Surface.ROTATION_270
            in 135 until 225 -> Surface.ROTATION_180
            in 225 until 315 -> Surface.ROTATION_90
            else -> Surface.ROTATION_0
        }
        if (candidate == current) return current
        val center = when (candidate) {
            Surface.ROTATION_270 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 270
            else -> 0
        }
        val away = minOf((degrees - center + 360) % 360, (center - degrees + 360) % 360)
        return if (away <= SWITCH_WITHIN) candidate else current
    }

    /** How far the camera's symbols turn so that they stand upright for the user, in degrees. */
    fun symbolAngle(rotation: Int): Float = when (rotation) {
        Surface.ROTATION_90 -> 90f
        Surface.ROTATION_180 -> 180f
        Surface.ROTATION_270 -> -90f
        else -> 0f
    }

    /** The phone must be this close (degrees) to a new way of holding it before it counts. */
    private const val SWITCH_WITHIN = 30
}
