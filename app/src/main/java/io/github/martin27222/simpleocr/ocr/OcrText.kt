package io.github.martin27222.simpleocr.ocr

import io.github.martin27222.simpleocr.core.merge.ReadLine
import io.github.martin27222.simpleocr.core.text.PlacedLine
import io.github.martin27222.simpleocr.core.text.ReadingOrder
import io.github.martin27222.simpleocr.core.text.TextFormatter
import io.github.martin27222.simpleocr.settings.AppSettings

/** The text that "Copy all" puts on the clipboard: every text block is one paragraph. */
fun OcrResult.textToCopy(settings: AppSettings): String = TextFormatter.format(
    paragraphs = blocks.map { block ->
        if (block.lines.isEmpty()) block.text.lines() else block.lines.map(OcrTextLine::text)
    },
    flowingText = settings.flowingText,
    joinHyphenatedWords = settings.joinHyphenatedWords,
)

/** All lines of the photo in reading order, as input for merging several photos. */
fun OcrResult.readLines(): List<ReadLine> = ReadingOrder.sort(
    blocks.flatMap { block ->
        block.lines.map { line ->
            PlacedLine(
                text = line.text,
                confidence = line.confidence,
                left = line.boundingBox.left,
                top = line.boundingBox.top,
                bottom = line.boundingBox.bottom,
            )
        }
    },
).map { ReadLine(it.text, it.confidence) }
