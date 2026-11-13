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

    @Test
    fun fourPhotosOfAFlatPageGiveEveryLineOnce() {
        val merged = TextMerge.merge(
            listOf(
                lines("Pour the uncooked couscous into a large mixing t", "olive oil over the top. Work the olive oil onto the"),
                lines("ouscous into a large mixing bowl and drizzle a little", ". Work the olive oil onto the grains by hand as this"),
                lines("olive oil over the top. Work the olive oil onto the", "helps to keep the couscous from clumping togeth", "34"),
                lines("the olive oil onto the grains by hand as this", "iscous from clumping together when it is cooked. Pour", "34"),
            ),
        )
        assertEquals(
            listOf(
                "Pour the uncooked couscous into a large mixing bowl and drizzle a little",
                "olive oil over the top. Work the olive oil onto the grains by hand as this",
                "helps to keep the couscous from clumping together when it is cooked. Pour",
                "34",
            ),
            merged.map { it.text },
        )
    }

    @Test
    fun badlyReadLinesAreLeftOut() {
        val merged = TextMerge.merge(
            listOf(
                lines("Cover with cling film and leave to rest"),
                listOf(ReadLine("ee eee SV ale +n cather", 0.39f), ReadLine("leave to rest for 5 minutes", 0.95f)),
            ),
        )
        assertEquals(listOf("Cover with cling film and leave to rest for 5 minutes"), merged.map { it.text })
    }

    @Test
    fun clearlySurerLineDecidesTheSharedStretch() {
        val merged = TextMerge.merge(
            listOf(
                listOf(ReadLine("olive oil over the top. Work the olive oil onto", 0.95f)),
                listOf(ReadLine("the top. WOrk the Olive Ol! onto the grains by hand", 0.70f)),
            ),
        )
        assertEquals(listOf("olive oil over the top. Work the olive oil onto the grains by hand"), merged.map { it.text })
    }
}
