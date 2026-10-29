package com.sadturtleman.flowershoes.core.tti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "완성" 의 정의를 고정한다 — 이 판단이 틀리면 미완성 기록이 쏘이거나
 * 완성된 기록이 영영 DB 에 남는다.
 */
class TtiRecordTest {

    @Test
    fun `네 구간이 모두 닫혀야 완성이다`() {
        val record = record(
            TtiTimeline.VIEW_CREATE to TtiSpan(0, 5),
            TtiTimeline.BACKEND to TtiSpan(5, 205),
            TtiTimeline.VIEW_BINDING to TtiSpan(205, 215),
            TtiTimeline.BIG_PART_LOADING to TtiSpan(215, 1215),
        )

        assertTrue(record.isComplete)
        // 처음~끝(1215ms) 이 아니라 구간 길이의 합이다.
        assertEquals(1215L, record.totalTimeMillis)
    }

    @Test
    fun `구간 하나가 빠지면 미완성이다`() {
        val record = record(
            TtiTimeline.VIEW_CREATE to TtiSpan(0, 5),
            TtiTimeline.BACKEND to TtiSpan(5, 205),
            TtiTimeline.VIEW_BINDING to TtiSpan(205, 215),
        )

        assertFalse(record.isComplete)
        assertNull(record.totalTimeMillis)
    }

    @Test
    fun `열려만 있고 닫히지 않은 구간이 있으면 미완성이다`() {
        val record = record(
            TtiTimeline.VIEW_CREATE to TtiSpan(0, 5),
            TtiTimeline.BACKEND to TtiSpan(5, 205),
            TtiTimeline.VIEW_BINDING to TtiSpan(205, 215),
            // 이미지가 아직 내려오는 중
            TtiTimeline.BIG_PART_LOADING to TtiSpan(215, null),
        )

        assertFalse(record.isComplete)
        assertNull(record.totalTimeMillis)
    }

    @Test
    fun `구간이 겹쳐도 합은 길이의 합이다`() {
        val record = record(
            TtiTimeline.VIEW_CREATE to TtiSpan(0, 10),
            TtiTimeline.BACKEND to TtiSpan(0, 100),
            TtiTimeline.VIEW_BINDING to TtiSpan(50, 60),
            TtiTimeline.BIG_PART_LOADING to TtiSpan(50, 150),
        )

        assertEquals(10L + 100L + 10L + 100L, record.totalTimeMillis)
    }

    private fun record(vararg spans: Pair<TtiTimeline, TtiSpan>) = TtiRecord(
        tti = Tti("test"),
        pageName = "/detail",
        createdAt = 0L,
        spans = spans.toMap(),
    )
}
