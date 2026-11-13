package io.github.martin27222.simpleocr.core.merge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LineOverlapTest {
    private fun joined(a: String, b: String, preferA: Boolean = true): String? =
        LineOverlap.find(a, b)?.let { LineOverlap.join(a, b, it, preferA) }

    @Test
    fun lineContinuesInTheNextPhoto() {
        assertEquals(
            "Aqua, Alcohol Denat., Parfum, Limonene",
            joined("Aqua, Alcohol Denat., Parf", "Denat., Parfum, Limonene"),
        )
    }

    @Test
    fun orderOfTheTwoLinesDoesNotMatter() {
        assertEquals(
            "Aqua, Alcohol Denat., Parfum, Limonene",
            joined("Denat., Parfum, Limonene", "Aqua, Alcohol Denat., Parf"),
        )
    }

    @Test
    fun cutOffLettersAtThePhotoEdgesAreDropped() {
        // "mixing t" ends with a cut-off "b"; "ouscous" starts with a cut-off "c".
        assertEquals(
            "Pour the uncooked couscous into a large mixing bowl and drizzle a little",
            joined(
                "Pour the uncooked couscous into a large mixing t",
                "ouscous into a large mixing bowl and drizzle a little",
            ),
        )
    }

    @Test
    fun lineInsideAnotherLineIsAbsorbed() {
        assertEquals(
            "helps to keep the couscous from clumping together",
            joined("helps to keep the couscous from clumping together", "s to keep the couscous trom"),
        )
        assertEquals(
            "helps to keep the couscous from clumping together",
            joined("hing the couscous from cl", "helps to keep the couscous from clumping together"),
        )
    }

    @Test
    fun sameStretchKeepsThePreferredReading() {
        assertEquals("salt and pepper", joined("salt and pepper", "salt and pepper", preferA = true))
        assertEquals("salt and pcpper", joined("salt and pepper", "salt and pcpper", preferA = false))
    }

    @Test
    fun readingMistakesInTheSharedStretchAreTolerated() {
        assertEquals(
            "boiling water over the couscous until all the grains are just submerged.",
            joined("boiling water over the couscous until all the grair", "the couscous umtil all the grains are just submerged."),
        )
    }

    @Test
    fun shortLinesCountOnlyWhenExact() {
        assertEquals("34", joined("34", "34"))
        assertEquals("mixing bowl.", joined("mixing bowl.", "wl."))
        assertNull(LineOverlap.find("34", "35"))
        assertNull(LineOverlap.find("mixing bowl.", "wI."))
    }

    @Test
    fun unrelatedLinesHaveNoOverlap() {
        assertNull(LineOverlap.find("Cover with cling film and leave", "Chop the spring onions, celery"))
        assertNull(LineOverlap.find("add them to the", "the grains by hand"))
        assertNull(LineOverlap.find("", "anything"))
    }
}
