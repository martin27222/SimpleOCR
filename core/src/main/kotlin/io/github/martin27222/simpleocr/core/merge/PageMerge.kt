package io.github.martin27222.simpleocr.core.merge

import io.github.martin27222.simpleocr.core.rectify.LeastSquares
import io.github.martin27222.simpleocr.core.text.LineBox
import io.github.martin27222.simpleocr.core.text.PageLayout
import java.util.Random
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * A recognized word and where it sits, in pixels.
 *
 * @property inset how far the word is from the nearer end of its line in the photo it was read
 *   from, in line heights. A word at the very end of a line is often cut off by the photo edge.
 *   [PageMerge] fills this in.
 */
data class PlacedWord(
    val text: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val inset: Float = 0f,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * Lays several overlapping photos of the same page over each other, using the positions of the
 * recognized words, and returns the words of the whole page.
 *
 * The first photo is the page; every further photo is added like this:
 *  1. Anchors. A longer word that both the page and the photo show is probably the same printed
 *     word. Each such pair is an anchor: one spot seen in both.
 *  2. Fit. The shift, size and turn that bring most anchors on top of each other are searched
 *     by trying pairs of anchors (RANSAC). Anchors that do not fit are words that merely repeat
 *     in the text and are dropped.
 *  3. Follow the page. Once the photo is roughly in place, short equal words that land on each
 *     other become anchors too. Each word is then moved by the anchors near it (moving least
 *     squares), so a bent page is followed line by line.
 *  4. Words. A word that lands on the same word of the page is kept once, in the reading
 *     further from the end of its line in its own photo, because words at the edge of a photo
 *     are the ones that get cut off. A word on a free place is added to the line it lands on;
 *     scraps of cut-off words that sit between words of the other photo are dropped.
 *
 * Because the place decides and not the text, a word that repeats in the text is never taken
 * for an overlap, and nothing is doubled where two photos meet.
 */
object PageMerge {
    /**
     * @param photos each photo as lines of words, in the pixel coordinates of that photo
     * @return the lines of the whole page in the coordinates of the first photo, or null if a
     *   photo shares too few words with the others to be placed
     */
    fun merge(photos: List<List<List<PlacedWord>>>): List<List<PlacedWord>>? {
        val prepared = photos.map(::withInsets).filter { it.isNotEmpty() }
        if (prepared.isEmpty()) return emptyList()
        var page = prepared.first()
        for (photo in prepared.drop(1)) page = add(page, photo) ?: return null
        return page.map { line -> line.sortedBy { it.left } }
    }

    /**
     * The text of merged lines as paragraphs, area by area (see [PageLayout]). An area made of
     * one or two stray characters, such as a speck read as a letter, is left out.
     */
    fun paragraphs(lines: List<List<PlacedWord>>): List<List<String>> {
        val filled = lines.filter { it.isNotEmpty() }.map { line -> line.sortedBy { it.left } }
        val boxes = filled.map { line ->
            LineBox(line.minOf { it.left }, line.minOf { it.top }, line.maxOf { it.right }, line.maxOf { it.bottom })
        }
        val texts = filled.map { line -> line.joinToString(" ") { it.text } }
        return PageLayout.frames(boxes)
            .filter { frame -> frame.lines.sumOf { index -> texts[index].count { !it.isWhitespace() } } > MAX_STRAY_LETTERS }
            .flatMap { frame -> frame.paragraphs.map { paragraph -> paragraph.map { texts[it] } } }
    }

    /** Sorts the words of every line and notes how far each one is from the end of its row. */
    private fun withInsets(photo: List<List<PlacedWord>>): List<List<PlacedWord>> {
        val lines = photo.filter { it.isNotEmpty() }.map { line -> line.sortedBy { it.left } }
        return lines.map { line ->
            val height = heightOf(line)
            val centerY = centerYOf(line)
            // The recognizer sometimes reports one printed line in pieces; the ends of the row count.
            val row = lines.filter { abs(centerYOf(it) - centerY) < 0.5f * min(heightOf(it), height) }
            val rowLeft = row.minOf { it.first().left }
            val rowRight = row.maxOf { it.last().right }
            line.map { word ->
                word.copy(inset = min(word.centerX - rowLeft, rowRight - word.centerX) / height)
            }
        }
    }

    private class Anchor(val pageLine: Int, val pageWord: PlacedWord, val photoLine: Int, val photoWord: PlacedWord)

    private fun add(page: List<List<PlacedWord>>, photo: List<List<PlacedWord>>): List<List<PlacedWord>>? {
        val lineHeight = median(page.map(::heightOf))
        val candidates = candidates(page, photo)
        val fit = fit(candidates, lineHeight) ?: return null
        var anchors = oneToOne(candidates.filter { fit.error(it) < INLIER_DISTANCE * lineHeight }, fit)
        if (anchors.size < MIN_ANCHORS) return null
        // Short words such as "a" or "to" are no good for the first fit, but once the photo is
        // roughly in place, an equal word right where it should be is the same printed word.
        repeat(2) { anchors = withNearbyWords(page, photo, anchors, lineHeight) }
        val warp = Warp(anchors, lineHeight)

        val merged = page.map { it.toMutableList() }.toMutableList()
        val sameWord = anchors.associate { it.photoWord to it.pageWord }
        val fromPhoto = HashSet<PlacedWord>()
        val confirmed = HashSet<PlacedWord>()
        val newLines = ArrayList<MutableList<PlacedWord>>()
        for (line in photo) {
            val moved = line.map(warp::move)
            // The page line that the anchored words of this photo line sit on; its other words
            // belong there too, even where the page line has no words yet.
            val home = line.mapNotNull { word -> sameWord[word]?.let { partner -> merged.indexOfFirst { partner in it } } }
                .filter { it >= 0 }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            var newLine: MutableList<PlacedWord>? = null
            for ((word, placed) in line.zip(moved)) {
                val partner = sameWord[word]
                val partnerLine = partner?.let { p -> merged.indexOfFirst { p in it } } ?: -1
                if (partnerLine >= 0) {
                    confirmed += keepBetter(merged[partnerLine], partner!!, placed)
                    continue
                }
                val target = home ?: nearestLine(merged, placed, lineHeight)
                if (target != null) {
                    if (placeInto(merged[target], placed, lineHeight)) fromPhoto += placed
                } else {
                    // Text the page did not show yet, e.g. a line below its lower edge.
                    if (newLine == null) newLine = ArrayList<PlacedWord>().also { newLines += it }
                    newLine += placed
                }
            }
        }
        for (line in merged) {
            line.sortBy { it.left }
            dropEdgeScraps(line, fromPhoto, confirmed, lineHeight)
        }
        merged += newLines
        return merged.filter { it.isNotEmpty() }
    }

    /**
     * Removes scraps of cut-off words: a word at the edge of its own photo that sits between
     * words of the other photo, which saw that place fully and found no word there.
     */
    private fun dropEdgeScraps(line: MutableList<PlacedWord>, fromPhoto: Set<PlacedWord>, confirmed: Set<PlacedWord>, lineHeight: Float) {
        if (line.none { it in fromPhoto }) return
        val reach = SCRAP_REACH * lineHeight
        line.removeAll { word ->
            if (word in confirmed || word.inset >= EDGE_INSET) return@removeAll false
            val mine = word in fromPhoto
            // Words both photos read count for either side.
            val others = line.filter { it !== word && (it in confirmed || (it in fromPhoto) != mine) }
            others.any { it.right <= word.centerX && word.left - it.right < reach } &&
                others.any { it.left >= word.centerX && it.left - word.right < reach }
        }
    }

    /**
     * Adds as anchors the photo words that land, after a rough placement, right on an equal word
     * of the page. Words of any length count here, since the place is already known.
     */
    private fun withNearbyWords(
        page: List<List<PlacedWord>>,
        photo: List<List<PlacedWord>>,
        anchors: List<Anchor>,
        lineHeight: Float,
    ): List<Anchor> {
        val warp = Warp(anchors, lineHeight)
        val usedPage = anchors.mapTo(HashSet()) { it.pageWord }
        val usedPhoto = anchors.mapTo(HashSet()) { it.photoWord }
        val found = ArrayList<Pair<Float, Anchor>>()
        photo.forEachIndexed { photoLine, words ->
            for (word in words) {
                if (word in usedPhoto) continue
                val key = key(word.text)
                if (key.isEmpty()) continue
                val moved = warp.move(word)
                page.forEachIndexed { pageLine, pageWords ->
                    for (pageWord in pageWords) {
                        if (pageWord in usedPage || key(pageWord.text) != key) continue
                        val dx = abs(pageWord.centerX - moved.centerX)
                        val dy = abs(pageWord.centerY - moved.centerY)
                        if (dx < NEAR_X * lineHeight && dy < NEAR_Y * lineHeight) {
                            found += hypot(dx, dy) to Anchor(pageLine, pageWord, photoLine, word)
                        }
                    }
                }
            }
        }
        val added = found.sortedBy { it.first }.map { it.second }
            .filter { usedPage.add(it.pageWord) && usedPhoto.add(it.photoWord) }
        return anchors + added
    }

    /** The page line whose height at the word is closest to the word's, if it is close enough. */
    private fun nearestLine(lines: List<List<PlacedWord>>, word: PlacedWord, lineHeight: Float): Int? {
        var best: Int? = null
        var bestDistance = SAME_LINE * lineHeight
        lines.forEachIndexed { index, line ->
            if (line.isEmpty()) return@forEachIndexed
            // A word far beside a line, e.g. in the next column, does not continue it.
            val reach = MAX_GAP * lineHeight
            if (word.right < line.first().left - reach || word.left > line.last().right + reach) {
                return@forEachIndexed
            }
            val distance = abs(yAt(line, word.centerX) - word.centerY)
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }

    /** Both photos read the same printed word: the reading further inside its photo is kept. */
    private fun keepBetter(line: MutableList<PlacedWord>, onPage: PlacedWord, fromPhoto: PlacedWord): PlacedWord {
        val index = line.indexOf(onPage)
        val photoWins = fromPhoto.text.length > onPage.text.length ||
            (fromPhoto.text.length == onPage.text.length && photoWordWins(listOf(onPage), fromPhoto))
        val kept = if (photoWins) fromPhoto.copy(left = onPage.left, top = onPage.top, right = onPage.right, bottom = onPage.bottom) else onPage
        val result = kept.copy(confidence = max(onPage.confidence, fromPhoto.confidence), inset = max(onPage.inset, fromPhoto.inset))
        line[index] = result
        return result
    }

    /** A word without a partner: it either covers words already there or fills a gap. */
    private fun placeInto(line: MutableList<PlacedWord>, word: PlacedWord, lineHeight: Float): Boolean {
        val slack = SLACK * lineHeight
        val covered = line.filter { other ->
            min(word.right, other.right) - max(word.left, other.left) > SAME_PLACE * min(word.width, other.width) - slack
        }
        val added = when {
            covered.isEmpty() -> true
            photoWordWins(covered, word) -> {
                line.removeAll(covered)
                true
            }
            else -> false
        }
        if (added) {
            line += word
            line.sortBy { it.left }
        }
        return added
    }

    /**
     * Moves a point of the photo to the page. Near each point the anchors around it count most,
     * so a page that bends is followed line by line (moving least squares with shift, size and
     * turn).
     */
    private class Warp(private val anchors: List<Anchor>, private val lineHeight: Float) {
        fun move(word: PlacedWord): PlacedWord {
            val (x, y) = at(word.centerX, word.centerY)
            val (leftX, _) = at(word.left, word.centerY)
            val (rightX, _) = at(word.right, word.centerY)
            val halfHeight = word.height * scaleNear(word.centerX, word.centerY) / 2f
            val halfWidth = (rightX - leftX) / 2f
            return word.copy(left = x - halfWidth, right = x + halfWidth, top = y - halfHeight, bottom = y + halfHeight)
        }

        private fun scaleNear(x: Float, y: Float): Float {
            val solved = solveNear(x, y) ?: return 1f
            return hypot(solved[0], solved[1]).toFloat()
        }

        fun at(x: Float, y: Float): Pair<Float, Float> {
            val c = solveNear(x, y) ?: return x to y
            val p = c[0]
            val q = c[1]
            return (p * x - q * y + c[2]).toFloat() to (q * x + p * y + c[3]).toFloat()
        }

        private val cache = HashMap<Pair<Float, Float>, DoubleArray?>()

        private fun solveNear(x: Float, y: Float): DoubleArray? = cache.getOrPut(x to y) {
            val rows = ArrayList<DoubleArray>(anchors.size * 2)
            val values = DoubleArray(anchors.size * 2)
            val weights = DoubleArray(anchors.size * 2)
            val soft = (WARP_REACH * lineHeight).let { it * it }
            anchors.forEachIndexed { index, anchor ->
                val ax = anchor.photoWord.centerX.toDouble()
                val ay = anchor.photoWord.centerY.toDouble()
                val dx = ax - x
                val dy = ay - y
                // Weights are squared inside the solver.
                val weight = 1.0 / kotlin.math.sqrt(dx * dx + dy * dy + soft)
                rows += doubleArrayOf(ax, -ay, 1.0, 0.0)
                rows += doubleArrayOf(ay, ax, 0.0, 1.0)
                values[index * 2] = anchor.pageWord.centerX.toDouble()
                values[index * 2 + 1] = anchor.pageWord.centerY.toDouble()
                weights[index * 2] = weight
                weights[index * 2 + 1] = weight
            }
            LeastSquares.solve(rows, values, weights)
        }
    }

    // --- Step 1: anchors ---------------------------------------------------------------------

    /** Every pair of a page word and a photo word with the same longer text. */
    private fun candidates(page: List<List<PlacedWord>>, photo: List<List<PlacedWord>>): List<Anchor> {
        class Spot(val line: Int, val word: PlacedWord)
        fun index(lines: List<List<PlacedWord>>): Map<String, List<Spot>> {
            val spots = HashMap<String, MutableList<Spot>>()
            lines.forEachIndexed { line, words ->
                for (word in words) {
                    val key = key(word.text)
                    if (key.length >= MIN_ANCHOR_LENGTH) spots.getOrPut(key) { ArrayList() } += Spot(line, word)
                }
            }
            return spots
        }
        val onPage = index(page)
        val anchors = ArrayList<Anchor>()
        for ((key, inPhoto) in index(photo)) {
            val inPage = onPage[key] ?: continue
            // A word that shows up many times says little about where it belongs.
            if (inPage.size > MAX_REPEATS || inPhoto.size > MAX_REPEATS) continue
            for (a in inPage) for (b in inPhoto) anchors += Anchor(a.line, a.word, b.line, b.word)
        }
        return anchors
    }

    /** Letters and digits only, in lower case: "Next," and "next" are the same word. */
    private fun key(text: String): String = text.filter { it.isLetterOrDigit() }.lowercase()

    /** Keeps for every word only its best anchor, so no word is tied to two places. */
    private fun oneToOne(anchors: List<Anchor>, fit: Fit): List<Anchor> {
        val usedPage = HashSet<PlacedWord>()
        val usedPhoto = HashSet<PlacedWord>()
        return anchors.sortedBy { fit.error(it) }.filter { usedPage.add(it.pageWord) && usedPhoto.add(it.photoWord) }
    }

    // --- Step 2: fit --------------------------------------------------------------------------

    /**
     * Moves a point of the photo to its place on the page: x' = a x + b y + c, y' = d x + e y + f.
     */
    private class Fit(val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float) {
        /** How much larger the photo's text is drawn on the page. */
        val scale: Float get() = hypot(a, d)

        fun error(anchor: Anchor): Float {
            val x = anchor.photoWord.centerX
            val y = anchor.photoWord.centerY
            return hypot(a * x + b * y + c - anchor.pageWord.centerX, d * x + e * y + f - anchor.pageWord.centerY)
        }
    }

    private fun fit(anchors: List<Anchor>, lineHeight: Float): Fit? {
        if (anchors.size < MIN_ANCHORS) return null
        // Shift, size and turn follow from two anchors. Many pairs are tried; the one that most
        // other anchors agree with wins.
        val random = Random(1)
        var best: Fit? = null
        var bestCount = 0
        val tries = min(MAX_TRIES, anchors.size * (anchors.size - 1) / 2)
        repeat(tries) {
            val first = anchors[random.nextInt(anchors.size)]
            val second = anchors[random.nextInt(anchors.size)]
            val guess = fromTwo(first, second, lineHeight) ?: return@repeat
            val count = anchors.count { guess.error(it) < ROUGH_DISTANCE * lineHeight }
            if (count > bestCount) {
                bestCount = count
                best = guess
            }
        }
        var fit = best ?: return null
        // Then the fit is made exact on all anchors that agree, twice.
        var distance = ROUGH_DISTANCE
        repeat(2) {
            val agreeing = anchors.filter { fit.error(it) < distance * lineHeight }
            fit = exact(agreeing, lineHeight) ?: return fit
            distance = INLIER_DISTANCE
        }
        return fit
    }

    private fun fromTwo(first: Anchor, second: Anchor, lineHeight: Float): Fit? {
        val photoX = second.photoWord.centerX - first.photoWord.centerX
        val photoY = second.photoWord.centerY - first.photoWord.centerY
        val pageX = second.pageWord.centerX - first.pageWord.centerX
        val pageY = second.pageWord.centerY - first.pageWord.centerY
        val photoLength = hypot(photoX, photoY)
        if (photoLength < MIN_PAIR_DISTANCE * lineHeight) return null
        val scale = hypot(pageX, pageY) / photoLength
        if (scale !in MIN_SCALE..MAX_SCALE) return null
        var turn = atan2(pageY, pageX) - atan2(photoY, photoX)
        while (turn > Math.PI) turn -= (2 * Math.PI).toFloat()
        while (turn < -Math.PI) turn += (2 * Math.PI).toFloat()
        if (abs(turn) > MAX_TURN) return null
        val cos = scale * kotlin.math.cos(turn)
        val sin = scale * kotlin.math.sin(turn)
        val x = first.photoWord.centerX
        val y = first.photoWord.centerY
        return Fit(
            a = cos, b = -sin, c = first.pageWord.centerX - (cos * x - sin * y),
            d = sin, e = cos, f = first.pageWord.centerY - (sin * x + cos * y),
        )
    }

    /**
     * The best fit through all agreeing anchors by least squares. With enough anchors spread
     * over the page it may also shear and stretch, which follows a photo taken at an angle.
     */
    private fun exact(anchors: List<Anchor>, lineHeight: Float): Fit? {
        if (anchors.size < MIN_ANCHORS) return null
        val spreadX = anchors.maxOf { it.photoWord.centerX } - anchors.minOf { it.photoWord.centerX }
        val spreadY = anchors.maxOf { it.photoWord.centerY } - anchors.minOf { it.photoWord.centerY }
        val free = anchors.size >= MIN_ANCHORS_FOR_SHEAR &&
            spreadX > MIN_SPREAD * lineHeight && spreadY > MIN_SPREAD * lineHeight
        return if (free) {
            val rows = anchors.map { doubleArrayOf(it.photoWord.centerX.toDouble(), it.photoWord.centerY.toDouble(), 1.0) }
            val x = LeastSquares.solve(rows, DoubleArray(anchors.size) { anchors[it].pageWord.centerX.toDouble() })
            val y = LeastSquares.solve(rows, DoubleArray(anchors.size) { anchors[it].pageWord.centerY.toDouble() })
            if (x == null || y == null) return null
            Fit(x[0].toFloat(), x[1].toFloat(), x[2].toFloat(), y[0].toFloat(), y[1].toFloat(), y[2].toFloat())
        } else {
            // Shift, size and turn only: x' = p x - q y + c, y' = q x + p y + f.
            val rows = ArrayList<DoubleArray>()
            val values = DoubleArray(anchors.size * 2)
            anchors.forEachIndexed { index, anchor ->
                val x = anchor.photoWord.centerX.toDouble()
                val y = anchor.photoWord.centerY.toDouble()
                rows += doubleArrayOf(x, -y, 1.0, 0.0)
                rows += doubleArrayOf(y, x, 0.0, 1.0)
                values[index * 2] = anchor.pageWord.centerX.toDouble()
                values[index * 2 + 1] = anchor.pageWord.centerY.toDouble()
            }
            val solved = LeastSquares.solve(rows, values) ?: return null
            val p = solved[0].toFloat()
            val q = solved[1].toFloat()
            Fit(p, -q, solved[2].toFloat(), q, p, solved[3].toFloat())
        }
    }

    /** The height of a page line at [x]; lines of a photo taken at an angle slope a little. */
    private fun yAt(line: List<PlacedWord>, x: Float): Float {
        val first = line.minBy { it.left }
        val last = line.maxBy { it.right }
        val length = last.centerX - first.centerX
        if (length < first.height) return centerYOf(line)
        return first.centerY + (x - first.centerX) / length * (last.centerY - first.centerY)
    }

    /**
     * Two photos read different text at the same place. The word further inside its own photo
     * line wins; if both are well inside, the one the recognizer was surer about.
     */
    private fun photoWordWins(onPage: List<PlacedWord>, word: PlacedWord): Boolean {
        val pageInset = onPage.minOf { it.inset }
        val bothInside = pageInset >= SAFE_INSET && word.inset >= SAFE_INSET
        return if (bothInside || abs(pageInset - word.inset) < INSET_TIE) {
            word.confidence > onPage.map { it.confidence }.average()
        } else {
            word.inset > pageInset
        }
    }

    private fun heightOf(line: List<PlacedWord>): Float = median(line.map { it.height })

    private fun centerYOf(line: List<PlacedWord>): Float = median(line.map { it.centerY })

    private fun median(values: List<Float>): Float = values.sorted()[values.size / 2]

    /** An area of text with no more letters than this is a speck, not text. */
    private const val MAX_STRAY_LETTERS = 2

    /** Shorter words such as "the" repeat too often to tell where a photo belongs. */
    private const val MIN_ANCHOR_LENGTH = 4

    /** A word found more often than this in one photo is not used as an anchor. */
    private const val MAX_REPEATS = 3

    /** Fewer shared words than this cannot place a photo reliably. */
    private const val MIN_ANCHORS = 3

    private const val MAX_TRIES = 600

    // Distances below are in line heights of the page.

    /** Two anchors closer together than this give no reliable size and turn. */
    private const val MIN_PAIR_DISTANCE = 3f

    /** How far an anchor may miss the first, rough fit and still count as agreeing. */
    private const val ROUGH_DISTANCE = 3f

    /** How far an anchor may miss the exact fit. */
    private const val INLIER_DISTANCE = 2f

    /** Shear and stretch are only fitted with at least this many anchors, spread this far. */
    private const val MIN_ANCHORS_FOR_SHEAR = 8
    private const val MIN_SPREAD = 3f

    /** Photos of the same page differ in size by less than this factor, and are turned less than this (radians). */
    private const val MIN_SCALE = 0.4f
    private const val MAX_SCALE = 2.5f
    private const val MAX_TURN = 0.5f

    /** A word that lands this close to an equal word of the page (across, along) is that word. */
    private const val NEAR_X = 1.5f
    private const val NEAR_Y = 0.6f

    /** A word belongs to a page line if it lands this close to it vertically. */
    private const val SAME_LINE = 0.6f

    /** A word closer than this to the end of its photo line may be a scrap of a cut-off word. */
    private const val EDGE_INSET = 1.5f

    /** A scrap is dropped if words of the other photo stand this close on both sides of it. */
    private const val SCRAP_REACH = 3f

    /** A word further than this beside the ends of a line does not belong to it. */
    private const val MAX_GAP = 12f

    /** How far two words may miss each other and still count as the same place. */
    private const val SLACK = 0.3f

    /** Anchors this close to a point count about equally when the page is followed there. */
    private const val WARP_REACH = 4f

    /** Two words share a place if they overlap by more than this share of the narrower one. */
    private const val SAME_PLACE = 0.5f

    /** A word this many line heights from the end of its line is not an edge word. */
    private const val SAFE_INSET = 3f

    /** Insets closer than this count as equal. */
    private const val INSET_TIE = 0.5f
}
