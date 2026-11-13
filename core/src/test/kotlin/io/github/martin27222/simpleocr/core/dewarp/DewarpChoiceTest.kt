package io.github.martin27222.simpleocr.core.dewarp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DewarpChoiceTest {
    @Test
    fun clearlyBetterFlattenedPhotoWins() {
        assertTrue(DewarpChoice.preferDewarped(originalScore = 400f, dewarpedScore = 520f))
    }

    @Test
    fun equalOrSlightlyBetterKeepsTheOriginal() {
        assertFalse(DewarpChoice.preferDewarped(originalScore = 400f, dewarpedScore = 400f))
        assertFalse(DewarpChoice.preferDewarped(originalScore = 400f, dewarpedScore = 405f))
    }

    @Test
    fun worseFlattenedPhotoLoses() {
        assertFalse(DewarpChoice.preferDewarped(originalScore = 400f, dewarpedScore = 120f))
        assertFalse(DewarpChoice.preferDewarped(originalScore = 0f, dewarpedScore = 0f))
    }
}
