package io.github.martin27222.simpleocr.settings

/**
 * The three quality levels the camera button switches between. What each level does can be
 * changed in the settings, see [QualityLevel].
 *
 * @property label the short name shown on the camera button
 * @property defaults what the level does unless the user changed it
 */
enum class Quality(val label: String, val defaults: QualityLevel) {
    Low("LQ", QualityLevel(maxSide = 2000, passes = 2)),
    Standard("SQ", QualityLevel(maxSide = 3000, passes = 2)),
    High("HQ", QualityLevel(maxSide = 3000, passes = 3));

    /** The level the camera button switches to next. */
    fun next(): Quality = entries[(ordinal + 1) % entries.size]
}

/**
 * How much time reading a photo may take.
 *
 * @property maxSide the longer side of the photo that is read, in pixels. Fewer pixels read
 *   faster, more pixels help with small print.
 * @property passes how often the photo is read:
 *   1. as taken;
 *   2. also flattened with the help of the text found in pass 1;
 *   3. also flattened by the UVDoc network.
 *   The text of pass 1 shows at once; later passes run in the background and improve it.
 */
data class QualityLevel(val maxSide: Int, val passes: Int) {
    /** At full size the camera also takes more time for a cleaner photo. */
    val carefulCapture: Boolean get() = maxSide >= FULL_SIDE

    companion object {
        /** A 12 megapixel photo (4080 x 3072) fits without shrinking. */
        const val FULL_SIDE = 4096

        /** The photo sizes offered in the settings. */
        val SIDES = listOf(2000, 3000, FULL_SIDE)

        /** The numbers of passes offered in the settings. */
        val PASSES = listOf(1, 2, 3)
    }
}
