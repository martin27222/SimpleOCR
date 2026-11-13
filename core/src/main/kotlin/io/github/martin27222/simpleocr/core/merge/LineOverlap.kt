package io.github.martin27222.simpleocr.core.merge

/**
 * Finds the stretch of text that two lines read from different photos have in common.
 *
 * Two photos of the same printed line each show a part of it. The parts can relate in three ways:
 *  - one continues the other: the end of one is the start of the other;
 *  - one lies inside the other;
 *  - both show the same stretch.
 * In every case the shared stretch reaches an end of at least one line on its left side and on
 * its right side. The search finds the stretch of that kind where most characters agree. A few
 * characters may differ, because each photo was read separately and each reading has mistakes.
 */
object LineOverlap {
    /** Shorter repeats happen by chance ("the " ... "the "), so they do not count. */
    const val MIN_LENGTH = 6

    /**
     * The shared stretch: characters [aStart, aEnd) of the first line and [bStart, bEnd) of the second.
     *
     * @property errors how many characters differ inside the shared stretch
     * @property splitA a place inside the shared stretch, near its middle, where both lines agree,
     *   as an index into the first line; [splitB] is the same place in the second line
     */
    data class Overlap(
        val aStart: Int,
        val aEnd: Int,
        val bStart: Int,
        val bEnd: Int,
        val errors: Int,
        val splitA: Int,
        val splitB: Int,
    ) {
        val length: Int get() = maxOf(aEnd - aStart, bEnd - bStart)

        /** Longer and cleaner shared stretches score higher. */
        val score: Int get() = length - 2 * errors
    }

    fun find(a: String, b: String): Overlap? {
        val n = a.length
        val m = b.length
        if (n == 0 || m == 0) return null
        shortContainment(a, b)?.let { return it }
        if (n < MIN_LENGTH || m < MIN_LENGTH) return null

        // score[i][j]: best "agreeing minus differing characters" of a shared stretch that ends
        // with a[i-1] and b[j-1]. It may start at the beginning of either line for free.
        val width = m + 1
        val score = IntArray((n + 1) * width)
        val step = ByteArray((n + 1) * width) // how the best stretch reaches this cell
        for (i in 1..n) {
            val charA = a[i - 1]
            for (j in 1..m) {
                val pair = score[(i - 1) * width + j - 1] + if (charA.equals(b[j - 1], ignoreCase = true)) 1 else -1
                val skipA = score[(i - 1) * width + j] - 1
                val skipB = score[i * width + j - 1] - 1
                val cell = i * width + j
                when {
                    pair >= skipA && pair >= skipB -> { score[cell] = pair; step[cell] = PAIR }
                    skipA >= skipB -> { score[cell] = skipA; step[cell] = SKIP_A }
                    else -> { score[cell] = skipB; step[cell] = SKIP_B }
                }
            }
        }

        // The stretch must end at the end of one of the lines.
        var endA = n
        var endB = m
        var best = score[n * width + m]
        for (j in 1 until m) if (score[n * width + j] > best) { best = score[n * width + j]; endA = n; endB = j }
        for (i in 1 until n) if (score[i * width + m] > best) { best = score[i * width + m]; endA = i; endB = m }
        if (best <= 0) return null

        // Walk back to where the stretch starts, counting differences and noting agreeing places.
        var i = endA
        var j = endB
        var errors = 0
        val agreeA = ArrayList<Int>()
        val agreeB = ArrayList<Int>()
        while (i > 0 && j > 0) {
            when (step[i * width + j]) {
                PAIR -> {
                    i--
                    j--
                    if (a[i].equals(b[j], ignoreCase = true)) { agreeA += i; agreeB += j } else errors++
                }
                SKIP_A -> { i--; errors++ }
                else -> { j--; errors++ }
            }
        }
        val length = maxOf(endA - i, endB - j)
        if (length < MIN_LENGTH || errors > allowedErrors(length) || agreeA.isEmpty()) return null

        // Split place: the agreeing position nearest the middle, preferably a space.
        val middle = (i + endA) / 2
        var split = 0
        var splitDistance = Int.MAX_VALUE
        for (k in agreeA.indices) {
            val distance = kotlin.math.abs(agreeA[k] - middle) * 2 + if (a[agreeA[k]] == ' ') 0 else 5
            if (distance < splitDistance) { splitDistance = distance; split = k }
        }
        return Overlap(i, endA, j, endB, errors, agreeA[split], agreeB[split])
    }

    /**
     * Joins two lines at their shared stretch.
     *
     * Characters near the edge of a photo are the least reliable, because letters there are cut
     * off. So where one line continues the other, the first half of the shared stretch is taken
     * from the line that started earlier and the second half from the line that goes on. Where
     * one line lies inside the other, the outer line is kept.
     *
     * @param trusted which line to believe for the whole shared stretch when one of them was read
     *   much more surely: -1 the first, +1 the second, 0 neither (split in the middle)
     * @param preferA which line to keep when both show exactly the same stretch
     */
    fun join(a: String, b: String, overlap: Overlap, preferA: Boolean, trusted: Int = 0): String {
        val aStartsEarlier = overlap.aStart > 0
        val bStartsEarlier = overlap.bStart > 0
        val aGoesOn = overlap.aEnd < a.length
        val bGoesOn = overlap.bEnd < b.length
        return when {
            aStartsEarlier && bGoesOn -> when {
                trusted < 0 -> a + b.substring(overlap.bEnd)
                trusted > 0 -> a.substring(0, overlap.aStart) + b
                else -> a.substring(0, overlap.splitA) + b.substring(overlap.splitB)
            }
            bStartsEarlier && aGoesOn -> when {
                trusted < 0 -> b.substring(0, overlap.bStart) + a
                trusted > 0 -> b + a.substring(overlap.aEnd)
                else -> b.substring(0, overlap.splitB) + a.substring(overlap.splitA)
            }
            aStartsEarlier || aGoesOn -> a // b lies inside a
            bStartsEarlier || bGoesOn -> b // a lies inside b
            else -> if (preferA) a else b // both show the same stretch
        }
    }

    /** One mistake is tolerated from 7 shared characters on, up to a quarter of the stretch. */
    private fun allowedErrors(length: Int): Int = minOf((length - 4) / 3, length / 4)

    /**
     * Short lines such as a page number or a cut-off word end cannot be compared loosely. They
     * count only if they are exactly the other line, or exactly its beginning or end.
     */
    private fun shortContainment(a: String, b: String): Overlap? {
        val n = a.length
        val m = b.length
        if (minOf(n, m) >= MIN_LENGTH) return null
        if (n == m) {
            return if (n >= 2 && a.equals(b, ignoreCase = true)) Overlap(0, n, 0, m, 0, 0, 0) else null
        }
        if (minOf(n, m) < 3) return null
        return when {
            n < m && b.endsWith(a, ignoreCase = true) -> Overlap(0, n, m - n, m, 0, 0, m - n)
            n < m && b.startsWith(a, ignoreCase = true) -> Overlap(0, n, 0, n, 0, 0, 0)
            n > m && a.endsWith(b, ignoreCase = true) -> Overlap(n - m, n, 0, m, 0, n - m, 0)
            n > m && a.startsWith(b, ignoreCase = true) -> Overlap(0, m, 0, m, 0, 0, 0)
            else -> null
        }
    }

    private const val PAIR: Byte = 0
    private const val SKIP_A: Byte = 1
    private const val SKIP_B: Byte = 2
}
