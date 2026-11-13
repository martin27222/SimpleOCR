package io.github.martin27222.simpleocr.core.merge

/**
 * Finds where the end of one line of text repeats at the start of another.
 *
 * Two photos of the same line share a stretch of text: the right part of the first photo shows
 * the same characters as the left part of the second. That stretch is the longest end of [left]
 * that is (nearly) equal to a beginning of [right]. "Nearly" allows a few characters to differ,
 * because both photos were read separately and each reading can contain mistakes.
 */
object LineOverlap {
    /** Shorter repeats happen by chance ("en " ... "en "), so they do not count. */
    const val MIN_LENGTH = 5

    /** One reading mistake is tolerated per this many overlapping characters. */
    const val CHARACTERS_PER_ALLOWED_ERROR = 6

    /**
     * @property leftKeep how many characters of the left line come before the repeated stretch
     * @property rightSkip how many characters at the start of the right line are the repeated stretch
     * @property errors how many characters differ inside the repeated stretch
     */
    data class Overlap(val leftKeep: Int, val rightSkip: Int, val errors: Int) {
        /** Longer and cleaner repeats score higher. */
        val score: Int get() = rightSkip - 2 * errors
    }

    fun find(left: String, right: String): Overlap? {
        val n = left.length
        val m = right.length
        if (n < MIN_LENGTH || m < MIN_LENGTH) return null

        // Edit-distance table, one row per character of the left line. cost[j] is the fewest
        // changes needed to turn the first j characters of the right line into text that ends
        // exactly where the left row ends; start[j] is where that text begins in the left line.
        // Starting anywhere in the left line is free (cost[0] = 0 in every row).
        var cost = IntArray(m + 1) { it }
        var start = IntArray(m + 1)
        var nextCost = IntArray(m + 1)
        var nextStart = IntArray(m + 1)
        for (i in 1..n) {
            nextCost[0] = 0
            nextStart[0] = i
            val leftChar = left[i - 1]
            for (j in 1..m) {
                val substitute = cost[j - 1] + if (leftChar.equals(right[j - 1], ignoreCase = true)) 0 else 1
                val skipLeft = cost[j] + 1
                val skipRight = nextCost[j - 1] + 1
                when {
                    substitute <= skipLeft && substitute <= skipRight -> {
                        nextCost[j] = substitute
                        nextStart[j] = start[j - 1]
                    }
                    skipLeft <= skipRight -> {
                        nextCost[j] = skipLeft
                        nextStart[j] = start[j]
                    }
                    else -> {
                        nextCost[j] = skipRight
                        nextStart[j] = nextStart[j - 1]
                    }
                }
            }
            cost = nextCost.also { nextCost = cost }
            start = nextStart.also { nextStart = start }
        }

        var best: Overlap? = null
        for (j in MIN_LENGTH..m) {
            val errors = cost[j]
            val leftLength = n - start[j]
            if (errors > j / CHARACTERS_PER_ALLOWED_ERROR || leftLength < MIN_LENGTH) continue
            val candidate = Overlap(leftKeep = start[j], rightSkip = j, errors = errors)
            if (best == null || candidate.score >= best.score) best = candidate
        }
        return best
    }
}
