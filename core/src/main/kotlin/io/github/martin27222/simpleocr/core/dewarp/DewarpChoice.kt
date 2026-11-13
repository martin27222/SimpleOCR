package io.github.martin27222.simpleocr.core.dewarp

/** Decides whether the flattened photo or the original photo is shown. */
object DewarpChoice {
    /**
     * The flattened photo has to read clearly better than the original to win, because the
     * original is the sharper image when the page was flat to begin with.
     */
    const val REQUIRED_GAIN = 1.02f

    fun preferDewarped(originalScore: Float, dewarpedScore: Float): Boolean =
        dewarpedScore > originalScore * REQUIRED_GAIN
}
