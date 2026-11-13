package io.github.martin27222.simpleocr.core.text

import kotlin.math.min

/** How many single-character changes (insert, delete, replace) turn one text into another. */
object EditDistance {
    fun between(a: String, b: String): Int {
        if (a == b) return 0
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val replace = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(replace, min(previous[j], current[j - 1]) + 1)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /** 0 for equal texts, 1 for texts with nothing in common. */
    fun relative(a: String, b: String): Float {
        val longest = maxOf(a.length, b.length)
        return if (longest == 0) 0f else between(a, b).toFloat() / longest
    }
}
