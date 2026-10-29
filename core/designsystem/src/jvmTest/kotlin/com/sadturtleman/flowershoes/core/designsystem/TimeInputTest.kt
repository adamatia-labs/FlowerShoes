package com.sadturtleman.flowershoes.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** 예약 · 시간 고르기(Figma Time Input): 시 · 분 두 칸 + 오전 · 오후. */
class TimeInputTest {

    @Test
    fun `오전 오후와 12시를 24시간으로 바꾼다`() {
        assertEquals(LocalTime(9, 0), KkTimeInput("09", "00", pm = false).time)
        assertEquals(LocalTime(21, 30), KkTimeInput("9", "30", pm = true).time)
        assertEquals(LocalTime(0, 5), KkTimeInput("12", "05", pm = false).time)
        assertEquals(LocalTime(12, 0), KkTimeInput("12", "00", pm = true).time)
    }

    @Test
    fun `시는 1부터 12 분은 0부터 59만 받는다`() {
        assertNull(KkTimeInput("13", "00", pm = false).time)
        assertNull(KkTimeInput("0", "00", pm = false).time)
        assertNull(KkTimeInput("9", "60", pm = false).time)
        assertNull(KkTimeInput("", "00", pm = false).time)
    }

    @Test
    fun `입력 칸은 숫자 두 자리까지만`() {
        assertEquals("12", digits2("1a2b3"))
        assertEquals("", digits2("오전"))
    }

    @Test
    fun `처음 값은 고른 시각을 12시간으로 펼친다`() {
        assertEquals(KkTimeInput("09", "00", pm = false), KkTimeInput.of(LocalTime(9, 0)))
        assertEquals(KkTimeInput("12", "30", pm = false), KkTimeInput.of(LocalTime(0, 30)))
        assertEquals(KkTimeInput("09", "15", pm = true), KkTimeInput.of(LocalTime(21, 15)))
    }

    @Test
    fun `지난 시각은 고를 수 없다`() {
        val now = LocalDateTime(2026, 10, 28, 21, 0)
        assertFalse(KkTimeInput("09", "00", pm = true).isFuture(LocalDate(2026, 10, 28), now))
        assertTrue(KkTimeInput("09", "01", pm = true).isFuture(LocalDate(2026, 10, 28), now))
        assertTrue(KkTimeInput("09", "00", pm = false).isFuture(LocalDate(2026, 10, 29), now))
    }
}
