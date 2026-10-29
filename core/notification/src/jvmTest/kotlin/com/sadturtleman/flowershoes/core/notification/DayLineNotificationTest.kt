package com.sadturtleman.flowershoes.core.notification

import com.sadturtleman.flowershoes.core.model.DayLine
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** 그날의 한 줄: 면회 · 외박 · 휴가가 끝난 다음 날 오전 10시(서울), 각자 폰에서. */
class DayLineNotificationTest {

    // 서울 2026-10-19(월) 18:00
    private val now = Instant.parse("2026-10-19T09:00:00Z")

    private fun event(id: String, type: EventType, start: String, end: String = start, title: String = "", lines: Map<String, DayLine> = emptyMap()) = Event(
        id, type, title, LocalDateTime.parse("${start}T13:00"), LocalDateTime.parse("${end}T16:00"),
        alarm = false, updatedAt = now, lamport = 1, lastWriterDevice = "dev", dayLines = lines,
    )

    private fun plan(vararg events: Event, me: String? = "jisu", at: Instant = now) =
        NotificationPlanner().plan(at, "민준", emptyList(), events.toList(), null, dayLineFor = me).filter { it.id.startsWith("dayline-") }

    @Test
    fun `면회가 끝난 다음 날 오전 10시에 알린다`() {
        val n = plan(event("v", EventType.VISIT, "2026-10-19", title = "첫 면회")).single()
        assertEquals("dayline-v", n.id)
        assertEquals(Instant.parse("2026-10-20T01:00:00Z"), n.at) // 10:00 KST
        assertEquals("어제 면회, 그날의 한 줄 남길까요?", n.title)
        assertEquals("첫 면회", n.body)
        assertEquals("kkotsin://dayline/v", n.deepLink)
    }

    @Test
    fun `여러 날 휴가는 끝난 날 다음 날에 알린다`() {
        val n = plan(event("l", EventType.VACATION, "2026-10-20", "2026-10-24")).single()
        assertEquals(Instant.parse("2026-10-25T01:00:00Z"), n.at)
        assertEquals("어제 휴가, 그날의 한 줄 남길까요?", n.title)
        assertEquals("휴가", n.body)
    }

    @Test
    fun `외박도 대상이고 훈련 당직 기념일은 아니다`() {
        val ids = plan(
            event("o", EventType.OVERNIGHT, "2026-10-20", "2026-10-21"),
            event("t", EventType.TRAINING, "2026-10-20"),
            event("d", EventType.DUTY, "2026-10-20"),
            event("a", EventType.ANNIVERSARY, "2026-10-20"),
        ).map { it.id }
        assertEquals(listOf("dayline-o"), ids)
    }

    @Test
    fun `이미 쓴 사람의 폰에는 알리지 않는다`() {
        val e = event("v", EventType.VISIT, "2026-10-19", lines = mapOf("jisu" to DayLine("벌써 보고 싶다", 1)))
        assertTrue(plan(e, me = "jisu").isEmpty())
        assertEquals(1, plan(e, me = "minjun").size)
    }

    @Test
    fun `알림 시각이 지났으면 예약하지 않는다`() {
        val e = event("v", EventType.VISIT, "2026-10-18")
        assertTrue(plan(e, at = Instant.parse("2026-10-19T01:30:00Z")).isEmpty())
        assertEquals(1, plan(e, at = Instant.parse("2026-10-19T00:30:00Z")).size)
    }

    @Test
    fun `끄면 알리지 않는다`() {
        assertTrue(plan(event("v", EventType.VISIT, "2026-10-19"), me = null).isEmpty())
    }
}
