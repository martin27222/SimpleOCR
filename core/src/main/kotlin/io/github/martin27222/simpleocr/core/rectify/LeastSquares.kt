package io.github.martin27222.simpleocr.core.rectify

import kotlin.math.abs
import kotlin.math.min

/** Small least-squares helpers. All problems here have a handful of unknowns. */
internal object LeastSquares {
    /**
     * Finds the coefficients c that make sum((row . c - value)^2) as small as possible.
     * Each row holds the terms of one measurement. Returns null if the measurements do not pin
     * down all coefficients.
     */
    fun solve(rows: List<DoubleArray>, values: DoubleArray, weights: DoubleArray? = null): DoubleArray? {
        val n = rows.firstOrNull()?.size ?: return null
        val normal = Array(n) { DoubleArray(n + 1) }
        for (index in rows.indices) {
            val row = rows[index]
            val weight = weights?.get(index)?.let { it * it } ?: 1.0
            for (i in 0 until n) {
                val scaled = row[i] * weight
                for (j in i until n) normal[i][j] += scaled * row[j]
                normal[i][n] += scaled * values[index]
            }
        }
        for (i in 0 until n) for (j in 0 until i) normal[i][j] = normal[j][i]
        return gaussianElimination(normal)
    }

    /**
     * Like [solve], but repeats the fit three times and each time trusts measurements less the
     * further they are from the current fit. A few wrong measurements then barely matter.
     */
    fun solveRobust(rows: List<DoubleArray>, values: DoubleArray): DoubleArray? {
        val weights = DoubleArray(rows.size) { 1.0 }
        var coefficients: DoubleArray? = null
        repeat(ROBUST_ROUNDS) {
            val fit = solve(rows, values, weights) ?: return coefficients
            coefficients = fit
            val residuals = DoubleArray(rows.size) { abs(dot(rows[it], fit) - values[it]) }
            downweight(residuals, weights)
        }
        return coefficients
    }

    /** Sets each weight to 1 for small residuals and smaller the larger the residual is. */
    fun downweight(residuals: DoubleArray, weights: DoubleArray) {
        val spread = median(residuals) * 1.4826 + 1e-9
        for (i in residuals.indices) {
            weights[i] = min(1.0, OUTLIER_THRESHOLD * spread / maxOf(residuals[i], 1e-9))
        }
    }

    fun dot(a: DoubleArray, b: DoubleArray): Double {
        var sum = 0.0
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }

    fun median(values: DoubleArray): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sortedArray()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    private fun gaussianElimination(matrix: Array<DoubleArray>): DoubleArray? {
        val n = matrix.size
        for (column in 0 until n) {
            var pivot = column
            for (row in column + 1 until n) {
                if (abs(matrix[row][column]) > abs(matrix[pivot][column])) pivot = row
            }
            if (abs(matrix[pivot][column]) < 1e-12) return null
            val swap = matrix[column]
            matrix[column] = matrix[pivot]
            matrix[pivot] = swap
            for (row in column + 1 until n) {
                val factor = matrix[row][column] / matrix[column][column]
                for (k in column..n) matrix[row][k] -= factor * matrix[column][k]
            }
        }
        val solution = DoubleArray(n)
        for (row in n - 1 downTo 0) {
            var sum = matrix[row][n]
            for (k in row + 1 until n) sum -= matrix[row][k] * solution[k]
            solution[row] = sum / matrix[row][row]
        }
        return solution
    }

    const val ROBUST_ROUNDS = 3
    private const val OUTLIER_THRESHOLD = 2.5
}
