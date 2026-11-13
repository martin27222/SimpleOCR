package io.github.martin27222.simpleocr.core.merge

import io.github.martin27222.simpleocr.core.text.EditDistance
import kotlin.math.abs

/** One recognized word and how sure the recognizer was about it (0..1). */
data class ReadWord(val text: String, val confidence: Float)

/**
 * Improves one reading of a photo with other readings of the same photo, for example the photo as
 * taken and its flattened version. Different versions of the image tend to produce different
 * mistakes, so comparing them word by word removes many of them.
 *
 * For every line of the base reading:
 *  1. find the line with nearly the same text in each other reading;
 *  2. pair up the words of both lines in order;
 *  3. where the paired words differ, the spelling most readings agree on wins; if there is no
 *     majority, the word the recognizer was surest about wins.
 *
 * Words are only replaced, never added or removed, so the base reading keeps its layout.
 */
object ReadingVote {
    fun vote(base: List<List<ReadWord>>, others: List<List<List<ReadWord>>>): List<List<ReadWord>> {
        if (others.isEmpty()) return base
        val otherTexts = others.map { reading -> reading.map(::textOf) }
        return base.map { line ->
            val text = textOf(line)
            if (text.length < MIN_LINE_LENGTH) return@map line
            val partners = others.indices.mapNotNull { index ->
                matchingLine(text, otherTexts[index])?.let { others[index][it] }
            }
            if (partners.isEmpty()) line else voteLine(line, partners)
        }
    }

    private fun textOf(line: List<ReadWord>): String = line.joinToString(" ") { it.text }

    /** Index of the line in [texts] that is nearly the same as [text], or null. */
    private fun matchingLine(text: String, texts: List<String>): Int? {
        var best: Int? = null
        var bestDifference = MAX_LINE_DIFFERENCE
        for ((index, candidate) in texts.withIndex()) {
            val longest = maxOf(text.length, candidate.length)
            if (abs(text.length - candidate.length) > MAX_LINE_DIFFERENCE * longest) continue
            val difference = EditDistance.relative(text, candidate)
            if (difference < bestDifference) {
                bestDifference = difference
                best = index
            }
        }
        return best
    }

    private fun voteLine(base: List<ReadWord>, partners: List<List<ReadWord>>): List<ReadWord> {
        val candidates = base.map { mutableListOf(it) }
        for (partner in partners) {
            pairWords(base, partner).forEachIndexed { index, word -> word?.let { candidates[index] += it } }
        }
        return candidates.map(::winner)
    }

    private fun winner(candidates: List<ReadWord>): ReadWord {
        val groups = candidates.groupBy { it.text }
        if (groups.size == 1) return candidates.first()
        val largest = groups.values.maxOf { it.size }
        val leaders = groups.values.filter { it.size == largest }
        // A clear majority wins; otherwise the single surest word does.
        val pool = if (leaders.size == 1 && largest >= 2) leaders.first() else candidates
        return pool.maxBy { it.confidence }
    }

    /**
     * For every base word the word of [other] at the same place, or null if there is none.
     * Standard sequence alignment: pairing two words costs how different they are, leaving a word
     * without a partner costs [GAP_COST].
     */
    private fun pairWords(base: List<ReadWord>, other: List<ReadWord>): List<ReadWord?> {
        val rows = base.size
        val columns = other.size
        val cost = Array(rows + 1) { FloatArray(columns + 1) }
        for (i in 1..rows) cost[i][0] = i * GAP_COST
        for (j in 1..columns) cost[0][j] = j * GAP_COST
        for (i in 1..rows) {
            for (j in 1..columns) {
                val pair = cost[i - 1][j - 1] + pairCost(base[i - 1].text, other[j - 1].text)
                cost[i][j] = minOf(pair, cost[i - 1][j] + GAP_COST, cost[i][j - 1] + GAP_COST)
            }
        }
        val partners = arrayOfNulls<ReadWord>(rows)
        var i = rows
        var j = columns
        while (i > 0 && j > 0) {
            val difference = pairCost(base[i - 1].text, other[j - 1].text)
            when (cost[i][j]) {
                cost[i - 1][j - 1] + difference -> {
                    if (sameWord(base[i - 1].text, other[j - 1].text)) partners[i - 1] = other[j - 1]
                    i--
                    j--
                }
                cost[i - 1][j] + GAP_COST -> i--
                else -> j--
            }
        }
        return partners.toList()
    }

    private fun pairCost(a: String, b: String): Float = if (a == b) 0f else EditDistance.relative(a, b)

    /** Two spellings count as the same word if at most one character or at most half of them differs. */
    private fun sameWord(a: String, b: String): Boolean =
        EditDistance.between(a, b) <= 1 || EditDistance.relative(a, b) <= MAX_WORD_DIFFERENCE

    private const val MIN_LINE_LENGTH = 4
    private const val MAX_LINE_DIFFERENCE = 0.3f
    private const val MAX_WORD_DIFFERENCE = 0.5f
    private const val GAP_COST = 0.6f
}
