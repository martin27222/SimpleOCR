package io.github.martin27222.simpleocr.core.merge

import org.junit.Assert.assertEquals
import org.junit.Test

class TextMergeTest {
    private fun lines(vararg text: String) = text.map { ReadLine(it) }
    private fun merged(vararg photos: List<ReadLine>) = TextMerge.merge(photos.toList()).map { it.text }

    @Test
    fun gluesTwoPhotosOfABottleLabel() {
        val result = merged(
            lines("Aqua, Alcohol Denat., Parf", "Linalool, Citral, Gera", "Hergestellt in Deu"),
            lines("Denat., Parfum, Limonene", "Citral, Geraniol, Citronellol", "llt in Deutschland"),
        )
        assertEquals(
            listOf(
                "Aqua, Alcohol Denat., Parfum, Limonene",
                "Linalool, Citral, Geraniol, Citronellol",
                "Hergestellt in Deutschland",
            ),
            result,
        )
    }

    @Test
    fun chainsThreePhotos() {
        val result = merged(
            lines("Aqua, Alcohol Den"),
            lines("cohol Denat., Parfum, Lim"),
            lines("Parfum, Limonene, Linalool"),
        )
        assertEquals(listOf("Aqua, Alcohol Denat., Parfum, Limonene, Linalool"), result)
    }

    @Test
    fun orderOfThePhotosDoesNotMatter() {
        val result = merged(
            lines("Denat., Parfum, Limonene"),
            lines("Aqua, Alcohol Denat., Parf"),
        )
        assertEquals(listOf("Aqua, Alcohol Denat., Parfum, Limonene"), result)
    }

    @Test
    fun keepsLinesThatOnlyOnePhotoShows() {
        val result = merged(
            lines("INGREDIENTS", "Aqua, Alcohol Denat., Parf"),
            lines("Denat., Parfum, Limonene", "50 ml"),
        )
        assertEquals(listOf("INGREDIENTS", "Aqua, Alcohol Denat., Parfum, Limonene", "50 ml"), result)
    }

    @Test
    fun photosTakenOneBelowTheOtherKeepSharedLinesOnce() {
        val result = merged(
            lines("Erste Zeile oben", "Zweite Zeile in der Mitte", "Dritte Zeile in der Mitte"),
            lines("Zweite Zeile in der Mitte", "Dritte Zeile in der Mitte", "Vierte Zeile unten"),
        )
        assertEquals(
            listOf("Erste Zeile oben", "Zweite Zeile in der Mitte", "Dritte Zeile in der Mitte", "Vierte Zeile unten"),
            result,
        )
    }

    @Test
    fun theSurerReadingOfTheSharedPartWins() {
        val result = TextMerge.merge(
            listOf(
                listOf(ReadLine("Aqua, Alcohol Denat., Parf", confidence = 0.9f)),
                listOf(ReadLine("Dcnat., Parfum, Limonene", confidence = 0.6f)),
            ),
        )
        assertEquals("Aqua, Alcohol Denat., Parfum, Limonene", result.single().text)
    }

    @Test
    fun photosWithoutSharedTextAreSimplyPutOneAfterTheOther() {
        val result = merged(lines("Vorderseite der Packung"), lines("Rueckseite mit Hinweisen"))
        assertEquals(listOf("Vorderseite der Packung", "Rueckseite mit Hinweisen"), result)
    }
}
