package io.github.martin27222.simpleocr.core.text

/**
 * Turns recognized lines into the text that is copied.
 *
 * Input is a list of paragraphs, each a list of lines as they appear in the photo.
 */
object TextFormatter {
    /** After these words a hyphen at the line end belongs to a list such as "Vor- und Nachteile". */
    private val listWords = setOf("und", "oder", "bzw.", "sowie", "and", "or")

    /**
     * @param flowingText join the lines of a paragraph with spaces instead of keeping line breaks
     * @param joinHyphenatedWords put words back together that were split at a line end
     */
    fun format(
        paragraphs: List<List<String>>,
        flowingText: Boolean,
        joinHyphenatedWords: Boolean,
    ): String = paragraphs
        .map { lines -> lines.map(String::trim).filter(String::isNotEmpty) }
        .filter { it.isNotEmpty() }
        .joinToString("\n\n") { lines ->
            val repaired = if (joinHyphenatedWords) rejoinSplitWords(lines) else lines
            repaired.joinToString(if (flowingText) " " else "\n")
        }

    private fun rejoinSplitWords(lines: List<String>): List<String> {
        val result = ArrayList<String>(lines.size)
        var carried: String? = null
        for (line in lines) {
            val previous = carried
            if (previous == null) {
                carried = line
                continue
            }
            val firstWord = line.substringBefore(' ')
            val rest = line.substringAfter(' ', missingDelimiterValue = "").trim()
            val joinedWord = when {
                !endsWithWordHyphen(previous) || firstWord.lowercase() in listWords -> null
                // "Zusammen-" + "fassung" is one word that was split to fit the line.
                firstWord.first().isLowerCase() -> previous.dropLast(1) + firstWord
                // "inter-" + "Course": a c, o, s, ... looks the same in both cases, so the reader
                // often takes the small letter for a capital. After a word part in small letters
                // and before small letters it can only be a small letter.
                looksMisreadAsCapital(previous, firstWord) ->
                    previous.dropLast(1) + firstWord.first().lowercaseChar() + firstWord.drop(1)
                // "Kosten-" + "Nutzen" is a compound that keeps its hyphen.
                firstWord.first().isLetterOrDigit() -> previous + firstWord
                else -> null
            }
            if (joinedWord == null) {
                result += previous
                carried = line
            } else if (rest.isEmpty()) {
                carried = joinedWord
            } else {
                result += joinedWord
                carried = rest
            }
        }
        carried?.let { result += it }
        return result
    }

    /** Letters whose capital has the same shape as the small letter, only bigger. */
    private const val SAME_SHAPE_CAPITALS = "COSUVWXZ"

    private fun looksMisreadAsCapital(previous: String, firstWord: String): Boolean {
        val part = previous.dropLast(1).substringAfterLast(' ')
        val letters = firstWord.drop(1).filter(Char::isLetter)
        return firstWord.first() in SAME_SHAPE_CAPITALS &&
            letters.isNotEmpty() && letters.all(Char::isLowerCase) &&
            part.isNotEmpty() && part.first().isLowerCase()
    }

    private fun endsWithWordHyphen(line: String): Boolean =
        line.length >= 2 && line.last() == '-' && line[line.length - 2].isLetter()
}
