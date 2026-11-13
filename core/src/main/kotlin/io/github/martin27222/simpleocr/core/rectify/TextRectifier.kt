package io.github.martin27222.simpleocr.core.rectify

import io.github.martin27222.simpleocr.core.dewarp.DewarpGrid
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One recognized character: where it sits in the photo, in pixels.
 *
 * @param position its place in the line counted in characters, spaces included. The distance
 *   between two characters divided by the difference of their positions is the width of one
 *   character there.
 */
data class Glyph(
    val centerX: Float,
    val centerY: Float,
    val height: Float,
    val left: Float,
    val position: Float,
)

/** How to rebuild the flat page: the map and the size of the flattened image. */
class FlatteningMap(
    val grid: DewarpGrid,
    val outputWidth: Int,
    val outputHeight: Int,
)

/**
 * Flattens a photo of a page using only the positions of the characters found in it.
 *
 * On a flat page, text lines are straight and parallel and all characters of a line are equally
 * wide. In a photo taken at an angle, or of a bent page, lines are curved and tilted and the
 * characters on the far side are squeezed. This class works out how the photo must be pulled so
 * that both become true again:
 *
 *  1. Line direction. Every text line gives the direction of the text at its characters. A smooth
 *     function is fitted through all of these, so the direction is known everywhere in the photo.
 *     Following that direction from a starting point draws the curve a text line would take there.
 *  2. Rows. Such a curve is drawn for every row of the flat image, starting from the column where
 *     characters are squeezed least. Each curve becomes one straight row.
 *  3. Squeeze. Along each line the distance from one character to the next is measured. How that
 *     distance changes across the page tells how much the page is squeezed sideways at each place.
 *  4. Columns. Walking along a row, squeezed parts are stretched so that every character gets the
 *     same width. Rows are lined up at the left margin, the straight line most lines start on.
 *
 * The result is a [DewarpGrid]; no image is touched here.
 */
object TextRectifier {
    /**
     * @param lines the characters of each recognized text line
     * @param width width of the photo in pixels
     * @param height height of the photo in pixels
     * @return the map, or null if there is too little text to tell the shape of the page
     */
    fun rectify(lines: List<List<Glyph>>, width: Int, height: Int): FlatteningMap? {
        val usable = lines
            .map { line -> line.sortedBy(Glyph::centerX) }
            .filter { it.size >= MIN_GLYPHS_PER_LINE }
        if (usable.size < MIN_LINES || usable.sumOf { it.size } < MIN_GLYPHS) return null

        val typicalHeight = LeastSquares.median(
            usable.flatMap { line -> line.map { it.height.toDouble() } }.toDoubleArray(),
        )
        if (typicalHeight <= 0.0) return null
        val size = max(width, height).toDouble()

        val directions = ArrayList<DoubleArray>() // x, y, slope
        val squeezes = ArrayList<SqueezeSample>()
        val lineStarts = ArrayList<DoubleArray>() // x, y
        usable.forEachIndexed { index, line ->
            val curve = LineCurve.fit(line) ?: return@forEachIndexed
            for (glyph in line) {
                val x = glyph.centerX.toDouble()
                directions += doubleArrayOf(x, curve.y(x), curve.slope(x))
            }
            squeezes += squeezeSamples(line, index)
            if (line.size >= MIN_GLYPHS_FOR_MARGIN) {
                val startX = line.minOf { it.left }.toDouble()
                lineStarts += doubleArrayOf(startX, curve.y(startX))
            }
        }
        if (directions.size < MIN_GLYPHS) return null

        val margin = 3 * typicalHeight
        val area = Area(
            left = directions.minOf { it[0] } - margin,
            top = directions.minOf { it[1] } - margin,
            right = directions.maxOf { it[0] } + margin,
            bottom = directions.maxOf { it[1] } + margin,
            size = size,
        )
        val slope = SlopeField.fit(directions, area) ?: return null
        val leftMargin = MarginLine.find(lineStarts, typicalHeight, fallbackX = area.left + margin)
        val squeeze = SqueezeCurve.fit(squeezes, area, leftMargin)

        // Rows: follow the text direction left and right from the least squeezed column.
        val columns = nodeCount(width)
        val rows = nodeCount(height)
        val stepX = (width - 1).toDouble() / (columns - 1)
        val stepY = (height - 1).toDouble() / (rows - 1)
        val referenceColumn = (squeeze.leastSqueezedX.coerceIn(0.1 * width, 0.9 * width) / stepX).roundToInt()
        val rowY = Array(rows) { DoubleArray(columns) }
        for (row in 0 until rows) rowY[row][referenceColumn] = row * stepY
        for (column in referenceColumn + 1 until columns) {
            for (row in 0 until rows) {
                rowY[row][column] = slope.follow((column - 1) * stepX, rowY[row][column - 1], stepX)
            }
        }
        for (column in referenceColumn - 1 downTo 0) {
            for (row in 0 until rows) {
                rowY[row][column] = slope.follow((column + 1) * stepX, rowY[row][column + 1], -stepX)
            }
        }

        // Columns: distance along each row, counted in "unsqueezed" pixels from the left margin.
        val along = Array(rows) { DoubleArray(columns) }
        for (row in 0 until rows) {
            var distance = 0.0
            for (column in 0 until columns) {
                val scale = squeeze.factor(column * stepX, rowY[row][column]).coerceIn(MIN_SCALE, MAX_SCALE)
                distance += stepX / scale
                along[row][column] = distance
            }
            val atMargin = crossing(along[row]) { column ->
                column * stepX - leftMargin.x(rowY[row][column])
            }
            for (column in 0 until columns) along[row][column] -= atMargin
        }

        val start = LeastSquares.median(DoubleArray(rows) { along[it][0] })
        val end = LeastSquares.median(DoubleArray(rows) { along[it][columns - 1] })
        val flatWidth = end - start
        if (flatWidth < MIN_WIDTH_RATIO * width) return null
        val outputWidth = min(flatWidth, MAX_WIDTH_RATIO * width).roundToInt()
        val outputColumns = nodeCount(outputWidth)

        val gridX = FloatArray(outputColumns * rows)
        val gridY = FloatArray(outputColumns * rows)
        for (row in 0 until rows) {
            for (column in 0 until outputColumns) {
                val target = start + flatWidth * column / (outputColumns - 1)
                val sourceColumn = positionOf(along[row], target)
                val sourceX = sourceColumn * stepX
                val sourceY = interpolate(rowY[row], sourceColumn)
                gridX[row * outputColumns + column] = (sourceX / (width - 1) * 2 - 1).toFloat()
                gridY[row * outputColumns + column] = (sourceY / (height - 1) * 2 - 1).toFloat()
            }
        }
        return FlatteningMap(DewarpGrid(outputColumns, rows, gridX, gridY), outputWidth, height)
    }

    private class SqueezeSample(val x: Double, val y: Double, val logPitch: Double, val line: Int)

    /** The width of one character, measured over a window of neighbouring characters. */
    private fun squeezeSamples(line: List<Glyph>, lineIndex: Int): List<SqueezeSample> {
        val window = SQUEEZE_WINDOW
        if (line.size < 2 * window + 3) return emptyList()
        val samples = ArrayList<SqueezeSample>()
        for (i in window until line.size - window) {
            val first = line[i - window]
            val last = line[i + window]
            val characters = last.position - first.position
            if (characters <= 0f) continue
            val pitch = (last.centerX - first.centerX) / characters
            if (pitch <= 0f) continue
            samples += SqueezeSample(
                x = line[i].centerX.toDouble(),
                y = line[i].centerY.toDouble(),
                logPitch = ln(pitch.toDouble()),
                line = lineIndex,
            )
        }
        return samples
    }

    /** The part of the photo that holds text, plus a rim. Outside it, fitted values stay constant. */
    private class Area(val left: Double, val top: Double, val right: Double, val bottom: Double, val size: Double) {
        fun normalX(x: Double) = x.coerceIn(left, right) / size * 2 - 1
        fun normalY(y: Double) = y.coerceIn(top, bottom) / size * 2 - 1
    }

    /** The curve of one text line: y as a parabola (or straight line, for short lines) of x. */
    private class LineCurve(val centerX: Double, val scale: Double, val c: DoubleArray) {
        fun y(x: Double): Double {
            val t = (x - centerX) / scale
            return c[0] + c[1] * t + (if (c.size > 2) c[2] * t * t else 0.0)
        }

        fun slope(x: Double): Double {
            val t = (x - centerX) / scale
            return (c[1] + (if (c.size > 2) 2 * c[2] * t else 0.0)) / scale
        }

        companion object {
            fun fit(line: List<Glyph>): LineCurve? {
                val centerX = line.sumOf { it.centerX.toDouble() } / line.size
                val scale = max(1.0, (line.last().centerX - line.first().centerX).toDouble())
                val degree = if (line.size >= MIN_GLYPHS_FOR_PARABOLA) 2 else 1
                val rows = line.map { glyph ->
                    val t = (glyph.centerX - centerX) / scale
                    if (degree == 2) doubleArrayOf(1.0, t, t * t) else doubleArrayOf(1.0, t)
                }
                val values = DoubleArray(line.size) { line[it].centerY.toDouble() }
                val coefficients = LeastSquares.solve(rows, values) ?: return null
                return LineCurve(centerX, scale, coefficients)
            }
        }
    }

    /** The direction of the text (dy/dx) at every place in the photo. */
    private class SlopeField(private val area: Area, private val c: DoubleArray) {
        fun at(x: Double, y: Double): Double = LeastSquares.dot(terms(area.normalX(x), area.normalY(y)), c)

        /** Where a text line through (x, y) is after moving [dx] sideways (midpoint rule). */
        fun follow(x: Double, y: Double, dx: Double): Double {
            val halfway = y + dx / 2 * at(x, y)
            return y + dx * at(x + dx / 2, halfway)
        }

        companion object {
            fun fit(samples: List<DoubleArray>, area: Area): SlopeField? {
                val rows = samples.map { terms(area.normalX(it[0]), area.normalY(it[1])) }
                val values = DoubleArray(samples.size) { samples[it][2] }
                return LeastSquares.solveRobust(rows, values)?.let { SlopeField(area, it) }
            }

            /** All products x^i * y^j up to the third power in total. */
            private fun terms(x: Double, y: Double): DoubleArray {
                val terms = DoubleArray(10)
                var index = 0
                var powerX = 1.0
                for (i in 0..3) {
                    var term = powerX
                    for (j in 0..3 - i) {
                        terms[index++] = term
                        term *= y
                    }
                    powerX *= x
                }
                return terms
            }
        }
    }

    /** The straight line x = offset + tilt * y that most text lines start on. */
    private class MarginLine(val offset: Double, val tilt: Double) {
        fun x(y: Double): Double = offset + tilt * y

        companion object {
            fun find(starts: List<DoubleArray>, typicalHeight: Double, fallbackX: Double): MarginLine {
                val tolerance = 0.6 * typicalHeight
                var best = MarginLine(starts.minOfOrNull { it[0] } ?: fallbackX, 0.0)
                var bestCount = 0
                // Try the line through every pair of starts and keep the one most starts agree with.
                for (i in starts.indices) {
                    for (j in i + 1 until starts.size) {
                        val dy = starts[i][1] - starts[j][1]
                        if (abs(dy) < 3 * typicalHeight) continue
                        val tilt = (starts[i][0] - starts[j][0]) / dy
                        if (abs(tilt) > MAX_MARGIN_TILT) continue
                        val offset = starts[i][0] - tilt * starts[i][1]
                        val count = starts.count { abs(it[0] - (offset + tilt * it[1])) < tolerance }
                        if (count > bestCount) {
                            bestCount = count
                            best = MarginLine(offset, tilt)
                        }
                    }
                }
                if (bestCount < 3) return best
                val agreeing = starts.filter { abs(it[0] - best.x(it[1])) < tolerance }
                val fit = LeastSquares.solve(
                    agreeing.map { doubleArrayOf(1.0, it[1]) },
                    DoubleArray(agreeing.size) { agreeing[it][0] },
                )
                return fit?.let { MarginLine(it[0], it[1]) } ?: best
            }
        }
    }

    /**
     * How much the page is squeezed sideways, as a curve across the page.
     *
     * The measured character width is modelled as a base value per text line (lines differ in
     * typeface and size) times a shape that all lines share. The shape depends
     * on the place across the page, measured along lines parallel to the left margin, because a
     * page bends around an upright axis.
     */
    private class SqueezeCurve(
        private val area: Area,
        private val margin: MarginLine,
        private val c: DoubleArray,
    ) {
        private val peak: Double
        val leastSqueezedX: Double

        init {
            var bestT = 0.0
            var best = Double.NEGATIVE_INFINITY
            for (i in 0..40) {
                val t = -1 + i / 20.0
                if (shape(t) > best) {
                    best = shape(t)
                    bestT = t
                }
            }
            peak = best
            leastSqueezedX = area.left + (bestT + 1) / 2 * (area.right - area.left)
        }

        /** 1 where the page is squeezed least, smaller where it is squeezed more. */
        fun factor(x: Double, y: Double): Double = exp(shape(across(x, y)) - peak)

        private fun shape(t: Double): Double = c[0] * t + c[1] * t * t + c[2] * t * t * t

        fun across(x: Double, y: Double): Double {
            val middleY = (area.top + area.bottom) / 2
            val shifted = x - margin.tilt * (y - middleY)
            return ((shifted - area.left) / (area.right - area.left)).coerceIn(-0.1, 1.1) * 2 - 1
        }

        companion object {
            fun fit(samples: List<SqueezeSample>, area: Area, margin: MarginLine): SqueezeCurve {
                val flat = SqueezeCurve(area, margin, DoubleArray(3))
                if (samples.size < MIN_SQUEEZE_SAMPLES) return flat
                val t = DoubleArray(samples.size) { flat.across(samples[it].x, samples[it].y) }
                val weights = DoubleArray(samples.size) { 1.0 }
                var coefficients = DoubleArray(3)
                repeat(LeastSquares.ROBUST_ROUNDS) {
                    // Removing each line's own average removes its base value from the problem.
                    val sums = HashMap<Int, DoubleArray>() // weight, t, t^2, t^3, value
                    for (i in samples.indices) {
                        val w = weights[i] * weights[i]
                        val sum = sums.getOrPut(samples[i].line) { DoubleArray(5) }
                        sum[0] += w
                        sum[1] += w * t[i]
                        sum[2] += w * t[i] * t[i]
                        sum[3] += w * t[i] * t[i] * t[i]
                        sum[4] += w * samples[i].logPitch
                    }
                    val rows = ArrayList<DoubleArray>(samples.size)
                    val values = DoubleArray(samples.size)
                    for (i in samples.indices) {
                        val sum = sums.getValue(samples[i].line)
                        rows += doubleArrayOf(
                            t[i] - sum[1] / sum[0],
                            t[i] * t[i] - sum[2] / sum[0],
                            t[i] * t[i] * t[i] - sum[3] / sum[0],
                        )
                        values[i] = samples[i].logPitch - sum[4] / sum[0]
                    }
                    val fit = LeastSquares.solve(rows, values, weights) ?: return SqueezeCurve(area, margin, coefficients)
                    coefficients = fit
                    val residuals = DoubleArray(samples.size) { abs(LeastSquares.dot(rows[it], fit) - values[it]) }
                    LeastSquares.downweight(residuals, weights)
                }
                return SqueezeCurve(area, margin, coefficients)
            }
        }
    }

    /** Grid nodes for a side of [pixels] pixels, about [NODE_SPACING] pixels apart. */
    private fun nodeCount(pixels: Int): Int = max(2, ceil((pixels - 1).toDouble() / NODE_SPACING).toInt() + 1)

    /** The value of [values] where the increasing function [signed] of the index crosses zero. */
    private inline fun crossing(values: DoubleArray, signed: (Int) -> Double): Double {
        var previous = signed(0)
        if (previous >= 0) return values[0]
        for (index in 1 until values.size) {
            val current = signed(index)
            if (current >= 0) {
                val fraction = -previous / (current - previous)
                return values[index - 1] + fraction * (values[index] - values[index - 1])
            }
            previous = current
        }
        return values.last()
    }

    /** The (fractional) index at which the increasing array [values] reaches [target]. */
    private fun positionOf(values: DoubleArray, target: Double): Double {
        if (target <= values[0]) return 0.0
        if (target >= values.last()) return (values.size - 1).toDouble()
        var low = 0
        var high = values.size - 1
        while (high - low > 1) {
            val middle = (low + high) / 2
            if (values[middle] <= target) low = middle else high = middle
        }
        return low + (target - values[low]) / (values[high] - values[low])
    }

    private fun interpolate(values: DoubleArray, position: Double): Double {
        val index = position.toInt().coerceIn(0, values.size - 2)
        val fraction = position - index
        return values[index] + fraction * (values[index + 1] - values[index])
    }

    private const val MIN_LINES = 4
    private const val MIN_GLYPHS = 60
    private const val MIN_GLYPHS_PER_LINE = 6
    private const val MIN_GLYPHS_FOR_PARABOLA = 12
    private const val MIN_GLYPHS_FOR_MARGIN = 15
    private const val MIN_SQUEEZE_SAMPLES = 30
    private const val SQUEEZE_WINDOW = 5
    private const val NODE_SPACING = 8
    private const val MIN_SCALE = 0.2
    private const val MAX_SCALE = 1.0
    private const val MAX_MARGIN_TILT = 1.0
    private const val MIN_WIDTH_RATIO = 0.5
    private const val MAX_WIDTH_RATIO = 1.5
}
