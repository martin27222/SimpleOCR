package io.github.martin27222.simpleocr.core.text

/** Where a recognized line sits in the photo, in pixels. */
data class LineBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerY: Float get() = (top + bottom) / 2f
    val height: Float get() = bottom - top
}

/**
 * One connected area of text, such as a column or a side note.
 *
 * @property paragraphs the paragraphs of the area from top to bottom; each one lists its lines
 *   in reading order, as indexes into the lines handed to [PageLayout.frames]
 */
data class Frame(val paragraphs: List<List<Int>>) {
    val lines: List<Int> get() = paragraphs.flatten()
}

/**
 * Works out the layout of a page from the boxes of its text lines.
 *
 * A page is cut the way a reader sees it:
 *  - a tall empty strip that no line crosses separates two columns, read left one first;
 *  - a wide empty band between lines separates two paragraphs;
 *  - a very wide empty band separates two areas of text, e.g. the body and the page number.
 *
 * Columns only show once the lines above and below them, such as a heading running across the
 * whole page, are set aside. So the page is first cut into bands of lines, and then the longest
 * runs of bands that share a column gap are split into their columns.
 */
object PageLayout {
    fun frames(lines: List<LineBox>): List<Frame> {
        if (lines.isEmpty()) return emptyList()
        val lineHeight = median(lines.map { it.height })
        return Cutter(lines, lineHeight).split(lines.indices.toList()).map { frame -> withOwnParagraphs(lines, frame) }
    }

    /**
     * Cuts a frame into paragraphs again, measured by its own lines. A side note in a smaller or
     * italic type has other line heights and gaps than the main text; measured by the main text,
     * every one of its lines would look like a paragraph of its own.
     */
    private fun withOwnParagraphs(lines: List<LineBox>, frame: Frame): Frame {
        val own = frame.lines
        if (own.size < 2) return frame
        val cutter = Cutter(lines, median(own.map { lines[it].height }))
        return Frame(cutter.paragraphs(own))
    }

    private class Cutter(private val lines: List<LineBox>, private val lineHeight: Float) {
        fun split(region: List<Int>): List<Frame> {
            val bands = bands(region)
            val frames = ArrayList<Frame>()
            var paragraphs = ArrayList<List<Int>>()
            fun closeFrame() {
                if (paragraphs.isNotEmpty()) frames += Frame(paragraphs)
                paragraphs = ArrayList()
            }
            var index = 0
            while (index < bands.size) {
                // The longest run of bands starting here that has a column gap.
                var columns: Pair<List<Int>, List<Int>>? = null
                var last = bands.size - 1
                while (last >= index) {
                    columns = columns(bands.subList(index, last + 1).flatMap { it.lines })
                    if (columns != null) break
                    last--
                }
                if (columns != null) {
                    closeFrame()
                    frames += split(columns.first)
                    frames += split(columns.second)
                    index = last + 1
                } else {
                    val band = bands[index]
                    if (band.gapAbove > band.usualGap + FRAME_GAP * lineHeight) closeFrame()
                    paragraphs += inReadingOrder(band.lines)
                    index++
                }
            }
            closeFrame()
            return frames
        }

        private class Band(val lines: List<Int>, val gapAbove: Float, val usualGap: Float)

        /** The paragraphs of one frame, each in reading order. */
        fun paragraphs(frame: List<Int>): List<List<Int>> = bands(frame).map { inReadingOrder(it.lines) }

        /** Cuts a region into paragraphs: runs of lines with no wide empty band between them. */
        private fun bands(region: List<Int>): List<Band> {
            val sorted = region.sortedBy { lines[it].top }
            // The usual distance between one line and the next, measured where lines follow
            // each other; lines side by side overlap and are left out.
            val gaps = ArrayList<Float>()
            var bottom = lines[sorted.first()].bottom
            for (line in sorted.drop(1)) {
                val gap = lines[line].top - bottom
                if (gap > -0.5f * lineHeight) gaps += gap
                bottom = maxOf(bottom, lines[line].bottom)
            }
            val usualGap = if (gaps.size >= 3) maxOf(median(gaps), 0f) else DEFAULT_GAP * lineHeight

            val bands = ArrayList<Band>()
            var current = ArrayList<Int>()
            var gapAbove = 0f
            bottom = lines[sorted.first()].bottom
            for (line in sorted) {
                val gap = lines[line].top - bottom
                if (current.isNotEmpty() && gap > usualGap + PARAGRAPH_GAP * lineHeight) {
                    bands += Band(current, gapAbove, usualGap)
                    current = ArrayList()
                    gapAbove = gap
                }
                current += line
                bottom = maxOf(bottom, lines[line].bottom)
            }
            bands += Band(current, gapAbove, usualGap)
            return bands
        }

        /**
         * The lines left and right of the widest empty strip that no line crosses, if there is
         * one. Both sides must stand next to each other: a short line and another one further
         * down and to the right are not two columns.
         */
        private fun columns(region: List<Int>): Pair<List<Int>, List<Int>>? {
            val sorted = region.sortedBy { lines[it].left }
            val cuts = ArrayList<Pair<Float, Int>>()
            var right = lines[sorted.first()].right
            for (position in 1 until sorted.size) {
                val gap = lines[sorted[position]].left - right
                if (gap >= COLUMN_GAP * lineHeight) cuts += gap to position
                right = maxOf(right, lines[sorted[position]].right)
            }
            for ((_, cut) in cuts.sortedByDescending { it.first }) {
                val leftSide = sorted.subList(0, cut)
                val rightSide = sorted.subList(cut, sorted.size)
                val sharedTop = maxOf(leftSide.minOf { lines[it].top }, rightSide.minOf { lines[it].top })
                val sharedBottom = minOf(leftSide.maxOf { lines[it].bottom }, rightSide.maxOf { lines[it].bottom })
                if (sharedBottom - sharedTop > 0.5f * lineHeight) return leftSide to rightSide
            }
            return null
        }

        /**
         * Rows from top to bottom, and inside a row from left to right. Two lines are in the same
         * row when their vertical centers are closer than half a line height; otherwise two lines
         * next to each other would swap places as soon as one sits a single pixel higher.
         */
        private fun inReadingOrder(region: List<Int>): List<Int> {
            val rows = ArrayList<MutableList<Int>>()
            for (line in region.sortedBy { lines[it].centerY }) {
                val first = rows.lastOrNull()?.first()?.let { lines[it] }
                val box = lines[line]
                if (first != null && box.centerY - first.centerY < 0.5f * minOf(first.height, box.height)) {
                    rows.last().add(line)
                } else {
                    rows.add(mutableListOf(line))
                }
            }
            return rows.flatMap { row -> row.sortedBy { lines[it].left } }
        }
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    // All gaps are measured in line heights.

    /** An empty strip at least this wide separates two columns. */
    private const val COLUMN_GAP = 1.0f

    /** An empty band this much taller than the usual gap between lines starts a new paragraph. */
    private const val PARAGRAPH_GAP = 0.6f

    /** An empty band this much taller than the usual gap separates two areas of text. */
    private const val FRAME_GAP = 2.4f

    /** The usual gap between lines where a region has too few lines to measure it. */
    private const val DEFAULT_GAP = 0.35f
}
