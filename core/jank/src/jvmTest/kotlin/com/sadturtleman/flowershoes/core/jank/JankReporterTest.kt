package com.sadturtleman.flowershoes.core.jank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 집계 규칙 회귀 테스트.
 *
 * 통계는 틀려도 화면에 아무 표시가 나지 않는다 — 숫자가 조용히 이상해질 뿐이다.
 * 그래서 "언제 나가는가" 와 "무엇이 섞이는가" 를 여기서 못 박는다.
 */
class JankReporterTest {

    @Test
    fun `화면을 떠날 때 그 화면의 통계가 나간다`() {
        val report = RecordingJankReport()
        val reporter = JankReporter(report)

        reporter.onPageEnter(HOME)
        repeat(10) { reporter.onFrame(frame(isJank = it < 2)) }
        reporter.onPageExit(HOME)

        val snapshot = report.single()
        assertEquals(JankSnapshot.Reason.PAGE_EXIT, snapshot.reason)
        assertEquals(HOME, snapshot.pageName)
        assertEquals(10, snapshot.totalFrames)
        assertEquals(2, snapshot.jankFrames)
    }

    /** 앞 화면에서 끊긴 것이 다음 화면 탓으로 보이면 원인을 엉뚱한 곳에서 찾게 된다. */
    @Test
    fun `화면이 바뀌면 앞 화면 통계가 먼저 비워진다`() {
        val report = RecordingJankReport()
        val reporter = JankReporter(report)

        reporter.onPageEnter(HOME)
        repeat(5) { reporter.onFrame(frame(isJank = true)) }
        reporter.onPageEnter(DETAIL)
        reporter.onFrame(frame(isJank = false))
        reporter.onPageExit(DETAIL)

        assertEquals(listOf(HOME, DETAIL), report.snapshots.map { it.pageName })
        assertEquals(5, report.snapshots[0].jankFrames)
        assertEquals(0, report.snapshots[1].jankFrames)
    }

    /** 이미 다른 화면으로 넘어간 뒤 늦게 도착한 이탈 신호가 지금 화면 통계를 가져가면 안 된다. */
    @Test
    fun `지금 화면이 아닌 이탈 신호는 무시한다`() {
        val report = RecordingJankReport()
        val reporter = JankReporter(report)

        reporter.onPageEnter(DETAIL)
        reporter.onFrame(frame(isJank = true))
        reporter.onPageExit(HOME)

        assertTrue(report.snapshots.isEmpty())
    }

    @Test
    fun `스크롤 구간은 따로 세어 멈출 때 나간다`() {
        val report = RecordingJankReport()
        val reporter = JankReporter(report)

        reporter.onPageEnter(HOME)
        // 머물러 있는 동안의 프레임은 스크롤 통계에 들어가지 않는다.
        repeat(30) { reporter.onFrame(frame(isJank = false)) }
        reporter.onScrollStart()
        repeat(10) { reporter.onFrame(frame(isJank = it < 4)) }
        reporter.onScrollEnd()

        val snapshot = report.single()
        assertEquals(JankSnapshot.Reason.SCROLL_END, snapshot.reason)
        assertEquals(10, snapshot.totalFrames)
        assertEquals(4, snapshot.jankFrames)
    }

    @Test
    fun `스크롤하지 않았으면 멈춰도 아무것도 나가지 않는다`() {
        val report = RecordingJankReport()
        val reporter = JankReporter(report)

        reporter.onPageEnter(HOME)
        reporter.onScrollEnd()

        assertTrue(report.snapshots.isEmpty())
    }

    /** 700ms 짜리 한 장은 평균에 묻히지만 사용자에게는 멈춘 것으로 보인다. */
    @Test
    fun `얼어붙은 프레임은 누적과 별개로 한 건씩 나간다`() {
        val report = RecordingJankReport()
        val reporter = JankReporter(report)

        reporter.onPageEnter(DETAIL)
        reporter.onFrame(frame(isJank = true, durationMillis = 900))

        val snapshot = report.single()
        assertEquals(JankSnapshot.Reason.FROZEN_FRAME, snapshot.reason)
        assertEquals(1, snapshot.totalFrames)
        assertEquals(900L, snapshot.maxFrameDurationMillis)
    }

    @Test
    fun `비율이 임계치를 넘으면 그 자리에서 나가고 버킷이 비워진다`() {
        val report = RecordingJankReport()
        val reporter = JankReporter(report)

        reporter.onPageEnter(HOME)
        // 120 장을 채우는 동안 10 장이 끊겼다 — 5% 를 넘는다.
        repeat(120) { reporter.onFrame(frame(isJank = it % 12 == 0)) }

        val snapshot = report.single()
        assertEquals(JankSnapshot.Reason.THRESHOLD_EXCEEDED, snapshot.reason)
        assertEquals(120, snapshot.totalFrames)

        // 비워졌으므로 곧바로 또 나가지 않는다.
        reporter.onFrame(frame(isJank = true))
        assertEquals(1, report.snapshots.size)
    }

    /** 화면이 처음 뜨는 몇 프레임은 늘 나쁘게 나온다. 그것으로 경보가 울리면 아무도 보지 않게 된다. */
    @Test
    fun `표본이 모자라면 비율이 나빠도 나가지 않는다`() {
        val report = RecordingJankReport()
        val reporter = JankReporter(report)

        reporter.onPageEnter(HOME)
        repeat(30) { reporter.onFrame(frame(isJank = true)) }

        assertTrue(report.snapshots.isEmpty())
    }

    @Test
    fun `백그라운드로 내려가면 남은 것을 모두 비운다`() {
        val report = RecordingJankReport()
        val reporter = JankReporter(report)

        reporter.onPageEnter(HOME)
        repeat(5) { reporter.onFrame(frame(isJank = false)) }
        reporter.onScrollStart()
        repeat(3) { reporter.onFrame(frame(isJank = true)) }
        reporter.onAppBackground()

        assertEquals(
            listOf(JankSnapshot.Reason.BACKGROUND, JankSnapshot.Reason.BACKGROUND),
            report.snapshots.map { it.reason },
        )
        // 화면 통계에는 스크롤 구간이 함께 들어 있고, 스크롤 통계는 그 구간만 담는다.
        assertEquals(8, report.snapshots[0].totalFrames)
        assertEquals(3, report.snapshots[1].totalFrames)
    }

    @Test
    fun `평균과 비율은 모은 프레임에서 나온다`() {
        val report = RecordingJankReport()
        val reporter = JankReporter(report)

        reporter.onPageEnter(HOME)
        reporter.onFrame(frame(isJank = true, durationMillis = 30))
        reporter.onFrame(frame(isJank = false, durationMillis = 10))
        reporter.onPageExit(HOME)

        val snapshot = report.single()
        assertEquals(20L, snapshot.averageFrameDurationMillis)
        assertEquals(30L, snapshot.maxFrameDurationMillis)
        assertEquals(0.5f, snapshot.jankRatio, 0.001f)
    }

    private fun frame(isJank: Boolean, durationMillis: Long = 12L) =
        JankFrame(isJank = isJank, durationMillis = durationMillis)

    private companion object {
        const val HOME = "/home"
        const val DETAIL = "/detail"
    }
}

private class RecordingJankReport : JankReport {
    val snapshots = mutableListOf<JankSnapshot>()

    override fun report(snapshot: JankSnapshot) {
        snapshots += snapshot
    }

    fun single(): JankSnapshot = snapshots.single()
}
