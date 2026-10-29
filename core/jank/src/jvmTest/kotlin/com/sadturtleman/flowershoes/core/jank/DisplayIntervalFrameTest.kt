package com.sadturtleman.flowershoes.core.jank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * iOS 의 표시 간격 판정.
 *
 * CADisplayLink 는 "언제 화면에 실렸는가" 만 알려준다. 놓친 프레임은 그 간격이 예고된 간격보다
 * 벌어진 것으로 읽는데, 기준을 16ms 로 박아 두면 120Hz 기기에서 한 장씩 놓치는 것을 못 본다.
 */
class DisplayIntervalFrameTest {

    @Test
    fun `예고된 때에 실린 프레임은 끊긴 것이 아니다`() {
        val frame = displayIntervalFrame(intervalMillis = 16.67, expectedIntervalMillis = 16.67)

        assertFalse(frame.isJank)
        assertEquals(17L, frame.durationMillis)
    }

    @Test
    fun `한 번의 수직 동기를 놓치면 끊긴 프레임이다`() {
        val frame = displayIntervalFrame(intervalMillis = 33.33, expectedIntervalMillis = 16.67)

        assertTrue(frame.isJank)
        assertEquals(33L, frame.durationMillis)
    }

    /** 60Hz 로는 제때인 간격이 120Hz 에서는 한 장을 놓친 것이다. */
    @Test
    fun `기준은 고정값이 아니라 예고된 간격이다`() {
        val frame = displayIntervalFrame(intervalMillis = 16.67, expectedIntervalMillis = 8.33)

        assertTrue(frame.isJank)
    }

    /** 콜백 시각은 조금씩 흔들린다. 반 프레임 안쪽의 흔들림까지 끊김으로 세면 비율이 늘 부풀려진다. */
    @Test
    fun `반 프레임 안쪽의 흔들림은 끊김이 아니다`() {
        val frame = displayIntervalFrame(intervalMillis = 20.0, expectedIntervalMillis = 16.67)

        assertFalse(frame.isJank)
    }

    @Test
    fun `꼬리표는 그대로 실린다`() {
        val frame = displayIntervalFrame(
            intervalMillis = 16.67,
            expectedIntervalMillis = 16.67,
            states = mapOf("page" to "/home"),
        )

        assertEquals(mapOf("page" to "/home"), frame.states)
    }
}
