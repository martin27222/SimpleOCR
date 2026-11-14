package io.github.martin27222.simpleocr.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TextFormatterTest {
    private val page = listOf(
        listOf("Die Zusammen-", "fassung zeigt Vor- und", "Nachteile der Kosten-", "Nutzen-Rechnung."),
        listOf("Zweiter Absatz."),
    )

    @Test
    fun flowingTextWithRepairedWords() {
        assertEquals(
            "Die Zusammenfassung zeigt Vor- und Nachteile der Kosten-Nutzen-Rechnung.\n\nZweiter Absatz.",
            TextFormatter.format(page, flowingText = true, joinHyphenatedWords = true),
        )
    }

    @Test
    fun lineBreaksAsInThePhoto() {
        assertEquals(
            "Die Zusammen-\nfassung zeigt Vor- und\nNachteile der Kosten-\nNutzen-Rechnung.\n\nZweiter Absatz.",
            TextFormatter.format(page, flowingText = false, joinHyphenatedWords = false),
        )
    }

    @Test
    fun lineBreaksKeptButSplitWordsRepaired() {
        assertEquals(
            "Die Zusammenfassung\nzeigt Vor- und\nNachteile der Kosten-Nutzen-Rechnung.\n\nZweiter Absatz.",
            TextFormatter.format(page, flowingText = false, joinHyphenatedWords = true),
        )
    }

    @Test
    fun blankLinesAndEmptyParagraphsDisappear() {
        assertEquals(
            "Eins Zwei",
            TextFormatter.format(listOf(listOf(" Eins ", "  ", "Zwei"), emptyList()), flowingText = true, joinHyphenatedWords = true),
        )
    }

    @Test
    fun aDashAfterASpaceIsNotASplitWord() {
        assertEquals(
            "Preis - siehe Etikett",
            TextFormatter.format(listOf(listOf("Preis -", "siehe Etikett")), flowingText = true, joinHyphenatedWords = true),
        )
    }
}
