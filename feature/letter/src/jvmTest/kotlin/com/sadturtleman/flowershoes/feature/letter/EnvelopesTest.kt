package com.sadturtleman.flowershoes.feature.letter

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** 편지함 봉투 · 읽기 화면 글자(Figma 4 · 편지함 `360:2613` · 받은 편지 `363:3666`, 오늘 2026-10-28). */
class EnvelopesTest {

    private val today = LocalDate(2026, 10, 28)
    private val discharge = LocalDate(2028, 2, 19)

    @Test
    fun `안 읽은 받은 편지는 봉인과 그날의 D-day 우표`() {
        assertEquals(Envelope.New("D-479"), envelopeOf(mine = false, unread = true, hasDrawing = true, on = today, discharge = discharge))
    }

    @Test
    fun `읽은 그림 편지는 폴라로이드`() {
        assertEquals(Envelope.Drawing, envelopeOf(mine = false, unread = false, hasDrawing = true, on = today, discharge = discharge))
    }

    @Test
    fun `내가 보낸 편지는 그림이 있어도 보냄 소인`() {
        assertEquals(Envelope.Sent("2026.10.19"), envelopeOf(mine = true, unread = false, hasDrawing = true, on = LocalDate(2026, 10, 19), discharge = discharge))
    }

    @Test
    fun `그 밖의 받은 편지는 그 편지를 받은 날의 D-day 우표`() {
        assertEquals(Envelope.Received("D-483"), envelopeOf(mine = false, unread = false, hasDrawing = false, on = LocalDate(2026, 10, 24), discharge = discharge))
    }

    @Test
    fun `우표는 전역일 당일 D-DAY 지나면 D+ 전역일을 모르면 날짜`() {
        assertEquals("D-DAY", stampText(discharge, discharge))
        assertEquals("D+3", stampText(discharge, LocalDate(2028, 2, 22)))
        assertEquals("10.24", stampText(null, LocalDate(2026, 10, 24)))
    }

    @Test
    fun `봉투 아래 줄은 보낸 사람과 오늘이면 시각 아니면 월일`() {
        val now = LocalDateTime(2026, 10, 28, 22, 0)
        assertEquals("민준 · 오늘 오후 9:12", envelopeMeta("민준", mine = false, at = LocalDateTime(2026, 10, 28, 21, 12), now = now))
        assertEquals("민준 · 10.27", envelopeMeta("민준", mine = false, at = LocalDateTime(2026, 10, 27, 20, 40), now = now))
        assertEquals("보낸 편지 · 06.20", envelopeMeta("지수", mine = true, at = LocalDateTime(2026, 6, 20, 9, 0), now = now))
        assertEquals("민준 · 2025.12.31", envelopeMeta("민준", mine = false, at = LocalDateTime(2025, 12, 31, 9, 0), now = now))
    }

    @Test
    fun `월 머리는 올해면 월만 지난해면 연도까지`() {
        assertEquals("10월", monthLabel(LocalDate(2026, 10, 1), today))
        assertEquals("2025년 12월", monthLabel(LocalDate(2025, 12, 31), today))
    }

    @Test
    fun `편지는 순서를 지키며 달마다 묶인다`() {
        val dates = listOf(LocalDate(2026, 10, 28), LocalDate(2026, 10, 19), LocalDate(2026, 9, 22), LocalDate(2025, 12, 1))
        val groups = groupByMonth(dates, today) { it }
        assertEquals(listOf("10월", "9월", "2025년 12월"), groups.map { it.first })
        assertEquals(listOf(2, 1, 1), groups.map { it.second.size })
    }

    @Test
    fun `읽기 화면 위 막대는 누가 보낸 편지인지와 보낸 시각`() {
        assertEquals("민준이 보낸 편지", readTitle(mine = false, partner = "민준"))
        assertEquals("지수가 보낸 편지", readTitle(mine = false, partner = "지수"))
        assertEquals("내가 보낸 편지", readTitle(mine = true, partner = "민준"))
        assertEquals("10월 28일 (수) 오후 9:12", readWhen(LocalDateTime(2026, 10, 28, 21, 12)))
        assertEquals("10월 27일 (화) 오전 12:05", readWhen(LocalDateTime(2026, 10, 27, 0, 5)))
    }

    @Test
    fun `아직 못 여는 편지는 열리는 시각과 남은 시간`() {
        val at = LocalDateTime(2026, 11, 1, 9, 0)
        assertEquals("11월 1일 오전 9시", opensAt(at))
        assertEquals("11월 1일 오후 9시 30분", opensAt(LocalDateTime(2026, 11, 1, 21, 30)))
        assertEquals("3일 11시간 48분", countdown(LocalDateTime(2026, 10, 28, 21, 12), at))
        assertEquals("11시간 48분", countdown(LocalDateTime(2026, 10, 31, 21, 12), at))
        assertEquals("1분", countdown(LocalDateTime(2026, 11, 1, 8, 59, 30), at))
    }
}
