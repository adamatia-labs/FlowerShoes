package com.sadturtleman.flowershoes.feature.pairing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** 연결 화면 문구(Figma 1 · 연결): 이름 조사 · 만료 시각 · 혼자 쓴 기록 수. */
class InviteCopyTest {

    @Test
    fun `이름 뒤 조사는 받침을 따른다`() {
        assertEquals("민준이랑", "민준".withRang())
        assertEquals("지수랑", "지수".withRang())
        assertEquals("민준이", "민준".withSubject())
        assertEquals("지수가", "지수".withSubject())
    }

    @Test
    fun `링크 만료 시각은 오늘 · 내일 · 날짜로 읽는다`() {
        // 2026-10-28(수) 21:41 KST
        val now = Instant.parse("2026-10-28T12:41:00Z")
        assertEquals("오늘 오후 11:00까지 쓸 수 있어요", expiryCaption(Instant.parse("2026-10-28T14:00:00Z"), now))
        assertEquals("내일 오전 9:41까지 쓸 수 있어요", expiryCaption(Instant.parse("2026-10-29T00:41:00Z"), now))
        assertEquals("11월 4일 오후 9:41까지 쓸 수 있어요", expiryCaption(Instant.parse("2026-11-04T12:41:00Z"), now))
        assertEquals("내일 오후 12:05까지 쓸 수 있어요", expiryCaption(Instant.parse("2026-10-29T03:05:00Z"), now))
    }

}
