package io.github.martin27222.simpleocr.ocr

import io.github.martin27222.simpleocr.core.merge.ReadLine
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
    arranged().blocks.flatMap { block ->
        block.lines.filter { line ->
            val rim = EDGE_RIM * line.boundingBox.height
            line.boundingBox.top > rim && line.boundingBox.bottom < imageHeight - rim
        }.map { line -> ReadLine(line.text, line.confidence) }
    }

/** A line closer to the photo edge than this share of its own height counts as cut off. */
private const val EDGE_RIM = 0.15f
