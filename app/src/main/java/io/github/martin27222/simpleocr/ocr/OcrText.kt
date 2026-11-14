package io.github.martin27222.simpleocr.ocr

import io.github.martin27222.simpleocr.core.merge.PlacedWord
import io.github.martin27222.simpleocr.core.merge.ReadLine
import io.github.martin27222.simpleocr.geometry.RectF2
import io.github.martin27222.simpleocr.core.text.TextFormatter
import io.github.martin27222.simpleocr.settings.AppSettings

/** The text that "Copy all" puts on the clipboard: every text block is one paragraph. */
fun OcrResult.textToCopy(settings: AppSettings): String = textOf(blocks, settings)

/** The text of one frame, formatted like [OcrResult.textToCopy]. */
fun OcrFrame.textToCopy(settings: AppSettings): String = textOf(blocks, settings)

private fun textOf(blocks: List<OcrTextBlock>, settings: AppSettings): String = TextFormatter.format(
    paragraphs = blocks.map { block ->
        if (block.lines.isEmpty()) block.text.lines() else block.lines.map(OcrTextLine::text)
    },
    flowingText = settings.flowingText,
    joinHyphenatedWords = settings.joinHyphenatedWords,
)

/**
 * All lines of the photo in reading order, as input for merging several photos.
 * Lines cut off by the top or bottom edge of the photo are left out: they are half visible and
 * read badly, and the neighbouring photo shows them whole.
 */
fun OcrResult.readLines(imageHeight: Int = Int.MAX_VALUE): List<ReadLine> =
    uncutLines(imageHeight).map { line -> ReadLine(line.text, line.confidence) }

/** Like [readLines], with every word and where it sits, for laying photos over each other. */
fun OcrResult.placedLines(imageHeight: Int = Int.MAX_VALUE): List<List<PlacedWord>> =
    uncutLines(imageHeight).map { line ->
        val boxes = line.wordBoxes.takeIf { it.size == line.words.size } ?: estimatedBoxes(line)
        line.words.zip(boxes) { word, box ->
            PlacedWord(word.text, word.confidence, box.left, box.top, box.right, box.bottom)
        }
    }.filter { it.isNotEmpty() }

private fun OcrResult.uncutLines(imageHeight: Int): List<OcrTextLine> =
    arranged().blocks.flatMap { block ->
        block.lines.filter { line ->
            val rim = EDGE_RIM * line.boundingBox.height
            line.boundingBox.top > rim && line.boundingBox.bottom < imageHeight - rim
        }
    }

/** Splits the line box among the words by their number of letters. */
private fun estimatedBoxes(line: OcrTextLine): List<RectF2> {
    val box = line.boundingBox
    val letters = line.words.sumOf { it.text.length + 1 }.coerceAtLeast(1)
    var before = 0
    return line.words.map { word ->
        val left = box.left + box.width * before / letters
        before += word.text.length + 1
        val right = box.left + box.width * (before - 1) / letters
        RectF2(left, box.top, right, box.bottom)
    }
}

/** A line closer to the photo edge than this share of its own height counts as cut off. */
private const val EDGE_RIM = 0.15f
