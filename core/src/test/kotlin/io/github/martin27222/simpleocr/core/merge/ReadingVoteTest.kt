package io.github.martin27222.simpleocr.core.merge

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingVoteTest {
    private fun line(text: String, confidence: Float = 0.9f, unsure: Map<String, Float> = emptyMap()) =
        text.split(" ").map { ReadWord(it, unsure[it] ?: confidence) }

    private fun text(lines: List<List<ReadWord>>) = lines.map { line -> line.joinToString(" ") { it.text } }

    @Test
    fun surerWordWinsBetweenTwoReadings() {
        val original = listOf(line("helbs to keep the couscous trom clumping", unsure = mapOf("helbs" to 0.4f, "trom" to 0.5f)))
        val flattened = listOf(line("helps to keep the couscous from clumping"))
        assertEquals(listOf("helps to keep the couscous from clumping"), text(ReadingVote.vote(original, listOf(flattened))))
    }

    @Test
    fun baseWordStaysWhenItIsSurer() {
        val original = listOf(line("boiling water over the couscous"))
        val flattened = listOf(line("hoiling water over the couscous", unsure = mapOf("hoiling" to 0.3f)))
        assertEquals(listOf("boiling water over the couscous"), text(ReadingVote.vote(original, listOf(flattened))))
    }

    @Test
    fun majorityBeatsASingleConfidentReading() {
        val base = listOf(line("rest for S minutes before removing", unsure = mapOf("S" to 0.95f)))
        val second = listOf(line("rest for 5 minutes before removing", confidence = 0.7f))
        val third = listOf(line("rest for 5 minutes before removing", confidence = 0.7f))
        assertEquals(listOf("rest for 5 minutes before removing"), text(ReadingVote.vote(base, listOf(second, third))))
    }

    @Test
    fun linesArePairedByTextNotByPosition() {
        val base = listOf(line("mixing bowl and drizzle a little"), line("olive oil ovcr the top", unsure = mapOf("ovcr" to 0.2f)))
        val other = listOf(line("a heading only the other reading found"), line("olive oil over the top"), line("mixing bowl and drizzle a little"))
        assertEquals(listOf("mixing bowl and drizzle a little", "olive oil over the top"), text(ReadingVote.vote(base, listOf(other))))
    }

    @Test
    fun extraOrMissingWordsDoNotShiftThePairing() {
        val base = listOf(line("Cover with cling flm and leave to rest", unsure = mapOf("flm" to 0.3f)))
        val other = listOf(line("Cover with with cling film and leave rest"))
        assertEquals(listOf("Cover with cling film and leave to rest"), text(ReadingVote.vote(base, listOf(other))))
    }

    @Test
    fun unrelatedLinesAreLeftAlone() {
        val base = listOf(line("juice of 2 lemons", confidence = 0.2f))
        val other = listOf(line("salt and pepper to taste"))
        assertEquals(listOf("juice of 2 lemons"), text(ReadingVote.vote(base, listOf(other))))
    }
}
