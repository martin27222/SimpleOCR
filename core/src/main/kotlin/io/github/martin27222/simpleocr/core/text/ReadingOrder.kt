package io.github.martin27222.simpleocr.core.text

import kotlin.math.min

/** A recognized line and where it sits in the photo, in pixels. */
data class PlacedLine(
    val text: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val bottom: Float,
) {
    val centerY: Float get() = (top + bottom) / 2f
    val height: Float get() = bottom - top
}

/**
 * Puts lines into reading order: rows from top to bottom, and inside a row from left to right.
 *
 * Two lines belong to the same row when their vertical centers are closer than half a line
 * height. Without that rule, two lines next to each other would swap places as soon as one of
 * them sits a single pixel higher.
 */
object ReadingOrder {
    fun sort(lines: List<PlacedLine>): List<PlacedLine> {
        val rows = ArrayList<MutableList<PlacedLine>>()
        for (line in lines.sortedBy(PlacedLine::centerY)) {
            val row = rows.lastOrNull()
            val first = row?.first()
            if (first != null && line.centerY - first.centerY < 0.5f * min(first.height, line.height)) {
                row.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }
        return rows.flatMap { row -> row.sortedBy(PlacedLine::left) }
    }
}
