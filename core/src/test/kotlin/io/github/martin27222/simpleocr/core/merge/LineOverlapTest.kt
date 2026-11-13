package io.github.martin27222.simpleocr.core.merge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LineOverlapTest {
    @Test
    fun findsAnExactRepeat() {
        val overlap = LineOverlap.find("Aqua, Alcohol Denat., Parf", "Denat., Parfum, Limonene")!!
        assertEquals("Aqua, Alcohol ", "Aqua, Alcohol Denat., Parf".substring(0, overlap.leftKeep))
        assertEquals("Denat., Parf", "Denat., Parfum, Limonene".substring(0, overlap.rightSkip))
        assertEquals(0, overlap.errors)
    }

    @Test
    fun toleratesAReadingMistakeInsideTheRepeat() {
        // "Limonene" was read as "Lim0nene" in the second photo.
        val overlap = LineOverlap.find("Parfum, Limonene, Lina", "Lim0nene, Linalool, Citral")!!
        assertEquals("Parfum, ", "Parfum, Limonene, Lina".substring(0, overlap.leftKeep))
        assertEquals(1, overlap.errors)
    }

    @Test
    fun aLineSeenCompletelyInBothPhotosOverlapsCompletely() {
        val overlap = LineOverlap.find("Zutaten: Wasser, Zucker", "Zutaten: Wasser, Zucker")!!
        assertEquals(0, overlap.leftKeep)
        assertEquals("Zutaten: Wasser, Zucker".length, overlap.rightSkip)
    }

    @Test
    fun ignoresShortChanceRepeats() {
        assertNull(LineOverlap.find("mit den", "den Zutaten mischen"))
    }

    @Test
    fun unrelatedLinesDoNotOverlap() {
        assertNull(LineOverlap.find("Aqua, Alcohol Denat.", "Mindestens haltbar bis Ende"))
    }
}
