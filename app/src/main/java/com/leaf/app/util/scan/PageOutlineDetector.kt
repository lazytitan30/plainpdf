package com.leaf.app.util.scan

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** An 8-bit grayscale image, row-major, values 0..255. */
class GrayImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(pixels.size == width * height) { "pixel count does not match size" }
    }
}

/** A corner in normalised image coordinates, 0..1 on both axes. */
data class Corner(val x: Float, val y: Float)

/** Four corners clockwise from top-left. */
data class Outline(val tl: Corner, val tr: Corner, val br: Corner, val bl: Corner) {
    fun corners(): List<Corner> = listOf(tl, tr, br, bl)

    companion object {
        fun full(inset: Float = 0f) = Outline(Corner(inset, inset), Corner(1f - inset, inset), Corner(1f - inset, 1f - inset), Corner(inset, 1f - inset))
    }
}

/**
 * Finds the outline of a page in a photo without any native or ML dependency, so it can
 * run in every flavour and be unit tested on the JVM.
 *
 * Pipeline: 3x3 blur, Sobel gradient, thinning to one-pixel edges, a Hough transform over
 * the edge pixels, non-maximum suppression of the accumulator, then a search over pairs of
 * near-horizontal and near-vertical lines. Each candidate quadrilateral is checked against
 * the edge map: every side must be supported by edge pixels along most of its length, which
 * is what separates a page edge from a line of text or from grain. When nothing convincing
 * is found the caller gets a slightly inset full frame so the user always has handles.
 */
object PageOutlineDetector {

    const val FALLBACK_INSET = 0.04f
    private const val MIN_SPAN = 0.3
    private const val MIN_AREA = 0.15
    private const val MIN_EDGE_PIXELS = 50
    private const val ANGLE_TOLERANCE = 25
    private const val MIN_LINE_FRACTION = 0.15
    private const val BORDER_PX = 3.0
    private const val MIN_SUPPORT = 0.45
    private const val CANDIDATES_PER_DIRECTION = 16
    private const val THETAS = 180

    fun detect(image: GrayImage): Outline = detectOrNull(image) ?: Outline.full(FALLBACK_INSET)

    /** Like [detect] but returns null instead of the fallback frame, which tests rely on. */
    fun detectOrNull(image: GrayImage): Outline? {
        val w = image.width
        val h = image.height
        if (w < 8 || h < 8) return null
        val gray = image.pixels

        val blur = IntArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            var s = 0
            for (dy in -1..1) for (dx in -1..1) s += gray[(y + dy) * w + x + dx]
            blur[y * w + x] = s / 9
        }
        // The blur is undefined on the outer ring, so the gradient starts one pixel further in;
        // otherwise the frame border itself would become the strongest edge in the photo.
        val gxs = IntArray(w * h)
        val gys = IntArray(w * h)
        val mag = IntArray(w * h)
        var sum = 0L
        var sumSq = 0L
        for (y in 2 until h - 2) for (x in 2 until w - 2) {
            val i = y * w + x
            val gx = -blur[i - w - 1] - 2 * blur[i - 1] - blur[i + w - 1] + blur[i - w + 1] + 2 * blur[i + 1] + blur[i + w + 1]
            val gy = -blur[i - w - 1] - 2 * blur[i - w] - blur[i - w + 1] + blur[i + w - 1] + 2 * blur[i + w] + blur[i + w + 1]
            val m = abs(gx) + abs(gy)
            gxs[i] = gx
            gys[i] = gy
            mag[i] = m
            sum += m
            sumSq += m.toLong() * m
        }
        val n = ((w - 4) * (h - 4)).toLong().coerceAtLeast(1)
        val mean = sum.toDouble() / n
        val std = sqrt((sumSq.toDouble() / n - mean * mean).coerceAtLeast(0.0))
        val threshold = (mean + 1.5 * std).toInt().coerceAtLeast(40)

        // Thin edges: keep a pixel only where the gradient peaks across the edge direction, so
        // a thick stroke contributes one line per side instead of one per pixel row.
        val edge = BooleanArray(w * h)
        var edgeCount = 0
        for (y in 2 until h - 2) for (x in 2 until w - 2) {
            val i = y * w + x
            val m = mag[i]
            if (m < threshold) continue
            val across = if (abs(gxs[i]) >= abs(gys[i])) 1 else w
            if (m >= mag[i - across] && m >= mag[i + across]) {
                edge[i] = true
                edgeCount++
            }
        }
        if (edgeCount < MIN_EDGE_PIXELS) return null

        // Hough accumulator: theta in 1 degree steps, rho in 2 px steps.
        val diag = hypot(w.toDouble(), h.toDouble()).toInt()
        val rhoBins = diag + 1
        val acc = IntArray(THETAS * rhoBins)
        val cosT = DoubleArray(THETAS) { cos(Math.toRadians(it.toDouble())) }
        val sinT = DoubleArray(THETAS) { sin(Math.toRadians(it.toDouble())) }
        for (y in 2 until h - 2) for (x in 2 until w - 2) {
            if (!edge[y * w + x]) continue
            for (t in 0 until THETAS) {
                val rho = x * cosT[t] + y * sinT[t]
                val rb = ((rho + diag) / 2).toInt()
                if (rb in 0 until rhoBins) acc[t * rhoBins + rb]++
            }
        }

        val candidates = ArrayList<Line>()
        val minVotes = (minOf(w, h) * MIN_LINE_FRACTION).toInt().coerceAtLeast(20)
        for (t in 0 until THETAS) for (rb in 0 until rhoBins) {
            val v = acc[t * rhoBins + rb]
            if (v < minVotes) continue
            var isMax = true
            loop@ for (dt in -2..2) for (dr in -2..2) {
                val tt = (t + dt + THETAS) % THETAS
                val rr = rb + dr
                if (rr !in 0 until rhoBins) continue
                if (acc[tt * rhoBins + rr] > v) { isMax = false; break@loop }
            }
            if (isMax) candidates.add(Line(t, rb * 2.0 - diag, v))
        }
        // A normal angle near 90 degrees is a horizontal line; near 0 or 180 a vertical one.
        val horizontal = candidates.filter { abs(it.theta - 90) <= ANGLE_TOLERANCE }.sortedByDescending { it.votes }.take(CANDIDATES_PER_DIRECTION)
        val vertical = candidates.filter { it.theta <= ANGLE_TOLERANCE || it.theta >= THETAS - ANGLE_TOLERANCE }.sortedByDescending { it.votes }.take(CANDIDATES_PER_DIRECTION)
        if (horizontal.size < 2 || vertical.size < 2) return null

        fun yAt(line: Line, x: Double): Double = (line.rho - x * cosT[line.theta]) / sinT[line.theta]
        fun xAt(line: Line, y: Double): Double = (line.rho - y * sinT[line.theta]) / cosT[line.theta]

        val cx = w / 2.0
        val cy = h / 2.0
        // Lines sitting on the frame border are the photo edge, never the page edge.
        val hSorted = horizontal.filter { yAt(it, cx) > BORDER_PX && yAt(it, cx) < h - BORDER_PX }.sortedBy { yAt(it, cx) }
        val vSorted = vertical.filter { xAt(it, cy) > BORDER_PX && xAt(it, cy) < w - BORDER_PX }.sortedBy { xAt(it, cy) }
        var best: Outline? = null
        var bestScore = 0.0
        for (i in hSorted.indices) for (j in i + 1 until hSorted.size) {
            val top = hSorted[i]
            val bottom = hSorted[j]
            if (yAt(bottom, cx) - yAt(top, cx) < h * MIN_SPAN) continue
            for (k in vSorted.indices) for (l in k + 1 until vSorted.size) {
                val left = vSorted[k]
                val right = vSorted[l]
                if (xAt(right, cy) - xAt(left, cy) < w * MIN_SPAN) continue
                val tl = intersect(top, left, cosT, sinT) ?: continue
                val tr = intersect(top, right, cosT, sinT) ?: continue
                val br = intersect(bottom, right, cosT, sinT) ?: continue
                val bl = intersect(bottom, left, cosT, sinT) ?: continue
                if (!inside(tl, w, h) || !inside(tr, w, h) || !inside(br, w, h) || !inside(bl, w, h)) continue
                val frac = abs(polygonArea(listOf(tl, tr, br, bl))) / (w * h)
                if (frac < MIN_AREA || frac > 0.995) continue
                // Every side must actually run along edge pixels.
                val sides = listOf(tl to tr, tr to br, br to bl, bl to tl)
                var supported = 0.0
                var weakest = 1.0
                for ((a, b) in sides) {
                    val s = support(edge, w, h, a, b)
                    weakest = minOf(weakest, s.fraction)
                    supported += s.pixels
                }
                if (weakest < MIN_SUPPORT) continue
                val score = supported * sqrt(frac)
                if (score > bestScore) {
                    bestScore = score
                    best = Outline(tl.norm(w, h), tr.norm(w, h), br.norm(w, h), bl.norm(w, h))
                }
            }
        }
        return best
    }

    /** A Hough line in normal form: x cos(theta) + y sin(theta) = rho. */
    private data class Line(val theta: Int, val rho: Double, val votes: Int)

    private data class P(val x: Double, val y: Double) {
        fun norm(w: Int, h: Int) = Corner((x / w).toFloat().coerceIn(0f, 1f), (y / h).toFloat().coerceIn(0f, 1f))
    }

    private class Support(val fraction: Double, val pixels: Double)

    /**
     * Walks from [a] to [b] and counts the steps that land on an edge pixel, looking up to
     * two pixels to either side across the line (a whole-degree Hough angle can be that far
     * off at the ends). Only across, not along, so grain cannot fake a line.
     */
    private fun support(edge: BooleanArray, w: Int, h: Int, a: P, b: P): Support {
        val steps = maxOf(abs(b.x - a.x), abs(b.y - a.y)).roundToInt().coerceAtLeast(1)
        val across = if (abs(b.x - a.x) >= abs(b.y - a.y)) w else 1
        var hits = 0
        for (s in 0..steps) {
            val t = s.toDouble() / steps
            val x = (a.x + (b.x - a.x) * t).roundToInt()
            val y = (a.y + (b.y - a.y) * t).roundToInt()
            if (x < 2 || y < 2 || x >= w - 2 || y >= h - 2) continue
            val i = y * w + x
            if (edge[i] || edge[i - across] || edge[i + across] || edge[i - 2 * across] || edge[i + 2 * across]) hits++
        }
        return Support(hits.toDouble() / (steps + 1), hits.toDouble())
    }

    private fun intersect(a: Line, b: Line, cosT: DoubleArray, sinT: DoubleArray): P? {
        val a1 = cosT[a.theta]; val b1 = sinT[a.theta]
        val a2 = cosT[b.theta]; val b2 = sinT[b.theta]
        val det = a1 * b2 - a2 * b1
        if (abs(det) < 1e-6) return null
        return P((a.rho * b2 - b.rho * b1) / det, (a1 * b.rho - a2 * a.rho) / det)
    }

    private fun inside(p: P, w: Int, h: Int): Boolean = p.x >= -w * 0.05 && p.x <= w * 1.05 && p.y >= -h * 0.05 && p.y <= h * 1.05

    private fun polygonArea(pts: List<P>): Double {
        var s = 0.0
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            s += a.x * b.y - b.x * a.y
        }
        return s / 2
    }
}
