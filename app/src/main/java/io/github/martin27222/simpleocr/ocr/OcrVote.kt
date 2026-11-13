package io.github.martin27222.simpleocr.ocr

import io.github.martin27222.simpleocr.core.merge.ReadingVote

/**
 * This reading with single words corrected by other readings of the same photo; see [ReadingVote].
 * Boxes and layout stay as they are, only the text of lines changes.
 */
fun OcrResult.votedWith(others: List<OcrResult>): OcrResult {
    if (others.isEmpty()) return this
    val lines = blocks.flatMap { it.lines }
    val voted = ReadingVote.vote(
        base = lines.map { it.words },
        others = others.map { other -> other.blocks.flatMap { block -> block.lines.map { it.words } } },
    )
    var index = 0
    val newBlocks = blocks.map { block ->
        val newLines = block.lines.map { line ->
            val words = voted[index++]
            if (words.isEmpty() || words == line.words) line else line.copy(text = words.joinToString(" ") { it.text }, words = words)
        }
        if (newLines == block.lines) block else block.copy(text = newLines.joinToString("\n") { it.text }, lines = newLines)
    }
    return OcrResult(fullText = newBlocks.joinToString("\n\n") { it.text }, blocks = newBlocks)
}
