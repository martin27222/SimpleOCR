package io.github.martin27222.simpleocr.core.dewarp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DewarpChoiceTest {
    @Test
    fun betterFlattenedPhotoWins() {
        assertTrue(DewarpChoice.preferDewarped(originalScore = 400f, dewarpedScore = 520f))
    }

    @Test
    fun equalOrSlightlyWorseFlattenedPhotoStillWins() {
        assertTrue(DewarpChoice.preferDewarped(originalScore = 400f, dewarpedScore = 400f))
        assertTrue(DewarpChoice.preferDewarped(originalScore = 400f, dewarpedScore = 370f))
    }

    @Test
    fun clearlyWorseFlattenedPhotoLoses() {
        assertFalse(DewarpChoice.preferDewarped(originalScore = 400f, dewarpedScore = 300f))
        assertFalse(DewarpChoice.preferDewarped(originalScore = 0f, dewarpedScore = 0f))
    }
}
