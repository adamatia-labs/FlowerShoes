package com.sadturtleman.flowershoes.core.drawing

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 리플레이 시간표. 보내는 쪽에서 [durationMs] 를 계산해 replayDurationMs 로 저장하고,
 * 받는 쪽 플레이어는 매 프레임 [frame] 으로 무엇을 얼마나 그릴지 묻는다.
 *
 * 규칙:
 * - 재생 대상은 배경 사진 → (획 · 스티커를 startMs 순서로). 틀 · 지우개 · 모르는 요소는 뺀다.
 * - 요소 사이 빈 시간은 최대 [MAX_GAP_MS] 로 자른다.
 * - 압축한 길이가 [MAX_DRAW_MS] 를 넘으면 그만큼 배속한다. 짧으면 그대로 둔다.
 * - 끝에 [FINISH_MS] 동안 완성 PNG 로 크로스페이드한다 — 지우개가 획 일부만 지운 부분 · 형광펜 겹침까지
 *   항상 완성본과 똑같이 끝난다. 전체 길이는 8초를 넘지 않는다.
 */
class ReplayTimeline private constructor(
    private val photos: List<PhotoBackground>,
    private val items: List<Scheduled>,
    private val speed: Double,
    private val drawMs: Long,
) {

    /** 화면에 표시하는 재생 길이("6초"). PNG 마무리 포함. */
    val durationMs: Long get() = drawMs + FINISH_MS

    private class Scheduled(val element: Element, val startMs: Double)

    data class StrokeProgress(
        val stroke: Stroke,
        /** 이 인덱스까지의 점을 그린다(포함). replaySegments 밖의 점은 렌더러가 건너뛴다. */
        val drawnThrough: Int,
        val finished: Boolean,
        /**
         * [drawnThrough] 다음 점까지 간 비율(0..1). 간소화로 직선 위의 점이 빠져도 펜이 실제 속도로 나아가 보이게
         * 렌더러가 두 점 사이를 이만큼 이어 그린다.
         */
        val toward: Float = 0f,
    ) {
        /** 진행 중인 획의 펜 끝(다음 점 쪽으로 [toward] 만큼 간 위치). 끝난 획이면 null. */
        val penTip: Pair<Int, Int>?
            get() {
                if (finished || drawnThrough < 0) return null
                val p = stroke.points
                val i = drawnThrough
                if (i + 1 >= p.size) return p.xs[i] to p.ys[i]
                return (p.xs[i] + (p.xs[i + 1] - p.xs[i]) * toward).toInt() to (p.ys[i] + (p.ys[i + 1] - p.ys[i]) * toward).toInt()
            }
    }

    data class StickerProgress(val sticker: StickerElement, val scale: Float)

    data class Frame(
        val photos: List<Pair<PhotoBackground, Float>>,
        val strokes: List<StrokeProgress>,
        val stickers: List<StickerProgress>,
        /** 완성 PNG 를 덮는 정도. 1 이면 PNG 만 보인다. */
        val finishAlpha: Float,
    ) {
        val isComplete: Boolean get() = finishAlpha >= 1f
    }

    /**
     * [tMs] 시점의 화면. 진행 막대 탐색도 이 함수 하나로 된다(상태가 없다).
     * 끝난 획은 렌더러가 오프스크린 비트맵에 캐시하고, 매 프레임은 finished=false 인 획만 다시 그리면 된다.
     */
    fun frame(tMs: Long): Frame {
        if (tMs >= durationMs) return completeFrame()
        val t = tMs.toDouble()
        val strokes = mutableListOf<StrokeProgress>()
        val stickers = mutableListOf<StickerProgress>()
        for (item in items) {
            if (item.startMs > t) break
            val local = (t - item.startMs) * speed // 원래 시간 축에서 얼마나 지났는지
            when (val e = item.element) {
                is Stroke -> {
                    val ts = e.points.ts
                    var i = -1
                    while (i + 1 < ts.size && ts[i + 1] <= local) i++
                    val toward = if (i in 0 until ts.size - 1) {
                        val span = (ts[i + 1] - ts[i]).coerceAtLeast(1)
                        ((local - ts[i]) / span).toFloat().coerceIn(0f, 1f)
                    } else 0f
                    strokes += StrokeProgress(e, i, finished = i == ts.size - 1, toward = toward)
                }
                is StickerElement -> stickers += StickerProgress(e, popScale(((t - item.startMs) / POP_MS).toFloat()))
                else -> Unit
            }
        }
        val photoAlpha = min(1.0, t / PHOTO_FADE_MS).toFloat()
        val finish = if (t <= drawMs) 0f else ((t - drawMs) / FINISH_MS).toFloat().coerceIn(0f, 1f)
        return Frame(photos.map { it to photoAlpha }, strokes, stickers, finish)
    }

    /** 캔버스를 탭했을 때. 바로 완성본을 보여 준다. */
    fun completeFrame(): Frame = Frame(
        photos = photos.map { it to 1f },
        strokes = items.mapNotNull { (it.element as? Stroke)?.let { s -> StrokeProgress(s, s.points.size - 1, true) } },
        stickers = items.mapNotNull { (it.element as? StickerElement)?.let { s -> StickerProgress(s, 1f) } },
        finishAlpha = 1f,
    )

    companion object {
        const val MAX_GAP_MS = 300L
        const val MAX_TOTAL_MS = 8_000L
        const val FINISH_MS = 150L
        const val MAX_DRAW_MS = MAX_TOTAL_MS - FINISH_MS
        const val PHOTO_FADE_MS = 300.0
        const val POP_MS = 150.0

        fun of(doc: DrawingDocument): ReplayTimeline {
            val photos = doc.elements.filterIsInstance<PhotoBackground>()
            val playable = doc.elements
                .filter { it is Stroke || it is StickerElement }
                .sortedBy { it.startMs }

            // 빈 시간 자르기
            val compressed = mutableListOf<Pair<Element, Long>>()
            var shift = 0L
            var prevEnd = playable.firstOrNull()?.startMs ?: 0L
            for (e in playable) {
                val gap = e.startMs - prevEnd
                if (gap > MAX_GAP_MS) shift += gap - MAX_GAP_MS
                val start = e.startMs - shift - (playable.first().startMs)
                compressed += e to start
                prevEnd = max(prevEnd, e.startMs + e.ownDuration())
            }
            val rawLength = compressed.maxOfOrNull { (e, s) -> s + e.ownDuration() } ?: 0L
            val speed = if (rawLength > MAX_DRAW_MS) rawLength.toDouble() / MAX_DRAW_MS else 1.0
            val drawMs = ceil(rawLength / speed).toLong()
            return ReplayTimeline(
                photos = photos,
                items = compressed.map { (e, s) -> Scheduled(e, s / speed) },
                speed = speed,
                drawMs = drawMs,
            )
        }

        private fun Element.ownDuration(): Long = when (this) {
            is Stroke -> points.durationMs.toLong()
            is StickerElement -> POP_MS.toLong()
            else -> 0L
        }

        /** 0 → 1.15 → 1 로 "톡" 튀어나오는 크기. */
        private fun popScale(p: Float): Float = when {
            p >= 1f -> 1f
            p < 0.6f -> 1.15f * (p / 0.6f)
            else -> 1.15f - 0.15f * ((p - 0.6f) / 0.4f)
        }
    }
}
