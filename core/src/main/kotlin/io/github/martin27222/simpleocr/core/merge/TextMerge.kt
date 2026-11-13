package io.github.martin27222.simpleocr.core.merge

/** One recognized line of text and how sure the recognizer was about it (0..1). */
data class ReadLine(val text: String, val confidence: Float = 1f)

/**
 * Joins the text of several photos of the same label into one text.
 *
 * Each photo is a list of lines from top to bottom. Photos overlap: a line continues from one
 * photo into the next, and the shared part appears in both. The merge
 *  1. pairs up lines of two photos so that the total length of shared text is as large as
 *     possible, keeping the top-to-bottom order of both photos;
 *  2. glues each pair together at its shared part, which is kept once;
 *  3. keeps lines without a partner unchanged, in order.
 *
 * It also covers photos taken one below the other: a line that is fully visible in both photos
 * overlaps completely and is kept once.
 */
object TextMerge {
    fun merge(photos: List<List<ReadLine>>): List<ReadLine> {
        var merged = emptyList<ReadLine>()
        for (photo in photos) {
            val lines = photo.filter { it.text.isNotBlank() }
            merged = when {
                merged.isEmpty() -> lines
                lines.isEmpty() -> merged
                else -> {
                    // The next photo usually continues to the right. If the object was turned
                    // the other way, it continues to the left; the better fit decides.
                    val forward = join(merged, lines)
                    val backward = join(lines, merged)
                    if (backward.score > forward.score) backward.lines else forward.lines
                }
            }
        }
        return merged
    }

    private class Joined(val lines: List<ReadLine>, val score: Int)

    /** Joins two photos, assuming lines of [first] continue in [second]. */
    private fun join(first: List<ReadLine>, second: List<ReadLine>): Joined {
        val rows = first.size
        val cols = second.size
        val overlaps = Array(rows) { i -> Array(cols) { j -> LineOverlap.find(first[i].text, second[j].text) } }

        // best[i][j]: largest total overlap score using the first i lines of one photo and the
        // first j lines of the other. A line is either paired with one line or left alone.
        val best = Array(rows + 1) { IntArray(cols + 1) }
        for (i in 1..rows) {
            for (j in 1..cols) {
                var value = maxOf(best[i - 1][j], best[i][j - 1])
                val overlap = overlaps[i - 1][j - 1]
                if (overlap != null) value = maxOf(value, best[i - 1][j - 1] + overlap.score)
                best[i][j] = value
            }
        }

        // Walk back from the end to read off the pairing. Collected bottom-up, reversed at the end.
        val lines = ArrayList<ReadLine>(rows + cols)
        var i = rows
        var j = cols
        while (i > 0 || j > 0) {
            val overlap = if (i > 0 && j > 0) overlaps[i - 1][j - 1] else null
            when {
                overlap != null && best[i][j] == best[i - 1][j - 1] + overlap.score -> {
                    lines += glue(first[i - 1], second[j - 1], overlap)
                    i--
                    j--
                }
                // Unpaired lines of the second photo go below unpaired lines of the first.
                j > 0 && (i == 0 || best[i][j] == best[i][j - 1]) -> lines += second[--j]
                else -> lines += first[--i]
            }
        }
        lines.reverse()
        return Joined(lines, best[rows][cols])
    }

    /** The shared part is taken from the line the recognizer was surer about. */
    private fun glue(left: ReadLine, right: ReadLine, overlap: LineOverlap.Overlap): ReadLine {
        val text = if (right.confidence >= left.confidence) {
            left.text.substring(0, overlap.leftKeep) + right.text
        } else {
            left.text + right.text.substring(overlap.rightSkip)
        }
        return ReadLine(text, minOf(left.confidence, right.confidence))
    }
}
