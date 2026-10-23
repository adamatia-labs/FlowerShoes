package com.sadturtleman.flowershoes.core.drawing

import kotlin.math.sqrt

/**
 * 저장 직전에 문서를 다듬는다.
 *
 * 1. 획마다 Douglas-Peucker 로 점을 줄인다(허용 오차 [tolerance] 논리 단위).
 * 2. 각 획에 대해 "그 뒤에 그은" 지우개와 겹치는 점을 빼고 남은 연속 구간을 replaySegments 로 적는다.
 *    리플레이는 이 구간만 그리므로 지우는 과정은 보이지 않는다.
 *
 * 지우개가 획 굵기의 일부만 지운 경우는 점 단위로 표현할 수 없다 — 리플레이 끝의 PNG 크로스페이드가 메운다.
 */
object StrokeProcessing {

    const val DEFAULT_TOLERANCE = 0.5

    fun prepareForSave(doc: DrawingDocument, tolerance: Double = DEFAULT_TOLERANCE): DrawingDocument {
        val simplified = doc.elements.map { e ->
            when (e) {
                is Stroke -> e.copy(points = simplify(e.points, tolerance))
                is EraserStroke -> e.copy(points = simplify(e.points, tolerance))
                else -> e
            }
        }
        val erasers = simplified.filterIsInstance<EraserStroke>()
        val withSegments = simplified.map { e ->
            if (e !is Stroke) e else e.copy(replaySegments = replaySegments(e, erasers.filter { it.startMs > e.startMs }))
        }
        return doc.copy(elements = withSegments)
    }

    /** Douglas-Peucker. 첫 점과 끝 점은 항상 남기고, 남는 점의 시각(ts)도 함께 남긴다. */
    fun simplify(points: PointBuffer, tolerance: Double): PointBuffer {
        val n = points.size
        if (n <= 2) return points
        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to n - 1)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            var farthest = -1
            var maxDist = tolerance
            for (i in a + 1 until b) {
                val d = distanceToSegment(points.xs[i], points.ys[i], points.xs[a], points.ys[a], points.xs[b], points.ys[b])
                if (d > maxDist) {
                    maxDist = d
                    farthest = i
                }
            }
            if (farthest >= 0) {
                keep[farthest] = true
                stack.addLast(a to farthest)
                stack.addLast(farthest to b)
            }
        }
        val idx = (0 until n).filter { keep[it] }
        return PointBuffer(
            xs = IntArray(idx.size) { points.xs[idx[it]] },
            ys = IntArray(idx.size) { points.ys[idx[it]] },
            ts = IntArray(idx.size) { points.ts[idx[it]] },
            pressure = points.pressure?.let { p -> ByteArray(idx.size) { p[idx[it]] } },
        )
    }

    /**
     * 지워지지 않은 점의 연속 구간. 모두 남으면 null(전체), 모두 지워지면 빈 배열.
     * 점의 중심이 지우개 반지름 안에 들어가면 지워진 것으로 본다.
     */
    fun replaySegments(stroke: Stroke, laterErasers: List<EraserStroke>): IntArray? {
        if (laterErasers.isEmpty()) return null
        val p = stroke.points
        val erased = BooleanArray(p.size) { i ->
            laterErasers.any { e -> isUnderEraser(p.xs[i], p.ys[i], e) }
        }
        if (erased.none { it }) return null
        val out = mutableListOf<Int>()
        var runStart = -1
        for (i in 0..p.size) {
            val alive = i < p.size && !erased[i]
            if (alive && runStart < 0) runStart = i
            if (!alive && runStart >= 0) {
                out += runStart
                out += i - 1
                runStart = -1
            }
        }
        return out.toIntArray()
    }

    private fun isUnderEraser(x: Int, y: Int, e: EraserStroke): Boolean {
        val r = e.brush.width / 2.0
        val ex = e.points.xs
        val ey = e.points.ys
        if (ex.size == 1) return distance(x, y, ex[0], ey[0]) <= r
        for (i in 0 until ex.size - 1) {
            if (distanceToSegment(x, y, ex[i], ey[i], ex[i + 1], ey[i + 1]) <= r) return true
        }
        return false
    }

    private fun distance(x: Int, y: Int, ax: Int, ay: Int): Double {
        val dx = (x - ax).toDouble()
        val dy = (y - ay).toDouble()
        return sqrt(dx * dx + dy * dy)
    }

    internal fun distanceToSegment(px: Int, py: Int, ax: Int, ay: Int, bx: Int, by: Int): Double {
        val vx = (bx - ax).toDouble()
        val vy = (by - ay).toDouble()
        val len2 = vx * vx + vy * vy
        if (len2 == 0.0) return distance(px, py, ax, ay)
        val t = (((px - ax) * vx + (py - ay) * vy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * vx
        val cy = ay + t * vy
        val dx = px - cx
        val dy = py - cy
        return sqrt(dx * dx + dy * dy)
    }
}
