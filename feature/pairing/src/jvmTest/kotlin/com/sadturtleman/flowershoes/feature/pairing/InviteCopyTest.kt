package com.sadturtleman.flowershoes.feature.pairing

import com.sadturtleman.flowershoes.core.data.sync.Outbox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

    @Test
    fun `혼자 쓴 기록은 편지 통 · 그림 장으로 센다`() {
        assertEquals("편지 23통 · 그림 4장", Outbox(23, 4, 40).summary())
        assertEquals("편지 3통", Outbox(3, 0, 3).summary())
        assertEquals("그림 2장", Outbox(0, 2, 2).summary())
        assertEquals("혼자 쓴 편지 23통과 그림 4장도 같이 보낼게요.", Outbox(23, 4, 40).linkedBody())
        assertEquals("혼자 쓴 편지 3통도 같이 보낼게요.", Outbox(3, 0, 3).linkedBody())
        assertEquals("혼자 그린 그림 2장도 같이 보낼게요.", Outbox(0, 2, 2).linkedBody())
        assertNull(Outbox(0, 0, 5).linkedBody(), "일정 · 프로필만 있으면 말하지 않는다")
    }

    @Test
    fun `편지 · 그림이 많을 때만 보내는 중 화면을 띄운다`() {
        assertTrue(Outbox(23, 4, 40).showsProgress())
        assertFalse(Outbox(2, 1, 3).showsProgress())
    }
}
