package io.github.martin27222.simpleocr.ocr

import io.github.martin27222.simpleocr.core.text.LineBox
import io.github.martin27222.simpleocr.core.text.PageLayout
import io.github.martin27222.simpleocr.geometry.RectF2

/**
 * Regroups the recognized lines by the layout of the page, see [PageLayout]: every paragraph
 * becomes one block, and every block knows the frame (column or separate area of text) it is in.
 *
 * The recognizer's own blocks are not used for this. It tends to put a narrow side column and
 * the text next to it into one block, which mixes their lines.
 */
fun OcrResult.arranged(): OcrResult {
    val lines = blocks.flatMap { it.lines }
    if (lines.isEmpty()) return this
    val frames = PageLayout.frames(
        lines.map { LineBox(it.boundingBox.left, it.boundingBox.top, it.boundingBox.right, it.boundingBox.bottom) },
    )
    val arranged = ArrayList<OcrTextBlock>()
    frames.forEachIndexed { frameIndex, frame ->
        for (paragraph in frame.paragraphs) {
            val number = arranged.size
            val blockLines = paragraph.mapIndexed { position, index ->
                lines[index].copy(id = "block-$number-line-$position")
            }
            arranged += OcrTextBlock(
                id = "block-$number",
                text = blockLines.joinToString("\n") { it.text },
                boundingBox = boundsOf(blockLines.map { it.boundingBox }),
                lines = blockLines,
                frame = frameIndex,
            )
        }
    }
    return OcrResult(fullText = arranged.joinToString("\n\n") { it.text }, blocks = arranged)
}

/** The smallest box around all [boxes]. */
fun boundsOf(boxes: List<RectF2>): RectF2 = RectF2(
    left = boxes.minOf { it.left },
    top = boxes.minOf { it.top },
    right = boxes.maxOf { it.right },
    bottom = boxes.maxOf { it.bottom },
)
