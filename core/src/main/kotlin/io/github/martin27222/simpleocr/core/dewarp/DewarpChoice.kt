package io.github.martin27222.simpleocr.core.dewarp

/** Decides whether the flattened photo or the original photo is shown. */
object DewarpChoice {
    /**
     * The flattened photo is shown unless it reads clearly worse than the original: on a
     * flattened page the areas of text are found more reliably, even where the words read the
     * same. Only a flattening that went wrong falls back to the original.
     */
    const val REQUIRED_SHARE = 0.9f

    fun preferDewarped(originalScore: Float, dewarpedScore: Float): Boolean =
        dewarpedScore > 0f && dewarpedScore >= originalScore * REQUIRED_SHARE
}
