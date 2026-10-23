package com.sadturtleman.flowershoes.jank.domain


/**
 * JankStats 가 프레임마다 부르는 콜백을 받아 통계로 묶어 [JankReport] 로 넘긴다.
 *
 * 세 가지를 따로 센다 — 화면 하나에서 쌓인 것, 스크롤 한 구간에서 쌓인 것, 그리고 그냥 두면
 * 놓칠 만큼 나쁜 한 프레임. 나눠 두는 이유는 원인이 다르기 때문이다. 화면 전체 비율은 그 화면이
 * 무거운지를 말하고, 스크롤 구간은 목록이 무거운지를 말한다. 둘을 한 통에 담으면 스크롤하지 않고
 * 머문 시간이 비율을 희석해 목록 문제가 보이지 않는다.
 *
 * 모든 함수는 메인 스레드에서 불린다(JankStats 콜백이 그 위에서 돈다). 그래서 잠금이 없다 —
 * 잠금을 걸면 그 대기가 다시 프레임을 놓치게 만든다.
 */
class JankReporter constructor(
    private val report: JankReport,
) {

    private var currentPage: String = PAGE_UNKNOWN
    private val pageBucket = Bucket()

    /** null 이 아닌 동안만 스크롤 구간이 쌓인다. */
    private var scrollBucket: Bucket? = null

    fun onFrame(frame: JankFrame) {
        val isFrozen = frame.durationMillis >= FROZEN_THRESHOLD_MILLIS

        pageBucket.add(frame.isJank, isFrozen, frame.durationMillis)
        scrollBucket?.add(frame.isJank, isFrozen, frame.durationMillis)

        // 얼어붙은 프레임은 누적에 섞으면 평균에 묻힌다. 한 건으로 따로 내보낸다.
        if (isFrozen) {
            report.report(
                JankSnapshot(
                    pageName = currentPage,
                    reason = JankSnapshot.Reason.FROZEN_FRAME,
                    totalFrames = 1,
                    jankFrames = if (frame.isJank) 1 else 0,
                    frozenFrames = 1,
                    maxFrameDurationMillis = frame.durationMillis,
                    sumFrameDurationMillis = frame.durationMillis,
                    states = frame.states,
                ),
            )
        }

        // 표본이 쌓이기 전에는 재지 않는다 — 화면이 처음 뜨는 몇 프레임은 늘 나쁘게 나온다.
        // 내보낸 뒤 버킷을 비우는 것은 같은 통계가 프레임마다 다시 나가는 것을 막기 위해서다.
        if (pageBucket.totalFrames >= MIN_SAMPLES_FOR_THRESHOLD &&
            pageBucket.jankRatio >= JANK_RATIO_THRESHOLD
        ) {
            report.report(
                pageBucket.toSnapshot(
                    pageName = currentPage,
                    reason = JankSnapshot.Reason.THRESHOLD_EXCEEDED,
                    states = frame.states,
                ),
            )
            pageBucket.reset()
        }
    }

    /** 화면에 들어왔다. 앞 화면이 남긴 것이 섞이지 않도록 먼저 비운다. */
    fun onPageEnter(pageName: String) {
        if (currentPage != pageName && pageBucket.totalFrames > 0) {
            report.report(pageBucket.toSnapshot(currentPage, JankSnapshot.Reason.PAGE_EXIT))
            pageBucket.reset()
        }
        currentPage = pageName
    }

    /** 화면을 떠났다. 이미 다른 화면으로 넘어간 뒤의 늦은 신호는 무시한다. */
    fun onPageExit(pageName: String) {
        if (currentPage != pageName) return
        if (pageBucket.totalFrames > 0) {
            report.report(pageBucket.toSnapshot(pageName, JankSnapshot.Reason.PAGE_EXIT))
            pageBucket.reset()
        }
        currentPage = PAGE_UNKNOWN
    }

    fun onScrollStart() {
        scrollBucket = Bucket()
    }

    fun onScrollEnd() {
        val bucket = scrollBucket ?: return
        if (bucket.totalFrames > 0) {
            report.report(bucket.toSnapshot(currentPage, JankSnapshot.Reason.SCROLL_END))
        }
        scrollBucket = null
    }

    /**
     * 앱이 백그라운드로 내려갔다. 안드로이드는 프로세스 종료를 알려주지 않으므로
     * 여기서 비우지 않으면 남은 통계는 그대로 사라진다.
     */
    fun onAppBackground() {
        if (pageBucket.totalFrames > 0) {
            report.report(pageBucket.toSnapshot(currentPage, JankSnapshot.Reason.BACKGROUND))
            pageBucket.reset()
        }
        scrollBucket?.let { bucket ->
            if (bucket.totalFrames > 0) {
                report.report(bucket.toSnapshot(currentPage, JankSnapshot.Reason.BACKGROUND))
            }
        }
        scrollBucket = null
    }

    /** 프레임을 세어 두는 통. 값만 쌓고 언제 내보낼지는 바깥이 정한다. */
    private class Bucket {
        var totalFrames: Int = 0
            private set
        var jankFrames: Int = 0
            private set
        var frozenFrames: Int = 0
            private set
        var maxFrameDurationMillis: Long = 0L
            private set
        var sumFrameDurationMillis: Long = 0L
            private set

        val jankRatio: Float
            get() = if (totalFrames == 0) 0f else jankFrames.toFloat() / totalFrames

        fun add(isJank: Boolean, isFrozen: Boolean, durationMillis: Long) {
            totalFrames += 1
            if (isJank) jankFrames += 1
            if (isFrozen) frozenFrames += 1
            if (durationMillis > maxFrameDurationMillis) maxFrameDurationMillis = durationMillis
            sumFrameDurationMillis += durationMillis
        }

        fun reset() {
            totalFrames = 0
            jankFrames = 0
            frozenFrames = 0
            maxFrameDurationMillis = 0L
            sumFrameDurationMillis = 0L
        }

        fun toSnapshot(
            pageName: String,
            reason: JankSnapshot.Reason,
            states: Map<String, String> = emptyMap(),
        ) = JankSnapshot(
            pageName = pageName,
            reason = reason,
            totalFrames = totalFrames,
            jankFrames = jankFrames,
            frozenFrames = frozenFrames,
            maxFrameDurationMillis = maxFrameDurationMillis,
            sumFrameDurationMillis = sumFrameDurationMillis,
            states = states,
        )
    }

    private companion object {
        const val PAGE_UNKNOWN = "unknown"

        /** 이만큼 걸린 프레임 하나는 "얼어붙었다" 로 본다(Android Vitals 기준). */
        const val FROZEN_THRESHOLD_MILLIS = 700L

        /** 임계치를 재기 전에 모아야 하는 프레임 수. 60fps 로 2초쯤이다. */
        const val MIN_SAMPLES_FOR_THRESHOLD = 120

        /** 누적 비율이 이 값을 넘으면 그 자리에서 내보낸다. */
        const val JANK_RATIO_THRESHOLD = 0.05f
    }
}
