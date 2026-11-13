package io.github.martin27222.simpleocr.dewarp

import android.graphics.Bitmap

/** Flattens a photo of a curved or tilted page. Returns a new bitmap of the same size. */
interface Dewarper {
    suspend fun dewarp(bitmap: Bitmap): Bitmap
}
