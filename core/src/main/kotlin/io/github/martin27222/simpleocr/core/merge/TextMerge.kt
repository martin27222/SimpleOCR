package io.github.martin27222.simpleocr.core.merge

/** One recognized line of text and how sure the recognizer was about it (0..1). */
data class ReadLine(val text: String, val confidence: Float = 1f)

/**
 * Joins the text of several overlapping photos of the same object into one text.
 *
 * Each photo is a list of lines from top to bottom. A printed line usually shows up in several
 * photos, each holding a part of it. Photo by photo, the merge
 *  1. pairs lines of the text so far with lines of the next photo, so that the paired lines share
 *     as much text as possible and the top-to-bottom order of both is kept;
 *  2. joins each pair at its shared stretch, which is kept once (see [LineOverlap]);
 *  3. keeps lines without a partner unchanged, in order.
 *
 * Lines the recognizer was very unsure about are left out.
 *
 * This covers photos taken side by side (round objects), one below the other, or both.
 */
object TextMerge {
    fun merge(photos: List<List<ReadLine>>): List<ReadLine> {
        var merged = emptyList<ReadLine>()
        for (photo in photos) {
            val lines = photo
                .map { it.copy(text = it.text.trim()) }
                .filter { it.text.isNotEmpty() && it.confidence >= MIN_CONFIDENCE }
            merged = when {
                merged.isEmpty() -> lines
                lines.isEmpty() -> merged
                else -> join(merged, lines)
            }
        }
        return merged
    }

    private fun join(first: List<ReadLine>, second: List<ReadLine>): List<ReadLine> {
        val rows = first.size
        val cols = second.size
        val overlaps = Array(rows) { i -> Array(cols) { j -> LineOverlap.find(first[i].text, second[j].text) } }

        // best[i][j]: largest total overlap score using the first i lines of the text so far and
        // the first j lines of the new photo. A line is either paired with one line or left alone.
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
                // Unpaired lines of the new photo go below unpaired lines of the text so far.
                j > 0 && (i == 0 || best[i][j] == best[i][j - 1]) -> lines += second[--j]
                else -> lines += first[--i]
            }
        }
        lines.reverse()
        return lines
    }

    private fun glue(a: ReadLine, b: ReadLine, overlap: LineOverlap.Overlap): ReadLine {
        val trusted = when {
            a.confidence - b.confidence > CLEARLY_SURER -> -1
            b.confidence - a.confidence > CLEARLY_SURER -> 1
            else -> 0
        }
        return ReadLine(
            text = LineOverlap.join(a.text, b.text, overlap, preferA = a.confidence >= b.confidence, trusted = trusted),
            confidence = maxOf(a.confidence, b.confidence),
        )
    }

    /** Lines the recognizer was this unsure about are mostly noise, such as half-visible lines. */
    private const val MIN_CONFIDENCE = 0.5f

    /** From this difference in confidence on, the surer line decides the whole shared stretch. */
    private const val CLEARLY_SURER = 0.15f
}
